package mlbinjuries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A CSV file of daily snapshots: one row per injured player per day the feed was fetched.
 *
 * <p>Columns: {@code snapshot_date,name,team,position,status,body_part,start_date,notes}.
 * Saving a snapshot for a date that already has rows replaces those rows, so re-running on
 * the same day never double-counts.
 */
public final class InjuryHistory {

    public static final String HEADER = "snapshot_date,name,team,position,status,body_part,start_date,notes";

    /** One row of the history file. */
    public record Entry(LocalDate snapshotDate, InjuredPlayer player) {
    }

    private final Path file;

    public InjuryHistory(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public List<Entry> load() throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        List<Entry> entries = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            List<String> f = Csv.split(line);
            if (f.size() < 8) {
                throw new IOException("Malformed history row " + (i + 1) + ": " + line);
            }
            LocalDate start = f.get(6).isEmpty() ? null : LocalDate.parse(f.get(6));
            entries.add(new Entry(LocalDate.parse(f.get(0)),
                    new InjuredPlayer(f.get(1), f.get(2), f.get(3), f.get(4), f.get(5), start, f.get(7))));
        }
        return entries;
    }

    /** Records today's list, replacing any rows already stored for that date. Returns the rows written for the date. */
    public int save(LocalDate snapshotDate, List<InjuredPlayer> players) throws IOException {
        List<Entry> kept = new ArrayList<>();
        for (Entry e : load()) {
            if (!e.snapshotDate().equals(snapshotDate)) {
                kept.add(e);
            }
        }
        for (InjuredPlayer p : players) {
            kept.add(new Entry(snapshotDate, p));
        }
        kept.sort((a, b) -> {
            int c = a.snapshotDate().compareTo(b.snapshotDate());
            if (c != 0) {
                return c;
            }
            c = a.player().team().compareTo(b.player().team());
            return c != 0 ? c : a.player().name().compareTo(b.player().name());
        });

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Entry e : kept) {
            InjuredPlayer p = e.player();
            lines.add(Csv.join(List.of(
                    e.snapshotDate().toString(), p.name(), p.team(), p.position(), p.status(), p.bodyPart(),
                    p.startDate() == null ? "" : p.startDate().toString(), p.notes())));
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        return players.size();
    }
}
