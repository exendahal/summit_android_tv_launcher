package np.exendahal.summitlauncher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Bikram Sambat date and AD/BS conversion (PRD 8.5).
 * Month lengths come from res/raw/bs_calendar.json, extracted from the MIT-licensed
 * nepali-date-converter 3.4.2 package (dateConfigMap), covering BS 2000 to 2090.
 * Anchor: BS 2000-01-01 = AD 1943-04-14.
 */
public final class BikramSambat {
    private static final String[] MONTHS_NE = {"बैशाख", "जेठ", "असार", "श्रावण", "भदौ", "आश्विन",
            "कार्तिक", "मंसिर", "पुष", "माघ", "फाल्गुन", "चैत्र"};
    private static final String[] MONTHS_EN = {"Baisakh", "Jestha", "Asar", "Shrawan", "Bhadra", "Ashwin",
            "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra"};
    /** Sunday first. */
    private static final String[] DAYS_NE = {"आइतबार", "सोमबार", "मंगलबार", "बुधबार", "बिहिबार", "शुक्रबार", "शनिबार"};
    private static final String[] DAYS_EN = {"Aaitabar", "Sombar", "Mangalbar", "Budhabar", "Bihibar", "Shukrabar", "Shanibar"};

    private static int startYear;
    private static int[][] months;
    private static LocalDate anchor;

    public final int year, month, day;
    /** 0 = Sunday ... 6 = Saturday. */
    public final int weekday;

    private BikramSambat(int year, int month, int day, int weekday) {
        this.year = year; this.month = month; this.day = day; this.weekday = weekday;
    }

    /** Loads the month table from resources once. */
    public static synchronized void load(Context c) {
        if (months != null) return;
        try (InputStream in = c.getResources().openRawResource(R.raw.bs_calendar)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            load(out.toString(StandardCharsets.UTF_8.name()));
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("bs_calendar.json unreadable", e);
        }
    }

    /** Loads the month table from JSON text (used directly by unit tests). */
    public static synchronized void load(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        startYear = o.getInt("startYear");
        anchor = LocalDate.parse(o.getString("anchorAd"));
        JSONArray rows = o.getJSONArray("months");
        int[][] m = new int[rows.length()][12];
        for (int y = 0; y < rows.length(); y++) {
            JSONArray r = rows.getJSONArray(y);
            for (int i = 0; i < 12; i++) m[y][i] = r.getInt(i);
        }
        months = m;
    }

    /**
     * Converts a Gregorian date.
     *
     * @throws IllegalArgumentException if the date is outside the table range.
     */
    public static BikramSambat fromGregorian(LocalDate date) {
        long days = ChronoUnit.DAYS.between(anchor, date);
        if (days < 0) throw new IllegalArgumentException("Before BS " + startYear);
        int weekday = date.getDayOfWeek().getValue() % 7;
        for (int y = 0; y < months.length; y++) {
            for (int m = 0; m < 12; m++) {
                if (days < months[y][m]) return new BikramSambat(startYear + y, m + 1, (int) days + 1, weekday);
                days -= months[y][m];
            }
        }
        throw new IllegalArgumentException("After BS " + (startYear + months.length - 1));
    }

    /**
     * Converts a BS date to Gregorian.
     *
     * @throws IllegalArgumentException if the year is outside the table range or the day is invalid.
     */
    public static LocalDate toGregorian(int year, int month, int day) {
        int y = year - startYear;
        if (y < 0 || y >= months.length || month < 1 || month > 12 || day < 1 || day > months[y][month - 1]) {
            throw new IllegalArgumentException("Invalid BS date " + year + "-" + month + "-" + day);
        }
        long days = day - 1;
        for (int i = 0; i < y; i++) for (int len : months[i]) days += len;
        for (int m = 0; m < month - 1; m++) days += months[y][m];
        return anchor.plusDays(days);
    }

    /** @return e.g. "शुक्रबार, कार्तिक २७, २०८३" (Nepali) or "Shukrabar, Kartik 27, 2083". */
    public String format(boolean nepali) {
        String s = (nepali ? DAYS_NE : DAYS_EN)[weekday] + ", " + (nepali ? MONTHS_NE : MONTHS_EN)[month - 1]
                + " " + day + ", " + year;
        return nepali ? Ui.nepaliDigits(s) : s;
    }

    /** @return "कार्तिक २७" style month and day. */
    public String monthDay(boolean nepali) {
        String s = (nepali ? MONTHS_NE : MONTHS_EN)[month - 1] + " " + day;
        return nepali ? Ui.nepaliDigits(s) : s;
    }
}
