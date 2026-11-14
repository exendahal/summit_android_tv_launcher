package np.exendahal.summitlauncher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Weather from Open-Meteo (PRD 8.3), always fetched in metric and converted locally.
 * The raw JSON is cached in preferences; the fakeData flavour returns the Appendix B demo data.
 */
public final class Weather {
    /** Background thread for network and disk work. */
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();

    /** Morning 06-12, Afternoon 12-17, Evening 17-21, Night 21-06. */
    public static final class Part {
        public int code, rainProb, rainFromHour = -1;
        public double temp;
    }

    public static final class Day {
        public LocalDate date;
        public int code, rainProb;
        public double hi, lo;
    }

    public double temp, wind, rainToday, hi, lo, namche = Double.NaN;
    public int code, namcheCode;
    public boolean isDay = true;
    public long updatedAt;
    public final Part[] parts = {new Part(), new Part(), new Part(), new Part()};
    public final List<Day> next = new ArrayList<>();

    /** Fallback when nothing is chosen or detected. */
    private static final double[] KATHMANDU = {27.7172, 85.3240};
    private static final double[] NAMCHE = {27.8069, 86.7140};
    private static final long GEO_MAX_AGE_MS = 24 * 3_600_000L;

    /** A searchable place (Open-Meteo geocoding result). */
    public static final class Place {
        public final String name, region;
        public final double lat, lon;
        Place(String name, String region, double lat, double lon) {
            this.name = name; this.region = region; this.lat = lat; this.lon = lon;
        }
    }

