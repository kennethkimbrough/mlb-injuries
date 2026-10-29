package mlbinjuries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InjuryHistoryTest {

    static final InjuredPlayer ALEX = new InjuredPlayer("Alex Example", "NYY", "SP", "Out", "Elbow",
            LocalDate.of(2026, 4, 12), "60-day IL, no timetable for return.");
    static final InjuredPlayer CASEY = new InjuredPlayer("Casey Sample", "BOS", "C", "Day-To-Day", "Hamstring",
            LocalDate.of(2026, 5, 3), "");
    static final InjuredPlayer QUOTED = new InjuredPlayer("Fallback Name", "NYY", "RF", "Injured", "",
            null, "Notes with, a comma and \"quotes\"");

    @Test
    void roundTripsRowsIncludingCommasQuotesAndMissingDates(@TempDir Path dir) throws IOException {
        InjuryHistory history = new InjuryHistory(dir.resolve("injuries.csv"));
        history.save(LocalDate.of(2026, 6, 1), List.of(ALEX, CASEY, QUOTED));

        List<InjuryHistory.Entry> entries = history.load();
        assertEquals(3, entries.size());
        assertEquals(LocalDate.of(2026, 6, 1), entries.get(0).snapshotDate());
        // Rows are sorted by team then name: BOS before NYY.
        assertEquals(CASEY, entries.get(0).player());
        assertEquals(ALEX, entries.get(1).player());
        assertEquals(QUOTED, entries.get(2).player());
    }

    @Test
    void savingTheSameDateTwiceReplacesInsteadOfDuplicating(@TempDir Path dir) throws IOException {
        InjuryHistory history = new InjuryHistory(dir.resolve("injuries.csv"));
        LocalDate day = LocalDate.of(2026, 6, 1);
        history.save(day, List.of(ALEX, CASEY));
        history.save(day, List.of(ALEX));
        history.save(day.plusDays(1), List.of(ALEX, QUOTED));

        List<InjuryHistory.Entry> entries = history.load();
        assertEquals(3, entries.size());
        assertEquals(1, entries.stream().filter(e -> e.snapshotDate().equals(day)).count());
        assertEquals(2, entries.stream().filter(e -> e.snapshotDate().equals(day.plusDays(1))).count());
    }

    @Test
    void writesAHeaderAndCreatesParentDirectories(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested").resolve("injuries.csv");
        new InjuryHistory(file).save(LocalDate.of(2026, 6, 1), List.of(ALEX));
        List<String> lines = Files.readAllLines(file);
        assertEquals(InjuryHistory.HEADER, lines.get(0));
        assertTrue(lines.get(1).startsWith("2026-06-01,Alex Example,NYY,SP,Out,Elbow,2026-04-12,"));
    }

    @Test
    void missingFileLoadsAsEmpty(@TempDir Path dir) throws IOException {
        assertTrue(new InjuryHistory(dir.resolve("none.csv")).load().isEmpty());
    }
}
