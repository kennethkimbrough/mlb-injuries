package mlbinjuries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A CSV file of daily snapshots: one row per injured player per day the list was fetched.
 *
 * <p>Columns: {@code snapshot_date,player_id,name,team,position,status,injury,start_date,notes}.
 *
 * <ul>
 *   <li>Saving a date that already has rows replaces them, so re-running on the same day never double-counts.</li>
 *   <li>A day with nobody listed (the injured lists are not used between the World Series and spring training)
 *       is stored as a row holding only the date, so the history knows everyone came off. Consecutive empty
 *       days after that add nothing.</li>
 * </ul>
 */
public final class InjuryHistory {

    public static final String HEADER = "snapshot_date,player_id,name,team,position,status,injury,start_date,notes";
    private static final int COLUMNS = 9;

    /** One player row. */
    public record Entry(LocalDate snapshotDate, InjuredPlayer player) {
    }

    /** Everything in the file: every snapshot date (including empty ones) and the player rows. */
    public record Contents(SortedSet<LocalDate> snapshotDates, List<Entry> entries) {
    }

    private final Path file;

    public InjuryHistory(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public Contents load() throws IOException {
        SortedSet<LocalDate> dates = new TreeSet<>();
        List<Entry> entries = new ArrayList<>();
        if (!Files.exists(file)) {
            return new Contents(dates, entries);
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            List<String> f = Csv.split(line);
            if (f.size() < COLUMNS) {
                throw new IOException("Malformed history row " + (i + 1) + ": " + line);
            }
            LocalDate date = LocalDate.parse(f.get(0));
            dates.add(date);
            if (f.get(2).isEmpty()) {
                continue; // nobody listed that day
            }
            long id = f.get(1).isEmpty() ? 0 : Long.parseLong(f.get(1));
            LocalDate start = f.get(7).isEmpty() ? null : LocalDate.parse(f.get(7));
            entries.add(new Entry(date,
                    new InjuredPlayer(id, f.get(2), f.get(3), f.get(4), f.get(5), f.get(6), start, f.get(8))));
        }
        return new Contents(dates, entries);
    }

    /**
     * Records the list for a date, replacing anything already stored for it.
     *
     * @return false if nothing was written because the list is empty and the previous snapshot was empty too
     */
    public boolean save(LocalDate snapshotDate, List<InjuredPlayer> players) throws IOException {
        Contents current = load();
        List<Entry> kept = new ArrayList<>();
        SortedSet<LocalDate> emptyDates = new TreeSet<>(current.snapshotDates());
        for (Entry e : current.entries()) {
            emptyDates.remove(e.snapshotDate());
            if (!e.snapshotDate().equals(snapshotDate)) {
                kept.add(e);
            }
        }
        emptyDates.remove(snapshotDate);

        if (players.isEmpty()) {
            SortedSet<LocalDate> earlier = new TreeSet<>(current.snapshotDates()).headSet(snapshotDate);
            boolean previousWasEmpty = !earlier.isEmpty() && emptyDates.contains(earlier.last());
            if (previousWasEmpty) {
                return false;
            }
            emptyDates.add(snapshotDate);
        } else {
            for (InjuredPlayer p : players) {
                kept.add(new Entry(snapshotDate, p));
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Entry e : kept) {
            rows.add(new Row(e.snapshotDate(), e.player()));
        }
        for (LocalDate d : emptyDates) {
            rows.add(new Row(d, null));
        }
        rows.sort((a, b) -> {
            int c = a.date().compareTo(b.date());
            if (c != 0 || a.player() == null || b.player() == null) {
                return c;
            }
            c = a.player().team().compareTo(b.player().team());
            return c != 0 ? c : a.player().name().compareTo(b.player().name());
        });

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Row r : rows) {
            lines.add(r.toCsv());
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        return true;
    }

    private record Row(LocalDate date, InjuredPlayer player) {
        String toCsv() {
            if (player == null) {
                return date + ",".repeat(COLUMNS - 1);
            }
            return Csv.join(List.of(
                    date.toString(),
                    player.playerId() > 0 ? Long.toString(player.playerId()) : "",
                    player.name(), player.team(), player.position(), player.status(), player.injury(),
                    player.startDate() == null ? "" : player.startDate().toString(),
                    player.notes()));
        }
    }
}