    /**
     * Searches cities worldwide by name with Open-Meteo geocoding (no key). Call off the main thread.
     *
     * @throws IOException when offline
     */
    public static List<Place> search(String query, String language) throws IOException {
        List<Place> out = new ArrayList<>();
        String q = query.trim();
        if (q.length() < 2) return out;
        String json = get("https://geocoding-api.open-meteo.com/v1/search?count=8&format=json&language="
                + java.net.URLEncoder.encode(language, "UTF-8") + "&name=" + java.net.URLEncoder.encode(q, "UTF-8"));
        try {
            JSONArray r = new JSONObject(json).optJSONArray("results");
            for (int i = 0; r != null && i < r.length(); i++) {
                JSONObject o = r.getJSONObject(i);
                String admin = o.optString("admin1"), country = o.optString("country");
                String region = admin.isEmpty() ? country : country.isEmpty() ? admin : admin + ", " + country;
                out.add(new Place(o.getString("name"), region, o.getDouble("latitude"), o.getDouble("longitude")));
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }
        return out;
    }

    /** Uses a searched place for weather. */
    public static void choose(Context c, Place place) {
        Prefs p = Prefs.get(c);
        p.raw().edit()
                .putString("loc_name", place.name)
                .putLong("loc_lat", Double.doubleToRawLongBits(place.lat))
                .putLong("loc_lon", Double.doubleToRawLongBits(place.lon))
                .apply();
        p.set(Prefs.LOCATION, "custom");
    }

    /** Switches back to the detected current location, re-detecting it now. */
    public static void chooseCurrent(Context c) {
        Prefs p = Prefs.get(c);
        p.raw().edit().remove("geo_at").apply();
        p.set(Prefs.LOCATION, "auto");
    }

    public static boolean usesCurrentLocation(Context c) { return !"custom".equals(Prefs.get(c).str(Prefs.LOCATION)); }

    /** @return the detected city, or null before the first detection. */
    public static String detectedCity(Context c) { return Prefs.get(c).raw().getString("geo_city", null); }

    /** @return the name of the place weather is shown for. */
    public static String placeName(Context c) {
        Prefs p = Prefs.get(c);
        String name = usesCurrentLocation(c) ? detectedCity(c) : p.raw().getString("loc_name", null);
        return name != null ? name : c.getString(R.string.loc_kathmandu);
    }

    /** @return {lat, lon}: the searched place, else the detected location, else Kathmandu. */
    private static double[] coords(Context c) {
        android.content.SharedPreferences sp = Prefs.get(c).raw();
        String prefix = usesCurrentLocation(c) ? "geo_" : "loc_";
        if (sp.contains(prefix + "lat")) {
            return new double[]{Double.longBitsToDouble(sp.getLong(prefix + "lat", 0)), Double.longBitsToDouble(sp.getLong(prefix + "lon", 0))};
        }
        return KATHMANDU;
    }

    /**
     * Detects the TV's approximate location from its public IP (city level), at most once a day.
     * Only used when the user picks "Current location". Call off the main thread.
     */
    static void detectLocation(Context c) {
        Prefs p = Prefs.get(c);
        if (!usesCurrentLocation(c)) return;
        if (System.currentTimeMillis() - p.raw().getLong("geo_at", 0) < GEO_MAX_AGE_MS) return;
        String[][] services = {{"https://ipapi.co/json/", "latitude", "longitude", "city"},
                {"https://ipwho.is/", "latitude", "longitude", "city"}};
        for (String[] svc : services) {
            try {
                JSONObject o = new JSONObject(get(svc[0]));
                if (!o.has(svc[1]) || o.optString(svc[3]).isEmpty()) continue;
                p.raw().edit()
                        .putLong("geo_lat", Double.doubleToRawLongBits(o.getDouble(svc[1])))
                        .putLong("geo_lon", Double.doubleToRawLongBits(o.getDouble(svc[2])))
                        .putString("geo_city", o.getString(svc[3]))
                        .putLong("geo_at", System.currentTimeMillis())
                        .apply();
                return;
            } catch (IOException | JSONException ignored) {
                // try the next service; fall back to Kathmandu if all fail
            }
        }
    }

    /**
     * Returns weather, from cache if younger than 30 min, else from the network. Falls back to stale
     * cache when offline. Call off the main thread.
     *
     * @return weather, or null when nothing is cached and the network failed.
     */
    public static Weather load(Context c) {
        if (BuildConfig.FAKE_DATA) return fake();
        Prefs p = Prefs.get(c);
        detectLocation(c);
        double[] ll = coords(c);
        String loc = ll[0] + "," + ll[1];
        String main = p.raw().getString("w_main", null), nam = p.raw().getString("w_namche", null);
        long at = p.raw().getLong("w_at", 0);
        boolean sameLoc = loc.equals(p.raw().getString("w_loc", ""));
        boolean fresh = sameLoc
                && System.currentTimeMillis() - at < c.getResources().getInteger(R.integer.weather_max_age_min) * 60_000L;
        if (!fresh) {
            try {
                main = get("https://api.open-meteo.com/v1/forecast?latitude=" + ll[0] + "&longitude=" + ll[1]
                        + "&current=temperature_2m,weather_code,wind_speed_10m,is_day"
                        + "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum"
                        + "&timezone=Asia%2FKathmandu&forecast_days=4");
                nam = get("https://api.open-meteo.com/v1/forecast?latitude=" + NAMCHE[0] + "&longitude=" + NAMCHE[1]
                        + "&current=temperature_2m,weather_code&timezone=Asia%2FKathmandu");
                at = System.currentTimeMillis();
                p.raw().edit().putString("w_main", main).putString("w_namche", nam).putLong("w_at", at)
                        .putString("w_loc", loc).apply();
            } catch (IOException e) {
                if (!sameLoc) return null; // never show another city's cached weather
            }
        }
        if (main == null) return null;
        try {
            return parse(main, nam, at);
        } catch (JSONException e) {
            return null;
        }
    }

    static String get(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            con.disconnect();
        }
    }

