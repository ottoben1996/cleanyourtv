package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

/**
 * The football parser, the competition/team rules and the row's selection, on plain JVM (the real
 * org.json comes from the test dependencies). The API payload is the shape of football-data.org v4.
 */
public class FootBallTest {

    private static final String CHANNELS =
            "FL1=Canal+|CL=beIN Sports 1|PL=Canal+|PD=beIN Sports 1";
    private static final String TEAMS = "PD:Real Madrid,FC Barcelona";

    private static String payload() {
        return "{ \"matches\": ["
                + match(1001, "2026-10-20T19:00:00Z", "SCHEDULED", null, "FL1", "Ligue 1",
                        "Paris Saint-Germain FC", "Paris SG", "Olympique de Marseille", "Marseille",
                        null, null)
                + "," + match(1002, "2026-10-20T18:30:00Z", "IN_PLAY", 67, "PD", "Primera Division",
                        "Real Madrid CF", "Real Madrid", "Sevilla FC", "Sevilla", 2, 1)
                + "," + match(1003, "2026-10-20T19:30:00Z", "TIMED", null, "PD", "Primera Division",
                        "Real Betis Balompie", "Betis", "Valencia CF", "Valencia", null, null)
                + "," + match(1004, "2026-10-20T19:00:00Z", "FINISHED", null, "CL",
                        "UEFA Champions League", "FC Barcelona", "Barcelona",
                        "FC Bayern Munchen", "Bayern", 3, 2)
                + "," + match(1005, "2026-10-20T19:00:00Z", "TIMED", null, "PL", "Premier League",
                        "Arsenal FC", "Arsenal", "Liverpool FC", "Liverpool", null, null)
                + "," + match(1006, "2026-10-20T19:00:00Z", "SCHEDULED", null, "PD",
                        "Primera Division", "Girona FC", "Girona", "CA Osasuna", "Osasuna", null, null)
                + "] }";
    }

    private static String match(long id, String utcDate, String status, Integer minute,
                                String code, String competition, String homeName, String homeShort,
                                String awayName, String awayShort, Integer homeScore, Integer awayScore) {
        return "{ \"id\": " + id
                + ", \"utcDate\": \"" + utcDate + "\""
                + ", \"status\": \"" + status + "\""
                + (minute == null ? "" : ", \"minute\": " + minute)
                + ", \"competition\": { \"code\": \"" + code + "\", \"name\": \"" + competition + "\" }"
                + ", \"homeTeam\": { \"name\": \"" + homeName + "\", \"shortName\": \"" + homeShort + "\" }"
                + ", \"awayTeam\": { \"name\": \"" + awayName + "\", \"shortName\": \"" + awayShort + "\" }"
                + ", \"score\": { \"fullTime\": { \"home\": "
                + (homeScore == null ? "null" : homeScore)
                + ", \"away\": " + (awayScore == null ? "null" : awayScore) + " } } }";
    }

    @Test
    public void keepsEveryListedCompetitionAndTheFollowedClubsOnly() throws Exception {
        List<FootBall.Match> matches = FootBall.parse(new JSONObject(payload()), CHANNELS, TEAMS);

        // Ligue 1, Real Madrid, Barcelona in the Champions League and the Premier League: four of six.
        assertEquals(4, matches.size());
        assertEquals(1002, matches.get(0).id);
        assertEquals(1001, matches.get(1).id);
        assertEquals(1005, matches.get(2).id);
        assertEquals(1004, matches.get(3).id);
    }

    @Test
    public void readsScoresMinuteChannelAndShortNames() throws Exception {
        List<FootBall.Match> matches = FootBall.parse(new JSONObject(payload()), CHANNELS, TEAMS);
        FootBall.Match live = matches.get(0);

        assertTrue(live.live());
        assertTrue(live.scored());
        assertEquals(67, live.minute);
        assertEquals(2, live.homeScore);
        assertEquals(1, live.awayScore);
        assertEquals("Real Madrid", live.home);
        assertEquals("Sevilla", live.away);
        assertEquals("beIN Sports 1", live.channel);
        assertEquals("PD", live.competition);
    }

