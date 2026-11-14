package np.exendahal.summitlauncher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Sports scores (PRD 8.4). prodData reads ESPN's public scoreboard (no key) until the PRD's own proxy
 * exists; fakeData uses demo matches. Home shows only matches that are live, start within the next hour,
 * or finished within the last hour.
 */
public final class Scores {
    private Scores() {}

    /**
     * League id to {ESPN path, display name, sport}. Leagues missing here (NPL, IPL, Big Bash, international
     * cricket, EuroLeague, tennis, F1, MotoGP, golf) have no free feed yet and show nothing.
     */
    private static final Map<String, String[]> ESPN = new HashMap<>();
    static {
        String[][] rows = {
                {"pl", "soccer/eng.1", "Premier League", "football"},
                {"laliga", "soccer/esp.1", "LaLiga", "football"},
                {"ucl", "soccer/uefa.champions", "UCL", "football"},
                {"seriea", "soccer/ita.1", "Serie A", "football"},
                {"bundesliga", "soccer/ger.1", "Bundesliga", "football"},
                {"ligue1", "soccer/fra.1", "Ligue 1", "football"},
                {"nba", "basketball/nba", "NBA", "basketball"},
                {"nfl", "football/nfl", "NFL", "american_football"},
                {"nhl", "hockey/nhl", "NHL", "hockey"},
                {"mlb", "baseball/mlb", "MLB", "baseball"},
        };
        for (String[] r : rows) ESPN.put(r[0], new String[]{r[1], r[2], r[3]});
    }

    private static Result cache;
    private static String cacheKey;

    /**
     * Loads matches for the selected leagues, refreshing every 60 s while a match is live and every
     * 15 min otherwise (PRD 8.4). When offline the last result is kept and ages into the stale state.
     * Call off the main thread.
     */
    public static synchronized Result load(Context c) {
        if (BuildConfig.FAKE_DATA) return fake();
        Set<String> leagues = Prefs.get(c).leagues();
        String key = String.join(",", leagues);
        long now = System.currentTimeMillis();
        if (cache != null && key.equals(cacheKey)) {
            boolean live = false;
            for (Match m : cache.matches) live |= m.live();
            if (now - cache.updatedAt < (live ? 60_000L : 15 * 60_000L)) return cache;
        }
        List<Match> all = new ArrayList<>();
        boolean ok = false, failed = false;
        for (String id : leagues) {
            String[] src = ESPN.get(id);
            if (src == null) continue;
            try {
                all.addAll(parseEspn(Weather.get("https://site.api.espn.com/apis/site/v2/sports/" + src[0] + "/scoreboard"),
                        id, src[1], src[2]));
                ok = true;
            } catch (IOException | JSONException e) {
                failed = true;
            }
        }
        if (failed && !ok) {
            // Nothing reachable: keep the last data (it turns stale after 15 min), or report stale now.
            return cache != null && key.equals(cacheKey) ? cache : new Result(new ArrayList<>(), 0);
        }
        cache = new Result(all, now);
        cacheKey = key;
        return cache;
    }

