package mlbinjuries;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Summary statistics over the daily snapshots in {@link InjuryHistory}.
 *
 * <p>"Days listed" counts the snapshots a player appeared in, so it only grows on days the feed
 * was actually fetched. With one snapshot per day that is days on the injury list.
 */
public final class InjuryStats {

    /** A player's run on the list across snapshots. */
    public record PlayerSpell(String name, String team, String position, String status, String bodyPart,
                              int daysListed, LocalDate firstSeen, LocalDate lastSeen, boolean stillListed) {
    }

    /** A team's totals across snapshots. */
    public record TeamTotal(String team, int playerDays, int distinctPlayers, int currentlyListed) {
    }

    private final List<InjuryHistory.Entry> entries;
    private final TreeSet<LocalDate> dates = new TreeSet<>();

    public InjuryStats(List<InjuryHistory.Entry> entries) {
        this.entries = List.copyOf(entries);
        for (InjuryHistory.Entry e : entries) {
            dates.add(e.snapshotDate());
        }
    }

    public int snapshotCount() {
        return dates.size();
    }

    public LocalDate firstSnapshot() {
        return dates.isEmpty() ? null : dates.first();
    }

    public LocalDate latestSnapshot() {
        return dates.isEmpty() ? null : dates.last();
    }

    /** One spell per player, longest first. */
    public List<PlayerSpell> playerSpells() {
        Map<String, List<InjuryHistory.Entry>> byPlayer = new LinkedHashMap<>();
        for (InjuryHistory.Entry e : entries) {
            byPlayer.computeIfAbsent(e.player().key(), k -> new ArrayList<>()).add(e);
        }
        LocalDate latest = latestSnapshot();
        List<PlayerSpell> spells = new ArrayList<>();
        for (List<InjuryHistory.Entry> es : byPlayer.values()) {
            TreeSet<LocalDate> seen = new TreeSet<>();
            InjuryHistory.Entry last = es.get(0);
            for (InjuryHistory.Entry e : es) {
                seen.add(e.snapshotDate());
                if (!e.snapshotDate().isBefore(last.snapshotDate())) {
                    last = e;
                }
            }
            InjuredPlayer p = last.player();
            spells.add(new PlayerSpell(p.name(), p.team(), p.position(), p.status(), p.bodyPart(),
                    seen.size(), seen.first(), seen.last(), seen.last().equals(latest)));
        }
        spells.sort(Comparator.comparingInt(PlayerSpell::daysListed).reversed()
                .thenComparing(PlayerSpell::team).thenComparing(PlayerSpell::name));
        return spells;
    }

    /** Team totals, most player-days first. */
    public List<TeamTotal> teamTotals() {
        Map<String, Integer> playerDays = new TreeMap<>();
        Map<String, Set<String>> players = new HashMap<>();
        Map<String, Integer> current = new HashMap<>();
        LocalDate latest = latestSnapshot();
        for (InjuryHistory.Entry e : entries) {
            String team = e.player().team();
            playerDays.merge(team, 1, Integer::sum);
            players.computeIfAbsent(team, k -> new TreeSet<>()).add(e.player().key());
            if (e.snapshotDate().equals(latest)) {
                current.merge(team, 1, Integer::sum);
            }
        }
        List<TeamTotal> totals = new ArrayList<>();
        for (Map.Entry<String, Integer> t : playerDays.entrySet()) {
            totals.add(new TeamTotal(t.getKey(), t.getValue(), players.get(t.getKey()).size(),
                    current.getOrDefault(t.getKey(), 0)));
        }
        totals.sort(Comparator.comparingInt(TeamTotal::playerDays).reversed().thenComparing(TeamTotal::team));
        return totals;
    }

    /** Players in the previous snapshot who are absent from the latest one. */
    public List<InjuredPlayer> returnedSinceLastSnapshot() {
        return difference(previousSnapshot(), latestSnapshot());
    }

    /** Players in the latest snapshot who were not in the previous one. */
    public List<InjuredPlayer> newSinceLastSnapshot() {
        return difference(latestSnapshot(), previousSnapshot());
    }

    private LocalDate previousSnapshot() {
        LocalDate latest = latestSnapshot();
        return latest == null ? null : dates.lower(latest);
    }

    private List<InjuredPlayer> difference(LocalDate inDate, LocalDate notInDate) {
        List<InjuredPlayer> out = new ArrayList<>();
        if (inDate == null || notInDate == null) {
            return out;
        }
        Set<String> exclude = new TreeSet<>();
        for (InjuryHistory.Entry e : entries) {
            if (e.snapshotDate().equals(notInDate)) {
                exclude.add(e.player().key());
            }
        }
        for (InjuryHistory.Entry e : entries) {
            if (e.snapshotDate().equals(inDate) && !exclude.contains(e.player().key())) {
                out.add(e.player());
            }
        }
        out.sort(Comparator.comparing(InjuredPlayer::team).thenComparing(InjuredPlayer::name));
        return out;
    }

    /** A plain-text report of everything above. */
    public String report(int topN) {
        StringBuilder sb = new StringBuilder();
        if (dates.isEmpty()) {
            return "No snapshots recorded yet. Run `fetch --save` first.\n";
        }
        sb.append(String.format("%d snapshot%s, %s to %s%n%n", dates.size(), dates.size() == 1 ? "" : "s",
                firstSnapshot(), latestSnapshot()));

        sb.append("Longest on the injury list\n");
        List<PlayerSpell> spells = playerSpells();
        for (PlayerSpell s : spells.subList(0, Math.min(topN, spells.size()))) {
            sb.append(String.format("  %3d day%s  %-24s %-4s %-6s %s%s%n", s.daysListed(),
                    s.daysListed() == 1 ? " " : "s", s.name(), s.team(), s.position(),
                    s.bodyPart().isEmpty() ? s.status() : s.bodyPart(),
                    s.stillListed() ? "" : "  (off the list since " + s.lastSeen() + ")"));
        }

        sb.append("\nTeams most affected (player-days listed)\n");
        List<TeamTotal> totals = teamTotals();
        for (TeamTotal t : totals.subList(0, Math.min(topN, totals.size()))) {
            sb.append(String.format("  %-4s %5d player-days, %2d player%s, %2d in the latest snapshot%n",
                    t.team(), t.playerDays(), t.distinctPlayers(), t.distinctPlayers() == 1 ? " " : "s",
                    t.currentlyListed()));
        }

        List<InjuredPlayer> returned = returnedSinceLastSnapshot();
        List<InjuredPlayer> added = newSinceLastSnapshot();
        if (dates.size() > 1) {
            sb.append(String.format("%nSince the previous snapshot (%s)%n", dates.lower(latestSnapshot())));
            sb.append(String.format("  off the list: %d%n", returned.size()));
            for (InjuredPlayer p : returned) {
                sb.append(String.format("    %-4s %s%n", p.team(), p.name()));
            }
            sb.append(String.format("  newly listed: %d%n", added.size()));
            for (InjuredPlayer p : added) {
                sb.append(String.format("    %-4s %s (%s)%n", p.team(), p.name(),
                        p.bodyPart().isEmpty() ? p.status() : p.bodyPart()));
            }
        }
        return sb.toString();
    }
}
