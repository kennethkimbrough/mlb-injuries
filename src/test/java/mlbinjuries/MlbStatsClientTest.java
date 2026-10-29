package mlbinjuries;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlbStatsClientTest {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
    static final String TRANSACTIONS = "/transactions?sportId=1&startDate=2025-09-10&endDate=2026-09-10";

    @Test
    void mergesBothRosterTypesForEveryTeamAndAddsTransactionDetails() throws Exception {
        Fixtures.FakeApi api = new Fixtures.FakeApi().withTransactions(TRANSACTIONS);
        List<String> warnings = new ArrayList<>();

        List<InjuredPlayer> players = new MlbStatsClient(api).fetchInjuredPlayers(TODAY, warnings::add);

        assertEquals(List.of("Casey Sample", "Note Only", "Alex Example", "Blake Fixture"),
                players.stream().map(InjuredPlayer::name).toList(),
                "the D60 player only on the full-season roster is included, the duplicate is not");
        assertTrue(warnings.isEmpty());

        InjuredPlayer alex = players.get(2);
        assertEquals("NYY", alex.team());
        assertEquals("60-day IL", alex.status());
        assertEquals("Right elbow surgery", alex.injury());
        assertEquals(LocalDate.of(2026, 4, 12), alex.startDate());

        InjuredPlayer noteOnly = players.get(1);
        assertEquals("Left oblique strain", noteOnly.injury());
        assertNull(noteOnly.startDate());

        assertEquals("BOS", players.get(3).team());
        assertEquals(1 + 2 * 2 + 1, api.requested.size());
        assertEquals(TRANSACTIONS, api.requested.get(api.requested.size() - 1));
    }

    @Test
    void missingTransactionsOnlyLoseTheDetails() throws Exception {
        Fixtures.FakeApi api = new Fixtures.FakeApi();
        List<String> warnings = new ArrayList<>();

        List<InjuredPlayer> players = new MlbStatsClient(api).fetchInjuredPlayers(TODAY, warnings::add);

        assertEquals(4, players.size());
        assertNull(players.get(2).startDate());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith("Could not load transactions"));
    }

    @Test
    void aFailedRosterFailsTheWholeFetch() throws Exception {
        Fixtures.FakeApi api = new Fixtures.FakeApi().withTransactions(TRANSACTIONS);
        api.responses.remove("/teams/111/roster?rosterType=40Man");
        assertThrows(IOException.class, () -> new MlbStatsClient(api).fetchInjuredPlayers(TODAY, w -> { }));
    }

    @Test
    void noTeamsIsAnError() throws Exception {
        Fixtures.FakeApi api = new Fixtures.FakeApi();
        api.responses.put("/teams?sportId=1", "{\"teams\": []}");
        assertThrows(IOException.class, () -> new MlbStatsClient(api).fetchInjuredPlayers(TODAY, w -> { }));
    }
}
