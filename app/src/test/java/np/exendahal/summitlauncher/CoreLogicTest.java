package np.exendahal.summitlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Pure-logic checks: BS conversion (PRD 8.5), unit conversion (8.3), score ordering (8.4). */
public class CoreLogicTest {
    @BeforeClass
    public static void loadCalendar() throws Exception {
        BikramSambat.load(new String(Files.readAllBytes(Paths.get("src/main/res/raw/bs_calendar.json")), "UTF-8"));
    }

    @Test
    public void newerReleaseIsDetected() {
        assertTrue(Updates.isNewer("v0.2.0", "0.1.0"));
        assertTrue(Updates.isNewer("v0.10.0", "0.9.5")); // numbers, not text
        assertTrue(Updates.isNewer("1.0", "0.9.9"));
        assertTrue(!Updates.isNewer("v0.1.0", "0.1.0"));
        assertTrue(!Updates.isNewer("v0.1.0", "0.1.0-debug"));
        assertTrue(!Updates.isNewer("v0.1", "0.1.0"));
        assertTrue(!Updates.isNewer("v0.1.0", "0.2.0"));
        assertTrue(!Updates.isNewer("nightly", "0.1.0")); // an unversioned tag never nags
    }

    /** BS date -> AD date. New-year dates are Nepal's official Baisakh 1 dates. */
    private static final Object[][] KNOWN = {
            {2000, 1, 1, "1943-04-14"},
            {2075, 1, 1, "2018-04-14"},
            {2076, 1, 1, "2019-04-14"},
            {2077, 1, 1, "2020-04-13"},
            {2078, 1, 1, "2021-04-14"},
            {2079, 1, 1, "2022-04-14"},
            {2080, 1, 1, "2023-04-14"},
            {2081, 1, 1, "2024-04-13"},
            {2082, 1, 1, "2025-04-14"},
            {2083, 1, 1, "2026-04-14"},
            {2083, 7, 1, "2026-10-18"},   // Kartik 1 2083
            {2083, 7, 27, "2026-11-13"},  // PRD design sample
    };

    @Test
    public void knownDatesConvertBothWays() {
        for (Object[] k : KNOWN) {
            LocalDate ad = LocalDate.parse((String) k[3]);
            BikramSambat bs = BikramSambat.fromGregorian(ad);
            String label = Arrays.toString(k);
            assertEquals(label, k[0], bs.year);
            assertEquals(label, k[1], bs.month);
            assertEquals(label, k[2], bs.day);
            assertEquals(label, ad, BikramSambat.toGregorian((int) k[0], (int) k[1], (int) k[2]));
        }
    }

    @Test
    public void yearBoundaryAndRoundTrip() {
        // Last day of 2082 is the day before 2083 new year.
        BikramSambat end = BikramSambat.fromGregorian(LocalDate.parse("2026-04-13"));
        assertEquals(2082, end.year);
        assertEquals(12, end.month);
        for (LocalDate d = LocalDate.parse("2018-01-01"); d.isBefore(LocalDate.parse("2030-01-01")); d = d.plusDays(1)) {
            BikramSambat b = BikramSambat.fromGregorian(d);
            assertEquals(d, BikramSambat.toGregorian(b.year, b.month, b.day));
        }
    }

    @Test
    public void nepaliFormat() {
        assertEquals("शुक्रबार, कार्तिक २७, २०८३", BikramSambat.fromGregorian(LocalDate.parse("2026-11-13")).format(true));
    }

    @Test
    public void unitConversions() {
        assertEquals(57, Weather.toF(14));
        assertEquals(-4, Weather.toF(-20));
        assertEquals(1.7, Weather.toMs(6), 1e-9);
        assertEquals(4, Weather.toMph(6));
        assertEquals(0.16, Weather.toInches(4), 1e-9);
    }

