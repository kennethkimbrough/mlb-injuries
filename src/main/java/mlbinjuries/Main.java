package mlbinjuries;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Command-line entry point.
 *
 * <pre>
 *   fetch [--save] [--history FILE]   print today's MLB injured lists; --save records them to the history
 *   report [--top N] [--history FILE] summary statistics from the history
 * </pre>
 */
public final class Main {

    private static final Path DEFAULT_HISTORY = Path.of("data", "injuries.csv");

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String command = args.length == 0 ? "fetch" : args[0];
        Path history = DEFAULT_HISTORY;
        boolean save = false;
        int top = 10;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--save" -> save = true;
                case "--history" -> history = Path.of(requireValue(args, ++i, "--history"));
                case "--top" -> top = Integer.parseInt(requireValue(args, ++i, "--top"));
                default -> usage("Unknown option: " + args[i]);
            }
        }

        switch (command) {
            case "fetch" -> fetch(history, save);
            case "report" -> report(history, top);
            case "-h", "--help", "help" -> usage(null);
            default -> usage("Unknown command: " + command);
        }
    }

    private static void fetch(Path historyFile, boolean save) throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        List<InjuredPlayer> players = new MlbStatsClient().fetchInjuredPlayers(today, System.err::println);
        System.out.print(TablePrinter.format(players));
        if (save) {
            boolean written = new InjuryHistory(historyFile).save(today, players);
            if (written) {
                System.out.printf("Saved %d player%s for %s to %s%n", players.size(),
                        players.size() == 1 ? "" : "s", today, historyFile);
            } else {
                System.out.println("Nobody listed, same as the previous snapshot; history unchanged.");
            }
        }
    }

    private static void report(Path historyFile, int top) throws Exception {
        InjuryStats stats = new InjuryStats(new InjuryHistory(historyFile).load());
        System.out.print(stats.report(top));
    }

    private static String requireValue(String[] args, int i, String option) {
        if (i >= args.length) {
            usage(option + " needs a value");
        }
        return args[i];
    }

    private static void usage(String error) {
        if (error != null) {
            System.err.println(error);
        }
        System.err.println("""
                Usage:
                  mlb-injuries fetch [--save] [--history FILE]
                  mlb-injuries report [--top N] [--history FILE]
                The history file defaults to data/injuries.csv.""");
        System.exit(error == null ? 0 : 2);
    }
}
