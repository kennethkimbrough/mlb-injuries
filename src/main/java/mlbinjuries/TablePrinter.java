package mlbinjuries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Renders a list of injured players as an aligned text table, grouped by team. */
public final class TablePrinter {

    private TablePrinter() {
    }

    public static String format(List<InjuredPlayer> players) {
        List<InjuredPlayer> sorted = new ArrayList<>(players);
        sorted.sort(Comparator.comparing(InjuredPlayer::team).thenComparing(InjuredPlayer::name));

        String[] headers = {"Team", "Player", "Pos", "Status", "Body part", "Since"};
        List<String[]> rows = new ArrayList<>();
        for (InjuredPlayer p : sorted) {
            rows.add(new String[] {
                    p.team(), p.name(), p.position(), p.status(), p.bodyPart(),
                    p.startDate() == null ? "" : p.startDate().toString()});
        }

        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        StringBuilder out = new StringBuilder();
        appendRow(out, headers, widths);
        for (int i = 0; i < headers.length; i++) {
            out.append("-".repeat(widths[i]));
            out.append(i < headers.length - 1 ? "  " : "\n");
        }
        for (String[] row : rows) {
            appendRow(out, row, widths);
        }
        out.append(rows.size()).append(rows.size() == 1 ? " player" : " players").append(" listed\n");
        return out.toString();
    }

    private static void appendRow(StringBuilder out, String[] cells, int[] widths) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            row.append(cells[i]);
            if (i < cells.length - 1) {
                row.append(" ".repeat(widths[i] - cells[i].length() + 2));
            }
        }
        out.append(row.toString().stripTrailing()).append('\n');
    }
}
