package mlbinjuries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the JSON array returned by the SportsData.io
 * {@code /v3/mlb/projections/json/InjuredPlayers} endpoint into {@link InjuredPlayer} records.
 *
 * <p>The parser is deliberately tolerant: any field may be missing or {@code null} in the feed,
 * and a missing name or team is the only thing that drops a row.
 */
public final class InjuryParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InjuryParser() {
    }

    public static List<InjuredPlayer> parse(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (root == null || !root.isArray()) {
            throw new IOException("Expected a JSON array of players");
        }
        List<InjuredPlayer> players = new ArrayList<>();
        for (JsonNode node : root) {
            String name = fullName(node);
            String team = text(node, "Team");
            if (name.isEmpty() || team.isEmpty()) {
                continue;
            }
            String status = text(node, "InjuryStatus");
            if (status.isEmpty()) {
                status = text(node, "Status");
            }
            players.add(new InjuredPlayer(
                    name,
                    team,
                    text(node, "Position"),
                    status,
                    text(node, "InjuryBodyPart"),
                    date(node, "InjuryStartDate"),
                    text(node, "InjuryNotes")));
        }
        return players;
    }

    private static String fullName(JsonNode node) {
        String first = text(node, "FirstName");
        String last = text(node, "LastName");
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        return text(node, "FantasyDraftName");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText().trim();
    }

    private static LocalDate date(JsonNode node, String field) {
        String raw = text(node, field);
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