    @Test
    public void scoresOnlyLiveOrWithinAnHour() {
        long now = System.currentTimeMillis();
        List<Scores.Match> all = Scores.fake().matches;
        List<Scores.Match> home = Scores.forHome(all, new HashSet<>(Arrays.asList("npl", "pl", "laliga", "ucl", "ipl")), true, 6, now);
        // NPL live, LaLiga live, PL finished 30 min ago, UCL starts in 40 min. Not: UCL finished 5 h ago, IPL off-season.
        assertEquals(4, home.size());
        assertTrue(home.get(0).live() && home.get(1).live());
        assertEquals("ft", home.get(2).status);
        assertTrue(home.get(3).scheduled());
        assertEquals("KAR 112/3 v JAN 164/6", Scores.chipText(home.get(0)));
        assertEquals("RMA 1–1 BAR", Scores.chipText(home.get(1)));
        assertEquals("LIV v PSG", Scores.chipText(home.get(3)));
        assertEquals(2, Scores.forHome(all, new HashSet<>(Arrays.asList("npl", "pl", "laliga", "ucl")), true, 2, now).size());
        assertTrue(Scores.forHome(all, new HashSet<>(Arrays.asList("ipl")), true, 4, now).isEmpty());
        // Two hours later the PL result (finished 30 min before "now") is more than an hour old.
        assertTrue(Scores.forHome(all, new HashSet<>(Arrays.asList("pl")), true, 4, now + 2 * 3_600_000L).isEmpty());
        assertTrue(Scores.isStale(now - 16 * 60_000L, 15));
    }

    @Test
    public void parsesEspnScoreboard() throws Exception {
        String json = "{\"events\":[{\"date\":\"2026-11-08T14:00Z\",\"status\":{\"type\":{\"name\":\"STATUS_FULL_TIME\","
                + "\"state\":\"post\",\"description\":\"Full Time\",\"shortDetail\":\"FT\"}},\"competitions\":[{"
                + "\"venue\":{\"fullName\":\"American Express Stadium\"},\"competitors\":["
                + "{\"homeAway\":\"home\",\"score\":\"2\",\"team\":{\"abbreviation\":\"BHA\",\"shortDisplayName\":\"Brighton\"}},"
                + "{\"homeAway\":\"away\",\"score\":\"0\",\"team\":{\"abbreviation\":\"BRE\",\"shortDisplayName\":\"Brentford\"}}]}]}]}";
        List<Scores.Match> m = Scores.parseEspn(json, "pl", "Premier League", "football");
        assertEquals(1, m.size());
        assertEquals("ft", m.get(0).status);
        assertEquals("BHA 2–0 BRE", Scores.chipText(m.get(0)));
        assertEquals(LocalDate.parse("2026-11-08"), java.time.Instant.ofEpochMilli(m.get(0).startTime).atZone(java.time.ZoneOffset.UTC).toLocalDate());
        // A full-time result from days ago is not shown on Home.
        assertTrue(Scores.forHome(m, new HashSet<>(Arrays.asList("pl")), true, 4, System.currentTimeMillis()).isEmpty());
    }

    @Test
    public void nepalTimeZoneBothSpellings() {
        assertTrue(Ui.isNepalTimeZone("Asia/Kathmandu"));
        assertTrue(Ui.isNepalTimeZone("Asia/Katmandu")); // older tz id, used by this Mi TV
        assertTrue(!Ui.isNepalTimeZone("Asia/Kolkata"));
    }

    /** Picture -> theme colour: vivid pixels vote for the nearest swatch hue; greys don't count. */
    @Test
    public void pictureColour() {
        int[] swatches = {0xFFE53935, 0xFF43A047, 0xFF1E88E5}; // red, green, blue
        int[] px = new int[100];
        Arrays.fill(px, 0xFF808080);                // mostly grey wall...
        for (int i = 0; i < 30; i++) px[i] = 0xFF2060C0; // ...with a blue sofa
        for (int i = 30; i < 35; i++) px[i] = 0xFFD03020; // and a little red
        assertEquals(2, ThemeController.closestSwatch(px, swatches));
        Arrays.fill(px, 0xFF808080);
        assertEquals(-1, ThemeController.closestSwatch(px, swatches)); // all grey: no colour
        Arrays.fill(px, 0xFF101010);
        assertEquals(-1, ThemeController.closestSwatch(px, swatches)); // near-black: no colour
        Arrays.fill(px, 0xFFE02060);                 // pinkish red (hue ~340) wraps round to red
        assertEquals(0, ThemeController.closestSwatch(px, swatches));
    }
}
