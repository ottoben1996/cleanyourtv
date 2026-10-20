package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tonight's football, read from football-data.org (free tier: one token, 10 requests a minute):
 * the competitions the owner follows, plus the clubs they follow inside another competition.
 * The launcher only displays it — picking a match opens the football app, where the channels live.
 *
 * Everything here is plain Java except {@link #fetch}, so the parsing, the filters and the order can
 * be tested on the JVM (src/test, FootBallTest).
 */
final class FootBall {

    static final String ENDPOINT = "https://api.football-data.org/v4/matches";
    private static final int READ_LIMIT = 256 * 1024;
    private static final long HOUR_MS = 3_600_000L;

    /** One match, reduced to what a card shows. */
    static final class Match {
        final long id;
        /** Competition code: FL1, CL, PL, PD… */
        final String competition;
        /** The name the API publishes, used when the code has no translation. */
        final String competitionName;
        /** The channel the owner watches this competition on; empty when the config names none. */
        final String channel;
        /** Short names, which is what fits on a card ("Paris SG", "Marseille"). */
        final String home, away;
        /** -1 when the match has not started or the API published no score. */
        final int homeScore, awayScore;
        final long kickoff;
        /** SCHEDULED, TIMED, IN_PLAY, PAUSED, FINISHED, POSTPONED, SUSPENDED, CANCELLED… */
        final String status;
        /** -1 when unknown. */
        final int minute;

        Match(long id, String competition, String competitionName, String channel,
              String home, String away, int homeScore, int awayScore, long kickoff,
              String status, int minute) {
            this.id = id;
            this.competition = competition;
            this.competitionName = competitionName;
            this.channel = channel;
            this.home = home;
            this.away = away;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.kickoff = kickoff;
            this.status = status;
            this.minute = minute;
        }

        boolean live() {
            return "IN_PLAY".equals(status) || "PAUSED".equals(status);
        }

        boolean finished() {
            return "FINISHED".equals(status);
        }

        /** Whether the two score columns mean anything yet. */
        boolean scored() {
            return live() || finished();
        }
    }

    /** Live first, then what is coming, then the results (the most recent first); id breaks ties. */
    static final Comparator<Match> ORDER = (a, b) -> {
        int ra = a.live() ? 0 : a.finished() ? 2 : 1;
        int rb = b.live() ? 0 : b.finished() ? 2 : 1;
        if (ra != rb) return ra - rb;
        int byTime = ra == 2 ? Long.compare(b.kickoff, a.kickoff) : Long.compare(a.kickoff, b.kickoff);
        // The API does not keep a stable order inside a kick-off slot; the id does, so the row does
        // not reshuffle itself on every refresh.
        return byTime != 0 ? byTime : Long.compare(a.id, b.id);
    };

    private FootBall() {
    }

    /**
     * Parses the API answer and applies the owner's rules: every match of a listed competition, and
     * inside a competition named in {@code teamFilter} only the clubs listed there. Sorted by
     * {@link #ORDER}; the caller then cuts the list with {@link #select}.
     *
     * @param channels   "FL1=Canal+|CL=beIN Sports 1", also accepting ';' between entries
     * @param teamFilter "PD:Real Madrid,FC Barcelona", one rule per ';'
     */
    static List<Match> parse(JSONObject root, String channels, String teamFilter) {
        List<Match> out = new ArrayList<>();
        JSONArray matches = root == null ? null : root.optJSONArray("matches");
        if (matches == null) return out;
        Map<String, String> channelByCode = channels(channels);
        Map<String, List<String>> only = teamFilter(teamFilter);
        for (int i = 0; i < matches.length(); i++) {
            JSONObject o = matches.optJSONObject(i);
            if (o == null) continue;
            JSONObject comp = o.optJSONObject("competition");
            JSONObject homeTeam = o.optJSONObject("homeTeam");
            JSONObject awayTeam = o.optJSONObject("awayTeam");
            if (comp == null || homeTeam == null || awayTeam == null) continue;
            String code = comp.optString("code", "").trim().toUpperCase(Locale.ROOT);
            String home = teamName(homeTeam);
            String away = teamName(awayTeam);
            if (home.isEmpty() || away.isEmpty() || !selected(code, only, home, away)) continue;
            long kickoff = instant(o.optString("utcDate", ""));
            if (kickoff <= 0) continue;
            int homeScore = -1;
            int awayScore = -1;
            JSONObject score = o.optJSONObject("score");
            JSONObject full = score == null ? null : score.optJSONObject("fullTime");
            if (full != null && !full.isNull("home") && !full.isNull("away")) {
                homeScore = full.optInt("home", -1);
                awayScore = full.optInt("away", -1);
            }
            out.add(new Match(o.optLong("id", 0L), code, comp.optString("name", code),
                    channelByCode.getOrDefault(code, ""), home, away, homeScore, awayScore, kickoff,
                    o.optString("status", "SCHEDULED").trim().toUpperCase(Locale.ROOT),
                    o.optInt("minute", -1)));
        }
        out.sort(ORDER);
        return out;
    }

    /**
     * What the row keeps: a live match, one that has not started yet, or a result from the last few
     * hours. A finished match older than four hours is yesterday's news, and a match postponed more
     * than three hours past its slot has stopped being "tonight".
     */
    static List<Match> select(List<Match> all, long now, int max) {
        List<Match> out = new ArrayList<>();
        for (Match m : all) {
            if (m.finished() && m.kickoff + 4 * HOUR_MS < now) continue;
            if (!m.finished() && !m.live() && m.kickoff + 3 * HOUR_MS < now) continue;
            out.add(m);
            if (out.size() >= max) break;
        }
        return out;
    }

    /**
     * Whether two answers would draw the same row: same matches in the same order with the same
     * score and status. A tick that changes nothing must not rebuild the cards (and lose their
     * scroll position).
     */
    static boolean same(List<Match> a, List<Match> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Match x = a.get(i);
            Match y = b.get(i);
            if (x.id != y.id || x.homeScore != y.homeScore || x.awayScore != y.awayScore
                    || x.minute != y.minute || !x.status.equals(y.status)) return false;
        }
        return true;
    }

    /**
     * One request for a date window (ISO dates, the API's own days). Returns null on any failure —
     * offline, bad token, rate limit — so the caller keeps the list it already had; an empty list
     * means "no match, successfully".
     */
    static List<Match> fetch(String token, String competitions, String dateFrom, String dateTo,
                             String channels, String teamFilter) {
        HttpURLConnection c = null;
        try {
            String url = ENDPOINT + "?competitions=" + competitions
                    + "&dateFrom=" + dateFrom + "&dateTo=" + dateTo;
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("X-Auth-Token", token);
            try (InputStream in = c.getInputStream()) {
                return parse(new JSONObject(read(in)), channels, teamFilter);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** "FL1=Canal+|CL=beIN Sports 1" (or ';' between entries) to code → channel. */
    private static Map<String, String> channels(String spec) {
        Map<String, String> out = new HashMap<>();
        if (spec == null) return out;
        for (String entry : spec.split("[|;]")) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String code = entry.substring(0, eq).trim().toUpperCase(Locale.ROOT);
            String label = entry.substring(eq + 1).trim();
            if (!code.isEmpty() && !label.isEmpty()) out.put(code, label);
        }
        return out;
    }

    /** "PD:Real Madrid,FC Barcelona" (one rule per ';') to code → clubs, normalised for matching. */
    private static Map<String, List<String>> teamFilter(String spec) {
        Map<String, List<String>> out = new HashMap<>();
        if (spec == null) return out;
        for (String rule : spec.split(";")) {
            int colon = rule.indexOf(':');
            if (colon <= 0) continue;
            String code = rule.substring(0, colon).trim().toUpperCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (String name : rule.substring(colon + 1).split(",")) {
                String n = normalize(name);
                if (!n.isEmpty()) names.add(n);
            }
            if (!code.isEmpty() && !names.isEmpty()) out.put(code, names);
        }
        return out;
    }

    private static boolean selected(String code, Map<String, List<String>> filter,
                                    String home, String away) {
        List<String> clubs = filter.get(code);
        if (clubs == null) return true;                 // no rule: every match of the competition
        String h = normalize(home);
        String a = normalize(away);
        for (String club : clubs) {
            if (h.contains(club) || a.contains(club)) return true;
        }
        return false;
    }

    /** Lower case, without accents: "Séville" matches "Sevilla". */
    static String normalize(String raw) {
        if (raw == null) return "";
        String flat = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return flat.toLowerCase(Locale.ROOT).trim();
    }

    private static String teamName(JSONObject team) {
        String shortName = team.optString("shortName", "").trim();
        return shortName.isEmpty() ? team.optString("name", "").trim() : shortName;
    }

    private static long instant(String iso) {
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String read(InputStream in) throws Exception {
        byte[] buf = new byte[8192];
        StringBuilder out = new StringBuilder();
        int n;
        while ((n = in.read(buf)) > 0 && out.length() < READ_LIMIT) {
            out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        return out.toString();
    }
}
