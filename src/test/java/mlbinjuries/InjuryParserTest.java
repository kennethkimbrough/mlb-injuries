package mlbinjuries;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InjuryParserTest {

    static String sample() throws IOException {
        try (InputStream in = InjuryParserTest.class.getResourceAsStream("/injured-players-sample.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesEveryFieldFromTheFeed() throws IOException {
        List<InjuredPlayer> players = InjuryParser.parse(sample());

        InjuredPlayer alex = players.get(0);
        assertEquals("Alex Example", alex.name());
        assertEquals("NYY", alex.team());
        assertEquals("SP", alex.position());
        assertEquals("Out", alex.status());
        assertEquals("Elbow", alex.bodyPart());
        assertEquals(LocalDate.of(2026, 4, 12), alex.startDate());
        assertEquals("60-day IL, no timetable for return.", alex.notes());
    }

    @Test
    void nullFieldsBecomeEmptyAndNameFallsBackToFantasyDraftName() throws IOException {
        List<InjuredPlayer> players = InjuryParser.parse(sample());

        InjuredPlayer casey = players.get(1);
        assertEquals("", casey.notes());

        InjuredPlayer fallback = players.get(2);
        assertEquals("Fallback Name", fallback.name());
        assertEquals("Injured", fallback.status(), "falls back to Status when InjuryStatus is null");
        assertEquals("", fallback.bodyPart());
        assertNull(fallback.startDate());
    }

    @Test
    void rowsWithoutATeamAreDropped() throws IOException {
        List<InjuredPlayer> players = InjuryParser.parse(sample());
        assertEquals(3, players.size());
    }

    @Test
    void rejectsNonArrayPayloads() {
        assertThrows(IOException.class, () -> InjuryParser.parse("{\"message\": \"unauthorized\"}"));
    }
}