    /** Parses one ESPN scoreboard response into matches. */
    static List<Match> parseEspn(String json, String league, String leagueName, String sport) throws JSONException {
        List<Match> out = new ArrayList<>();
        JSONArray events = new JSONObject(json).optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            JSONObject type = e.getJSONObject("status").getJSONObject("type");
            JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
            JSONObject home = null, away = null;
            JSONArray teams = comp.getJSONArray("competitors");
            for (int t = 0; t < teams.length(); t++) {
                JSONObject x = teams.getJSONObject(t);
                if ("home".equals(x.optString("homeAway"))) home = x; else away = x;
            }
            if (home == null || away == null) continue;
            String state = type.optString("state"), name = type.optString("name");
            String status = "in".equals(state) ? "live" : "pre".equals(state) ? "scheduled"
                    : name.contains("POSTPONED") || name.contains("CANCELED") ? "postponed" : "ft";
            JSONObject ht = home.getJSONObject("team"), at = away.getJSONObject("team");
            out.add(new Match(league, leagueName, sport, status,
                    ht.optString("shortDisplayName", ht.optString("displayName")), ht.optString("abbreviation"),
                    "scheduled".equals(status) ? "" : home.optString("score"),
                    at.optString("shortDisplayName", at.optString("displayName")), at.optString("abbreviation"),
                    "scheduled".equals(status) ? "" : away.optString("score"),
                    type.optString("shortDetail"), type.optString("description"),
                    comp.optJSONObject("venue") == null ? "" : comp.getJSONObject("venue").optString("fullName"),
                    parseTime(e.optString("date")), 0));
        }
        return out;
    }

    /** ESPN sends "2026-11-08T14:00Z" (no seconds). */
    private static long parseTime(String iso) {
        try {
            return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Relevance window either side of now. */
    static final long WINDOW_MS = 60 * 60_000L;

    /** One match, mirroring the proxy contract in docs/proxy-api.md. Times are epoch ms; endTime 0 = unknown. */
    public static final class Match {
        public final String id, league, leagueName, sport, status, homeName, homeShort, homeScore,
                awayName, awayShort, awayScore, clock, summary, venue;
        public final long startTime, endTime;

        Match(String league, String leagueName, String sport, String status, String homeName, String homeShort,
              String homeScore, String awayName, String awayShort, String awayScore, String clock,
              String summary, String venue, long startTime, long endTime) {
            this.id = league + "-" + homeShort + awayShort;
            this.league = league; this.leagueName = leagueName; this.sport = sport; this.status = status;
            this.homeName = homeName; this.homeShort = homeShort; this.homeScore = homeScore;
            this.awayName = awayName; this.awayShort = awayShort; this.awayScore = awayScore;
            this.clock = clock; this.summary = summary; this.venue = venue;
            this.startTime = startTime; this.endTime = endTime;
        }

        public boolean live() { return "live".equals(status) || "ht".equals(status); }

        public boolean scheduled() { return "scheduled".equals(status); }

        /** End time, or an estimate from typical match length when the feed does not send one. */
        long finishedAt() {
            if (endTime > 0) return endTime;
            long hours = "cricket".equals(sport) ? 4 : "football".equals(sport) ? 2 : 3;
            return startTime + hours * 3_600_000L;
        }
    }

    /** Result of a fetch: matches plus the server's updatedAt (epoch ms). */
    public static final class Result {
        public final List<Match> matches;
        public final long updatedAt;
        Result(List<Match> matches, long updatedAt) { this.matches = matches; this.updatedAt = updatedAt; }
    }

    /** Demo data based on Appendix B, with times relative to now so every case is visible. */
    public static Result fake() {
        long now = System.currentTimeMillis(), min = 60_000L;
        List<Match> m = new ArrayList<>();
        m.add(new Match("npl", "NPL", "cricket", "live", "Karnali Yaks", "KAR", "112/3", "Janakpur Bolts", "JAN",
                "164/6", "13.2 ov", "Yaks need 53 from 40 balls", "TU Ground, Kirtipur", now - 100 * min, 0));
        m.add(new Match("pl", "Premier League", "football", "ft", "Arsenal", "ARS", "2", "Chelsea", "CHE", "1",
                "", "Full time", "London", now - 135 * min, now - 30 * min));
        m.add(new Match("laliga", "LaLiga", "football", "live", "Real Madrid", "RMA", "1", "Barcelona", "BAR", "1",
                "67′", "Second half · 67 minutes", "Madrid", now - 75 * min, 0));
        m.add(new Match("ucl", "UCL", "football", "scheduled", "Liverpool", "LIV", "", "Paris SG", "PSG", "",
                "", "", "Liverpool", now + 40 * min, 0));
        m.add(new Match("ucl", "UCL", "football", "ft", "Inter", "INT", "0", "Bayern Munich", "BAY", "2",
                "", "Full time", "Milan", now - 7 * 60 * min, now - 5 * 60 * min)); // too old: hidden
        m.add(new Match("ipl", "IPL", "cricket", "offseason", "", "", "", "", "", "", "", "Starts March", "", 0, 0));
        m.add(new Match("nba", "NBA", "basketball", "live", "LA Lakers", "LAL", "108", "Boston", "BOS", "112",
                "Q4", "", "", now - 140 * min, 0));
        return new Result(m, now - 2 * min);
    }

    /** True when the match belongs on Home right now (live, starting within an hour, or finished within an hour). */
    static boolean relevant(Match m, long now) {
        if (m.live()) return true;
        if (m.scheduled()) return m.startTime >= now - WINDOW_MS && m.startTime <= now + WINDOW_MS;
        if ("ft".equals(m.status)) return now - m.finishedAt() <= WINDOW_MS;
        return false; // off-season, postponed, older results
    }

    private static int rank(Match m, boolean liveFirst) {
        if (m.live()) return liveFirst ? 0 : 1;
        if ("ft".equals(m.status)) return 1;
        return 2; // upcoming
    }

    /** Filters to selected leagues and the relevance window, orders live, finished, upcoming; then limits (PRD 8.4). */
    public static List<Match> forHome(List<Match> all, Set<String> leagues, boolean liveFirst, int max, long now) {
        List<Match> out = new ArrayList<>();
        for (Match m : all) if (leagues.contains(m.league) && relevant(m, now)) out.add(m);
        out.sort((a, b) -> Integer.compare(rank(a, liveFirst), rank(b, liveFirst))); // stable sort keeps API order
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** "KAR 112/3 v JAN 164/6" for cricket, "ARS 2–1 CHE" otherwise, "LIV v PSG" before kick-off. */
    public static String chipText(Match m) {
        if (m.scheduled()) return m.homeShort + " v " + m.awayShort;
        if ("cricket".equals(m.sport)) return m.homeShort + " " + m.homeScore + " v " + m.awayShort + " " + m.awayScore;
        return m.homeShort + " " + m.homeScore + "–" + m.awayScore + " " + m.awayShort;
    }

    /** Clock when live, FT when finished, kick-off time when scheduled. */
    public static String statusText(Match m, String ft, String kickOff) {
        if (m.live()) return m.clock;
        if ("ft".equals(m.status)) return ft;
        return m.scheduled() ? kickOff : "";
    }

    /** True when data is older than the stale limit; stale data is never shown as live. */
    public static boolean isStale(long updatedAt, int staleMinutes) {
        return System.currentTimeMillis() - updatedAt > staleMinutes * 60_000L;
    }
}
