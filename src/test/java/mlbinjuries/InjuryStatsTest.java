package mlbinjuries;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InjuryStatsTest {

    static final LocalDate D1 = LocalDate.of(2026, 6, 1);
    static final LocalDate D2 = LocalDate.of(2026, 6, 2);
    static final LocalDate D3 = LocalDate.of(2026, 6, 3);
    static final LocalDate D4 = LocalDate.of(2026, 6, 4);

    static InjuredPlayer player(long id, String name, String team, String injury) {
        return new InjuredPlayer(id, name, team, "SP", "15-day IL", injury, null, "");
    }

    static InjuryHistory.Entry on(LocalDate date, InjuredPlayer p) {
        return new InjuryHistory.Entry(date, p);
    }

    static final InjuredPlayer A = player(1, "Alex Example", "NYY", "Elbow inflammation");
    static final InjuredPlayer B = player(2, "Blake Fixture", "NYY", "Knee sprain");
    static final InjuredPlayer C = player(3, "Casey Sample", "BOS", "Hamstring strain");

    static final List<InjuryHistory.Entry> ENTRIES = List.of(
            on(D1, A), on(D1, B), on(D1, C),
            on(D2, A), on(D2, C),
            on(D3, A), on(D3, B));

    static InjuryStats stats(List<InjuryHistory.Entry> entries, LocalDate... extraDates) {
        TreeSet<LocalDate> dates = new TreeSet<>(List.of(extraDates));
        entries.forEach(e -> dates.add(e.snapshotDate()));
        return new InjuryStats(new InjuryHistory.Contents(dates, entries));
    }

    @Test
    void countsSnapshotsAndDateRange() {
        InjuryStats stats = stats(ENTRIES);
        assertEquals(3, stats.snapshotCount());
        assertEquals(D1, stats.firstSnapshot());
        assertEquals(D3, stats.latestSnapshot());
    }

    @Test
    void playerSpellsCountDaysListedAndWhetherStillListed() {
        List<InjuryStats.PlayerSpell> spells = stats(ENTRIES).playerSpells();
        assertEquals("Alex Example", spells.get(0).name());
        assertEquals(3, spells.get(0).daysListed());
        assertTrue(spells.get(0).stillListed());

        InjuryStats.PlayerSpell casey = spells.stream().filter(s -> s.name().equals("Casey Sample")).findFirst().get();
        assertEquals(2, casey.daysListed());
        assertEquals(D2, casey.lastSeen());
        assertFalse(casey.stillListed());
    }

    @Test
    void aTradedPlayerIsStillOnePlayer() {
        InjuredPlayer alexTraded = new InjuredPlayer(1, "Alex Example", "BOS", "SP", "60-day IL", "", null, "");
        List<InjuryStats.PlayerSpell> spells = stats(List.of(on(D1, A), on(D2, alexTraded))).playerSpells();
        assertEquals(1, spells.size());
        assertEquals(2, spells.get(0).daysListed());
        assertEquals("BOS", spells.get(0).team(), "the latest appearance wins");
    }

    @Test
    void teamTotalsSumPlayerDays() {
        List<InjuryStats.TeamTotal> totals = stats(ENTRIES).teamTotals();
        assertEquals("NYY", totals.get(0).team());
        assertEquals(5, totals.get(0).playerDays());
        assertEquals(2, totals.get(0).distinctPlayers());
        assertEquals(2, totals.get(0).currentlyListed());
        assertEquals("BOS", totals.get(1).team());
        assertEquals(0, totals.get(1).currentlyListed());
    }

    @Test
    void diffsAgainstThePreviousSnapshot() {
        InjuryStats stats = stats(ENTRIES);
        assertEquals(List.of(C), stats.returnedSinceLastSnapshot());
        assertEquals(List.of(B), stats.newSinceLastSnapshot());
    }

    @Test
    void anEmptyLatestSnapshotMeansEveryoneCameOff() {
        InjuryStats stats = stats(ENTRIES, D4);
        assertEquals(D4, stats.latestSnapshot());
        assertTrue(stats.playerSpells().stream().noneMatch(InjuryStats.PlayerSpell::stillListed));
        assertEquals(List.of(A, B).size(), stats.returnedSinceLastSnapshot().size());
        assertTrue(stats.report(10).contains("Nobody was on an injured list in the latest snapshot"));
    }

    @Test
    void reportMentionsTheHeadlineNumbers() {
        String report = stats(ENTRIES).report(10);
        assertTrue(report.contains("3 snapshots, 2026-06-01 to 2026-06-03"));
        assertTrue(report.contains("Alex Example"));
        assertTrue(report.contains("Elbow inflammation"));
        assertTrue(report.contains("off the list: 1"));
        assertTrue(report.contains("newly listed: 1"));
        assertFalse(report.contains("Nobody was on"));
    }

    @Test
    void emptyHistoryProducesAHint() {
        assertTrue(stats(List.of()).report(10).contains("No snapshots"));
    }
}