    @Test
    public void aMatchWithoutScoreKeepsTheColumnsEmpty() throws Exception {
        List<FootBall.Match> matches = FootBall.parse(new JSONObject(payload()), CHANNELS, TEAMS);
        FootBall.Match scheduled = matches.get(1);

        assertFalse(scheduled.live());
        assertFalse(scheduled.scored());
        assertFalse(scheduled.finished());
        assertEquals(-1, scheduled.homeScore);
        assertEquals(-1, scheduled.awayScore);
        assertEquals("Canal+", scheduled.channel);
        assertEquals("Paris SG", scheduled.home);
    }

    @Test
    public void resultsComeLastAndTheMostRecentFirst() throws Exception {
        List<FootBall.Match> matches = FootBall.parse(new JSONObject(payload()), CHANNELS, TEAMS);
        FootBall.Match result = matches.get(3);

        assertTrue(result.finished());
        assertEquals("Barcelona", result.home);
        assertEquals(3, result.homeScore);
        assertEquals(2, result.awayScore);
        assertEquals("beIN Sports 1", result.channel);
    }

    @Test
    public void selectDropsOldResultsAndCapsTheRow() throws Exception {
        long now = Instant.parse("2026-10-20T20:30:00Z").toEpochMilli();
        List<FootBall.Match> all = FootBall.parse(new JSONObject(payload()), CHANNELS, TEAMS);

        List<FootBall.Match> kept = FootBall.select(all, now, 10);
        assertEquals(4, kept.size());

        // Five hours later the 19:00 match is over: no upcoming row, no result from the afternoon.
        List<FootBall.Match> late = FootBall.select(all,
                Instant.parse("2026-10-21T00:00:00Z").toEpochMilli(), 10);
        assertEquals(1, late.size());
        assertEquals(1002, late.get(0).id);

        assertEquals(3, FootBall.select(all, now, 3).size());
    }

    @Test
    public void theChannelsSpecAlsoAcceptsSemicolons() throws Exception {
        List<FootBall.Match> matches = FootBall.parse(new JSONObject(payload()),
                "FL1=Canal+;PD=beIN Sports 2", "");
        assertEquals("Canal+", matches.get(1).channel);
        assertEquals("beIN Sports 2", matches.get(0).channel);
        // With no team rule, every La Liga match is kept.
        assertEquals(6, matches.size());
    }

    @Test
    public void sameComparesIdsScoresAndStatuses() throws Exception {
        List<FootBall.Match> a = FootBall.parse(new JSONObject(payload()), CHANNELS, TEAMS);
        List<FootBall.Match> b = FootBall.parse(new JSONObject(payload()), CHANNELS, TEAMS);
        assertTrue(FootBall.same(a, b));

        List<FootBall.Match> goal = FootBall.parse(
                new JSONObject(payload().replace("\"home\": 2, \"away\": 1",
                        "\"home\": 3, \"away\": 1")), CHANNELS, TEAMS);
        assertFalse(FootBall.same(a, goal));
        assertFalse(FootBall.same(a, a.subList(0, 2)));
    }

    @Test
    public void accentsAndCaseDoNotMatterInTheTeamRule() throws Exception {
        assertEquals("seville", FootBall.normalize("  Séville "));
        assertEquals("fc bayern munchen", FootBall.normalize("FC Bayern München"));
        assertEquals("", FootBall.normalize(null));
    }

    @Test
    public void anEmptyOrBrokenAnswerIsAnEmptyList() throws Exception {
        assertTrue(FootBall.parse(null, CHANNELS, TEAMS).isEmpty());
        assertTrue(FootBall.parse(new JSONObject("{}"), CHANNELS, TEAMS).isEmpty());
    }
}
