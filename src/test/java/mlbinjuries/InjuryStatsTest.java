package mlbinjuries;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InjuryStatsTest {

    static final LocalDate D1 = LocalDate.of(2026, 6, 1);
    static final LocalDate D2 = LocalDate.of(2026, 6, 2);
    static final LocalDate D3 = LocalDate.of(2026, 6, 3);

    static InjuredPlayer player(String name, String team, String part) {
        return new InjuredPlayer(name, team, "SP", "Out", part, null, "");
    }

    static InjuryHistory.Entry on(LocalDate date, InjuredPlayer p) {
        return new InjuryHistory.Entry(date, p);
    }

    static final InjuredPlayer A = player("Alex Example", "NYY", "Elbow");
    static final InjuredPlayer B = player("Blake Fixture", "NYY", "Knee");
    static final InjuredPlayer C = player("Casey Sample", "BOS", "Hamstring");

    static final List<InjuryHistory.Entry> ENTRIES = List.of(
            on(D1, A), on(D1, B), on(D1, C),
            on(D2, A), on(D2, C),
            on(D3, A), on(D3, B));

    @Test
    void countsSnapshotsAndDateRange() {
        InjuryStats stats = new InjuryStats(ENTRIES);
        assertEquals(3, stats.snapshotCount());
        assertEquals(D1, stats.firstSnapshot());
        assertEquals(D3, stats.latestSnapshot());
    }

    @Test
    void playerSpellsCountDaysListedAndWhetherStillListed() {
        List<InjuryStats.PlayerSpell> spells = new InjuryStats(ENTRIES).playerSpells();
        assertEquals("Alex Example", spells.get(0).name());
        assertEquals(3, spells.get(0).daysListed());
        assertTrue(spells.get(0).stillListed());

        InjuryStats.PlayerSpell casey = spells.stream().filter(s -> s.name().equals("Casey Sample")).findFirst().get();
        assertEquals(2, casey.daysListed());
        assertEquals(D1, casey.firstSeen());
        assertEquals(D2, casey.lastSeen());
        assertFalse(casey.stillListed());
    }

    @Test
    void teamTotalsSumPlayerDays() {
        List<InjuryStats.TeamTotal> totals = new InjuryStats(ENTRIES).teamTotals();
        assertEquals("NYY", totals.get(0).team());
        assertEquals(5, totals.get(0).playerDays());
        assertEquals(2, totals.get(0).distinctPlayers());
        assertEquals(2, totals.get(0).currentlyListed());
        assertEquals("BOS", totals.get(1).team());
        assertEquals(2, totals.get(1).playerDays());
        assertEquals(0, totals.get(1).currentlyListed());
    }

    @Test
    void diffsAgainstThePreviousSnapshot() {
        InjuryStats stats = new InjuryStats(ENTRIES);
        assertEquals(List.of(C), stats.returnedSinceLastSnapshot());
        assertEquals(List.of(B), stats.newSinceLastSnapshot());
    }

    @Test
    void reportMentionsTheHeadlineNumbers() {
        String report = new InjuryStats(ENTRIES).report(10);
        assertTrue(report.contains("3 snapshots, 2026-06-01 to 2026-06-03"));
        assertTrue(report.contains("Alex Example"));
        assertTrue(report.contains("off the list: 1"));
        assertTrue(report.contains("newly listed: 1"));
    }

    @Test
    void emptyHistoryProducesAHint() {
        assertTrue(new InjuryStats(List.of()).report(10).contains("No snapshots"));
    }
}