    /** Parses Open-Meteo responses into the UI model. */
    static Weather parse(String mainJson, String namcheJson, long updatedAt) throws JSONException {
        Weather w = new Weather();
        w.updatedAt = updatedAt;
        JSONObject o = new JSONObject(mainJson);
        JSONObject cur = o.getJSONObject("current");
        w.temp = cur.getDouble("temperature_2m");
        w.code = cur.getInt("weather_code");
        w.wind = cur.getDouble("wind_speed_10m");
        w.isDay = cur.getInt("is_day") == 1;

        JSONObject d = o.getJSONObject("daily");
        w.hi = d.getJSONArray("temperature_2m_max").getDouble(0);
        w.lo = d.getJSONArray("temperature_2m_min").getDouble(0);
        w.rainToday = d.getJSONArray("precipitation_sum").optDouble(0, 0);
        JSONArray times = d.getJSONArray("time");
        for (int i = 1; i < Math.min(4, times.length()); i++) {
            Day day = new Day();
            day.date = LocalDate.parse(times.getString(i));
            day.code = d.getJSONArray("weather_code").getInt(i);
            day.hi = d.getJSONArray("temperature_2m_max").getDouble(i);
            day.lo = d.getJSONArray("temperature_2m_min").getDouble(i);
            day.rainProb = d.getJSONArray("precipitation_probability_max").optInt(i, 0);
            w.next.add(day);
        }

        JSONObject h = o.getJSONObject("hourly");
        int[][] ranges = {{6, 12}, {12, 17}, {17, 21}, {21, 30}}; // night runs into tomorrow 06:00
        for (int p = 0; p < 4; p++) {
            w.parts[p] = part(h.getJSONArray("weather_code"), h.getJSONArray("temperature_2m"),
                    h.getJSONArray("precipitation_probability"), ranges[p][0], ranges[p][1]);
        }
        if (namcheJson != null) {
            JSONObject n = new JSONObject(namcheJson).getJSONObject("current");
            w.namche = n.getDouble("temperature_2m");
            w.namcheCode = n.getInt("weather_code");
        }
        return w;
    }

    /** Dominant code per day part; any hour with rain probability of 50% or more marks the part as wet. */
    static Part part(JSONArray codes, JSONArray temps, JSONArray probs, int from, int to) {
        Part p = new Part();
        Map<Integer, Integer> count = new HashMap<>();
        double sum = 0;
        int n = 0, wetCode = -1;
        for (int i = from; i < to && i < codes.length(); i++) {
            int code = codes.optInt(i);
            count.merge(code, 1, Integer::sum);
            sum += temps.optDouble(i, 0);
            n++;
            int prob = probs.optInt(i, 0);
            p.rainProb = Math.max(p.rainProb, prob);
            if (prob >= 50 && isWet(code)) {
                wetCode = Math.max(wetCode, code);
                if (p.rainFromHour < 0) p.rainFromHour = i % 24;
            }
        }
        int best = 0, bestN = -1;
        for (Map.Entry<Integer, Integer> e : count.entrySet()) if (e.getValue() > bestN) { best = e.getKey(); bestN = e.getValue(); }
        p.code = wetCode >= 0 ? wetCode : best;
        p.temp = n == 0 ? 0 : sum / n;
        return p;
    }

    static boolean isWet(int code) { return code >= 51; }

    /** Appendix B demo data (Kathmandu). */
    public static Weather fake() {
        Weather w = new Weather();
        w.temp = 14; w.code = 0; w.isDay = false; w.hi = 21; w.lo = 9; w.wind = 6; w.rainToday = 4;
        w.updatedAt = System.currentTimeMillis() - 120_000;
        int[][] parts = {{0, 16, 0, -1}, {61, 19, 70, 14}, {3, 15, 20, -1}, {0, 11, 0, -1}};
        for (int i = 0; i < 4; i++) {
            w.parts[i].code = parts[i][0]; w.parts[i].temp = parts[i][1];
            w.parts[i].rainProb = parts[i][2]; w.parts[i].rainFromHour = parts[i][3];
        }
        int[][] days = {{61, 18, 10, 80}, {0, 22, 9, 0}, {2, 21, 10, 10}};
        for (int i = 0; i < 3; i++) {
            Day d = new Day();
            d.date = LocalDate.now().plusDays(i + 1);
            d.code = days[i][0]; d.hi = days[i][1]; d.lo = days[i][2]; d.rainProb = days[i][3];
            w.next.add(d);
        }
        w.namche = -6; w.namcheCode = 85;
        return w;
    }

