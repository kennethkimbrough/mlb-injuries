package mlbinjuries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses responses from the MLB Stats API ({@code statsapi.mlb.com/api/v1}).
 *
 * <ul>
 *   <li>{@code /teams?sportId=1}: {@code {"teams": [{"id", "abbreviation", ...}]}}</li>
 *   <li>{@code /teams/{id}/roster}: {@code {"roster": [{"person": {"id", "fullName"}, "position": {"abbreviation"},
 *       "status": {"code", "description"}, "note"?}]}}. Injured-list statuses have codes such as D10, D15, D60.</li>
 *   <li>{@code /transactions}: {@code {"transactions": [{"person": {"id"}, "date", "description", ...}]}}, where
 *       injured-list moves read like "Team placed RHP Name on the 15-day injured list retroactive to April 9, 2026.
 *       Right elbow inflammation."</li>
 * </ul>
 *
 * <p>Every field is read defensively: a missing or null value becomes empty, never an exception.
 */
public final class MlbParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern IL_CODE = Pattern.compile("D(\\d+)");

    private MlbParser() {
    }

    /** The injured-list move that explains a player's current spell. */
    public record IlTransaction(LocalDate placed, String injury, String description) {
    }

    /** Team id to abbreviation. */
    public static Map<Integer, String> parseTeams(String json) throws IOException {
        Map<Integer, String> teams = new LinkedHashMap<>();
        for (JsonNode t : array(read(json), "teams")) {
            int id = t.path("id").asInt(0);
            String abbr = text(t, "abbreviation");
            if (id > 0 && !abbr.isEmpty()) {
                teams.put(id, abbr);
            }
        }
        return teams;
    }

    /** The players on a roster whose status is an injured list. */
    public static List<InjuredPlayer> parseInjuredList(String json, String team) throws IOException {
        List<InjuredPlayer> players = new ArrayList<>();
        for (JsonNode entry : array(read(json), "roster")) {
            String code = text(entry.path("status"), "code");
            String description = text(entry.path("status"), "description");
            if (!isInjured(code, description)) {
                continue;
            }
            JsonNode person = entry.path("person");
            String name = text(person, "fullName");
            if (name.isEmpty()) {
                continue;
            }
            players.add(new InjuredPlayer(
                    person.path("id").asLong(0),
                    name,
                    team,
                    text(entry.path("position"), "abbreviation"),
                    statusLabel(code, description),
                    text(entry, "note"),
                    null,
                    ""));
        }
        return players;
    }

    static boolean isInjured(String code, String description) {
        return IL_CODE.matcher(code).matches() || description.toLowerCase(Locale.ROOT).startsWith("injured");
    }

    /** "D10" becomes "10-day IL"; anything else keeps the API's description. */
    static String statusLabel(String code, String description) {
        Matcher m = IL_CODE.matcher(code);
        if (m.matches()) {
            return m.group(1) + "-day IL";
        }
        return description;
    }

    /**
     * Placements on and transfers between injured lists, by player id. For each player the result holds the date
     * of the most recent placement (the start of the current spell) and the most recent injury description.
     */
    public static Map<Long, IlTransaction> parseIlTransactions(String json) throws IOException {
        record Move(LocalDate date, boolean placement, String injury, String description) {
        }
        Map<Long, List<Move>> byPlayer = new HashMap<>();
        for (JsonNode t : array(read(json), "transactions")) {
            String description = text(t, "description");
            String lower = description.toLowerCase(Locale.ROOT);
            boolean placement = lower.contains(" placed ");
            boolean transfer = lower.contains(" transferred ");
            if (!lower.contains("injured list") || !(placement || transfer)) {
                continue;
            }
            long id = t.path("person").path("id").asLong(0);
            LocalDate date = date(text(t, "date"));
            if (date == null) {
                date = date(text(t, "effectiveDate"));
            }
            if (id <= 0 || date == null) {
                continue;
            }
            byPlayer.computeIfAbsent(id, k -> new ArrayList<>())
                    .add(new Move(date, placement, extractInjury(description), description));
        }

        Map<Long, IlTransaction> result = new HashMap<>();
        for (Map.Entry<Long, List<Move>> e : byPlayer.entrySet()) {
            List<Move> moves = e.getValue();
            moves.sort((a, b) -> a.date().compareTo(b.date()));
            Move latest = moves.get(moves.size() - 1);
            LocalDate placed = null;
            String injury = "";
            for (Move m : moves) {
                if (m.placement()) {
                    placed = m.date();
                    injury = "";
                }
                if (!m.injury().isEmpty()) {
                    injury = m.injury();
                }
            }
            if (placed == null) {
                placed = moves.get(0).date();
            }
            result.put(e.getKey(), new IlTransaction(placed, injury, latest.description()));
        }
        return result;
    }

    /**
     * The injury named after the injured-list clause: "... on the 15-day injured list retroactive to April 9, 2026.
     * Right elbow inflammation." gives "Right elbow inflammation". Returns "" when the text names no injury.
     */
    static String extractInjury(String description) {
        String lower = description.toLowerCase(Locale.ROOT);
        int il = lower.lastIndexOf("injured list");
        if (il < 0) {
            return "";
        }
        int end = description.indexOf(". ", il);
        if (end >= 0) {
            return trimPeriod(description.substring(end + 2));
        }
        int open = description.indexOf('(', il);
        int close = description.indexOf(')', open + 1);
        if (open >= 0 && close > open) {
            return description.substring(open + 1, close).trim();
        }
        return "";
    }

    /** The roster player with placement date, injury and transaction text filled in from the matching move. */
    static InjuredPlayer enrich(InjuredPlayer p, IlTransaction move) {
        if (move == null) {
            return p;
        }
        String injury = move.injury().isEmpty() ? p.injury() : move.injury();
        return new InjuredPlayer(p.playerId(), p.name(), p.team(), p.position(), p.status(), injury,
                move.placed(), move.description());
    }

    private static String trimPeriod(String s) {
        String t = s.trim();
        while (t.endsWith(".")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    private static JsonNode read(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IOException("Expected a JSON object from the MLB Stats API");
        }
        return root;
    }

    private static Iterable<JsonNode> array(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isArray() ? node : List.of();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText().trim();
    }

    private static LocalDate date(String raw) {
        if (raw.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
