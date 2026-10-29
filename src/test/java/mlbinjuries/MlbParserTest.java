package mlbinjuries;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlbParserTest {

    @Test
    void teamsMapIdToAbbreviationAndSkipIncompleteEntries() throws IOException {
        Map<Integer, String> teams = MlbParser.parseTeams(Fixtures.read("teams.json"));
        assertEquals(Map.of(147, "NYY", 111, "BOS"), teams);
    }

    @Test
    void rosterKeepsOnlyInjuredListStatuses() throws IOException {
        List<InjuredPlayer> nyy = MlbParser.parseInjuredList(Fixtures.read("roster-147-40Man.json"), "NYY");
        assertEquals(2, nyy.size());

        InjuredPlayer casey = nyy.get(0);
        assertEquals(900002L, casey.playerId());
        assertEquals("Casey Sample", casey.name());
        assertEquals("NYY", casey.team());
        assertEquals("C", casey.position());
        assertEquals("10-day IL", casey.status());
        assertEquals("", casey.injury());
        assertNull(casey.startDate());

        assertEquals("Left oblique strain", nyy.get(1).injury(), "a roster note becomes the injury");
    }

    @Test
    void tradedAndOptionedPlayersAreNotInjured() throws IOException {
        assertTrue(MlbParser.parseInjuredList(Fixtures.read("roster-147-fullSeason.json"), "NYY").stream()
                .noneMatch(p -> p.name().equals("Traded Away")));
        assertEquals(List.of("Blake Fixture"),
                MlbParser.parseInjuredList(Fixtures.read("roster-111-40Man.json"), "BOS").stream()
                        .map(InjuredPlayer::name).toList());
    }

    @Test
    void statusCodes() {
        assertTrue(MlbParser.isInjured("D60", "Injured 60-Day"));
        assertTrue(MlbParser.isInjured("D7", ""));
        assertTrue(MlbParser.isInjured("", "Injured 10-Day"));
        assertFalse(MlbParser.isInjured("A", "Active"));
        assertFalse(MlbParser.isInjured("DES", "Designated for Assignment"));
        assertEquals("60-day IL", MlbParser.statusLabel("D60", "Injured 60-Day"));
        assertEquals("Injured - other", MlbParser.statusLabel("ILF", "Injured - other"));
    }

    @Test
    void extractsTheInjuryAfterTheInjuredListClause() {
        assertEquals("Right elbow inflammation", MlbParser.extractInjury(
                "Team placed RHP A on the 15-day injured list retroactive to April 9, 2026. Right elbow inflammation."));
        assertEquals("Right elbow surgery", MlbParser.extractInjury(
                "Team transferred RHP A from the 15-day injured list to the 60-day injured list. Right elbow surgery."));
        assertEquals("right wrist sprain", MlbParser.extractInjury(
                "Team placed LF B on the 15-day injured list (right wrist sprain)."));
        assertEquals("", MlbParser.extractInjury("Team placed C on the 10-day injured list."));
        assertEquals("", MlbParser.extractInjury("Team traded RHP D to Other Team."));
    }

    @Test
    void transactionsGiveTheCurrentSpellPerPlayer() throws IOException {
        Map<Long, MlbParser.IlTransaction> moves = MlbParser.parseIlTransactions(Fixtures.read("transactions.json"));

        MlbParser.IlTransaction alex = moves.get(900004L);
        assertEquals(LocalDate.of(2026, 4, 12), alex.placed(), "a transfer does not restart the spell");
        assertEquals("Right elbow surgery", alex.injury(), "the latest injury text wins");
        assertTrue(alex.description().contains("to the 60-day injured list"));

        MlbParser.IlTransaction casey = moves.get(900002L);
        assertEquals(LocalDate.of(2026, 9, 2), casey.placed(), "a new placement starts a new spell");
        assertEquals("", casey.injury(), "the earlier spell's injury is not carried over");

        assertEquals("right wrist sprain", moves.get(900006L).injury());
        assertFalse(moves.containsKey(900005L), "trades are ignored");
    }

    @Test
    void enrichFillsDatesAndFallsBackToTheRosterNote() {
        InjuredPlayer fromRoster = new InjuredPlayer(1, "A", "NYY", "P", "15-day IL", "Note text", null, "");
        assertEquals(fromRoster, MlbParser.enrich(fromRoster, null));

        InjuredPlayer enriched = MlbParser.enrich(fromRoster,
                new MlbParser.IlTransaction(LocalDate.of(2026, 6, 1), "", "Team placed P A on the 15-day injured list."));
        assertEquals(LocalDate.of(2026, 6, 1), enriched.startDate());
        assertEquals("Note text", enriched.injury());
        assertEquals("Team placed P A on the 15-day injured list.", enriched.notes());
    }

    @Test
    void rejectsNonObjectPayloads() {
        assertThrows(IOException.class, () -> MlbParser.parseTeams("[]"));
    }
}
