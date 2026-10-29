package mlbinjuries;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TablePrinterTest {

    @Test
    void printsAnAlignedTableGroupedByTeam() {
        List<InjuredPlayer> players = List.of(
                new InjuredPlayer(1, "Zed Last", "NYY", "P", "60-day IL", "Right elbow surgery",
                        LocalDate.of(2026, 4, 12), ""),
                new InjuredPlayer(2, "Casey Sample", "BOS", "C", "10-day IL", "", null, ""));

        String[] lines = TablePrinter.format(players).split("\n");

        assertEquals("Team  Player        Pos  List       Injury               Placed", lines[0]);
        assertTrue(lines[1].matches("-+( {2}-+)+"), "a dashed rule under the header");
        assertEquals("BOS   Casey Sample  C    10-day IL", lines[2]);
        assertEquals("NYY   Zed Last      P    60-day IL  Right elbow surgery  2026-04-12", lines[3]);
        assertEquals("2 players listed", lines[4]);
    }

    @Test
    void longInjuriesAreShortened() {
        String injury = "Right elbow ulnar collateral ligament reconstruction surgery";
        assertEquals(40, TablePrinter.shorten(injury, 40).length());
        assertTrue(TablePrinter.shorten(injury, 40).endsWith("…"));
        assertEquals("Short", TablePrinter.shorten("Short", 40));
    }

    @Test
    void emptyListAndSingularCount() {
        assertTrue(TablePrinter.format(List.of()).endsWith("0 players listed\n"));
        assertTrue(TablePrinter.format(List.of(
                new InjuredPlayer(3, "Only One", "SEA", "P", "15-day IL", "Shoulder", null, "")))
                .endsWith("1 player listed\n"));
    }
}
