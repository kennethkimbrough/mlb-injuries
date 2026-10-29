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
                new InjuredPlayer("Zed Last", "NYY", "SP", "Out", "Elbow", LocalDate.of(2026, 4, 12), ""),
                new InjuredPlayer("Casey Sample", "BOS", "C", "Day-To-Day", "Hamstring", null, ""));

        String[] lines = TablePrinter.format(players).split("\n");

        assertEquals("Team  Player        Pos  Status      Body part  Since", lines[0]);
        assertTrue(lines[1].matches("-+( {2}-+)+"), "a dashed rule under the header");
        assertEquals("BOS   Casey Sample  C    Day-To-Day  Hamstring", lines[2]);
        assertEquals("NYY   Zed Last      SP   Out         Elbow      2026-04-12", lines[3]);
        assertEquals("2 players listed", lines[4]);
    }

    @Test
    void singularCount() {
        String out = TablePrinter.format(List.of(
                new InjuredPlayer("Only One", "SEA", "RP", "Out", "Shoulder", null, "")));
        assertTrue(out.endsWith("1 player listed\n"));
    }
}
