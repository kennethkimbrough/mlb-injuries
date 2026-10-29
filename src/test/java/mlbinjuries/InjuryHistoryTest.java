package mlbinjuries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InjuryHistoryTest {

    static final InjuredPlayer ALEX = new InjuredPlayer(900004, "Alex Example", "NYY", "P", "60-day IL",
            "Right elbow surgery", LocalDate.of(2026, 4, 12),
            "New York Yankees transferred RHP Alex Example from the 15-day injured list to the 60-day injured list. Right elbow surgery.");
    static final InjuredPlayer CASEY = new InjuredPlayer(900002, "Casey Sample", "BOS", "C", "10-day IL",
            "", null, "");
    static final InjuredPlayer QUOTED = new InjuredPlayer(0, "No Id \"Q\"", "NYY", "RF", "15-day IL",
            "Left wrist sprain, mild", LocalDate.of(2026, 5, 3), "Text with, commas and \"quotes\"");

    static final LocalDate D1 = LocalDate.of(2026, 6, 1);

    @Test
    void roundTripsRowsIncludingCommasQuotesAndMissingFields(@TempDir Path dir) throws IOException {
        InjuryHistory history = new InjuryHistory(dir.resolve("injuries.csv"));
        assertTrue(history.save(D1, List.of(ALEX, CASEY, QUOTED)));

        InjuryHistory.Contents c = history.load();
        assertEquals(List.of(D1), List.copyOf(c.snapshotDates()));
        // Rows are sorted by team then name: BOS before NYY.
        assertEquals(List.of(CASEY, ALEX, QUOTED), c.entries().stream().map(InjuryHistory.Entry::player).toList());
    }

    @Test
    void savingTheSameDateTwiceReplacesInsteadOfDuplicating(@TempDir Path dir) throws IOException {
        InjuryHistory history = new InjuryHistory(dir.resolve("injuries.csv"));
        history.save(D1, List.of(ALEX, CASEY));
        history.save(D1, List.of(ALEX));
        history.save(D1.plusDays(1), List.of(ALEX, QUOTED));

        List<InjuryHistory.Entry> entries = history.load().entries();
        assertEquals(3, entries.size());
        assertEquals(1, entries.stream().filter(e -> e.snapshotDate().equals(D1)).count());
    }

    @Test
    void anEmptyDayIsRecordedOnceAfterAListedDay(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("injuries.csv");
        InjuryHistory history = new InjuryHistory(file);
        history.save(D1, List.of(ALEX));

        assertTrue(history.save(D1.plusDays(1), List.of()), "first empty day is recorded");
        assertFalse(history.save(D1.plusDays(2), List.of()), "a second empty day in a row adds nothing");

        InjuryHistory.Contents c = history.load();
        assertEquals(List.of(D1, D1.plusDays(1)), List.copyOf(c.snapshotDates()));
        assertEquals(1, c.entries().size());
        assertTrue(Files.readAllLines(file).contains("2026-06-02,,,,,,,,"));

        assertTrue(history.save(D1.plusDays(3), List.of(CASEY)), "a listed day after the gap is recorded");
        assertEquals(3, history.load().snapshotDates().size());
    }

    @Test
    void anEmptyDayCanBeReplacedByAListedOne(@TempDir Path dir) throws IOException {
        InjuryHistory history = new InjuryHistory(dir.resolve("injuries.csv"));
        history.save(D1, List.of());
        history.save(D1, List.of(ALEX));
        InjuryHistory.Contents c = history.load();
        assertEquals(1, c.snapshotDates().size());
        assertEquals(1, c.entries().size());
    }

    @Test
    void writesTheHeaderAndCreatesParentDirectories(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested").resolve("injuries.csv");
        new InjuryHistory(file).save(D1, List.of(CASEY));
        List<String> lines = Files.readAllLines(file);
        assertEquals(InjuryHistory.HEADER, lines.get(0));
        assertEquals("2026-06-01,900002,Casey Sample,BOS,C,10-day IL,,,", lines.get(1));
    }

    @Test
    void missingFileLoadsAsEmpty(@TempDir Path dir) throws IOException {
        InjuryHistory.Contents c = new InjuryHistory(dir.resolve("none.csv")).load();
        assertTrue(c.snapshotDates().isEmpty());
        assertTrue(c.entries().isEmpty());
    }
}