    // ---- WMO code mapping (PRD 8.3) ----

    public static int icon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.ic_sunny : R.drawable.ic_bedtime;
        if (code <= 2) return R.drawable.ic_partly_cloudy_day;
        if (code == 3) return R.drawable.ic_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_foggy;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_ac_unit;
        if (code >= 95) return R.drawable.ic_thunderstorm;
        if (code >= 51) return R.drawable.ic_rainy;
        return R.drawable.ic_cloud;
    }

    public static int label(int code, boolean day) {
        if (code == 0) return day ? R.string.cond_sunny : R.string.cond_clear;
        if (code <= 2) return R.string.cond_partly;
        if (code == 3) return R.string.cond_cloudy;
        if (code == 45 || code == 48) return R.string.cond_fog;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.string.cond_snow;
        if (code >= 95) return R.string.cond_thunder;
        if (code >= 51) return R.string.cond_rain;
        return R.string.cond_cloudy;
    }

    // ---- Units (PRD 8.3) ----

    public static long toF(double c) { return Math.round(c * 9 / 5 + 32); }
    public static double toMs(double kmh) { return Math.round(kmh / 3.6 * 10) / 10.0; }
    public static long toMph(double kmh) { return Math.round(kmh / 1.609); }
    public static double toInches(double mm) { return Math.round(mm / 25.4 * 100) / 100.0; }

    public static String temp(Prefs p, double c) {
        long v = p.is(Prefs.TEMP_UNIT, "f") ? toF(c) : Math.round(c);
        return Ui.digits(p, v + "°");
    }

    public static String wind(Context c, Prefs p, double kmh) {
        switch (p.str(Prefs.WIND_UNIT)) {
            case "ms": return Ui.digits(p, c.getString(R.string.unit_ms, String.valueOf(toMs(kmh))));
            case "mph": return Ui.digits(p, c.getString(R.string.unit_mph, String.valueOf(toMph(kmh))));
            default: return Ui.digits(p, c.getString(R.string.unit_kmh, String.valueOf(Math.round(kmh))));
        }
    }

    public static String rain(Context c, Prefs p, double mm) {
        return p.is(Prefs.RAIN_UNIT, "in")
                ? Ui.digits(p, c.getString(R.string.unit_in, String.valueOf(toInches(mm))))
                : Ui.digits(p, c.getString(R.string.unit_mm, String.valueOf(Math.round(mm))));
    }

    /**
     * Day summary, e.g. "Sunny morning, rain from 2 PM, clear tonight" (PRD 8.3).
     * Uses morning, afternoon and night; identical consecutive parts merge.
     */
    public String summary(Context c, Locale locale) {
        String m = c.getString(label(parts[0].code, true));
        String a = c.getString(label(parts[1].code, true));
        String n = c.getString(label(parts[3].code, false));
        if (parts[0].code == parts[1].code && parts[1].code == parts[3].code && !isWet(parts[1].code)) {
            return c.getString(R.string.sum_all_day, m);
        }
        List<String> out = new ArrayList<>();
        out.add(c.getString(R.string.sum_morning, m));
        if (parts[1].rainFromHour >= 0) {
            String hour = LocalTime.of(parts[1].rainFromHour, 0).format(DateTimeFormatter.ofPattern("h a", locale));
            out.add(c.getString(R.string.sum_rain_from, hour));
        } else if (parts[1].code != parts[0].code) {
            out.add(c.getString(R.string.sum_afternoon, a.toLowerCase(locale)));
        }
        out.add(c.getString(R.string.sum_tonight, n.toLowerCase(locale)));
        return String.join(c.getString(R.string.sum_sep), out);
    }

    public static String dayName(LocalDate d, Locale l) { return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, l); }
}
