package mlbinjuries;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds today's injured list from MLB's public Stats API. No key or account is needed.
 *
 * <p>One fetch makes 1 + 30 x 2 + 1 requests: the team list, each team's 40-man and full-season rosters (players
 * on the 60-day IL come off the 40-man roster, so both are read and merged by player id), and one year of
 * transactions for placement dates and injury descriptions.
 */
public final class MlbStatsClient {

    public static final String BASE_URL = "https://statsapi.mlb.com/api/v1";
    static final List<String> ROSTER_TYPES = List.of("40Man", "fullSeason");

    /** Fetches a path such as {@code /teams?sportId=1} and returns the response body. */
    @FunctionalInterface
    interface JsonSource {
        String get(String pathAndQuery) throws IOException, InterruptedException;
    }

    private final JsonSource source;

    public MlbStatsClient() {
        this(httpSource(BASE_URL, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()));
    }

    MlbStatsClient(JsonSource source) {
        this.source = source;
    }

    /**
     * Everyone currently on an MLB injured list. A failed team or roster request fails the whole fetch, so a
     * partial list is never recorded as if it were complete; a failed transactions request only loses the
     * placement dates and injury text, and is reported through {@code warn}.
     */
    public List<InjuredPlayer> fetchInjuredPlayers(LocalDate today, Consumer<String> warn)
            throws IOException, InterruptedException {
        Map<Integer, String> teams = MlbParser.parseTeams(source.get("/teams?sportId=1"));
        if (teams.isEmpty()) {
            throw new IOException("The MLB Stats API returned no teams");
        }

        Map<String, InjuredPlayer> byKey = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> team : teams.entrySet()) {
            for (String rosterType : ROSTER_TYPES) {
                String json = source.get("/teams/" + team.getKey() + "/roster?rosterType=" + rosterType);
                for (InjuredPlayer p : MlbParser.parseInjuredList(json, team.getValue())) {
                    byKey.putIfAbsent(p.key(), p);
                }
            }
        }

        Map<Long, MlbParser.IlTransaction> moves = Map.of();
        try {
            moves = MlbParser.parseIlTransactions(source.get("/transactions?sportId=1&startDate="
                    + today.minusDays(365) + "&endDate=" + today));
        } catch (IOException e) {
            warn.accept("Could not load transactions (" + e.getMessage()
                    + "); placement dates and injury details will be blank.");
        }

        List<InjuredPlayer> players = new ArrayList<>();
        for (InjuredPlayer p : byKey.values()) {
            players.add(MlbParser.enrich(p, moves.get(p.playerId())));
        }
        return players;
    }

    static JsonSource httpSource(String baseUrl, HttpClient http) {
        return pathAndQuery -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                    .header("Accept", "application/json")
                    .header("User-Agent", "mlb-injuries (https://github.com/kennethkimbrough/mlb-injuries)")
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("MLB Stats API returned HTTP " + response.statusCode() + " for " + pathAndQuery);
            }
            return response.body();
        };
    }
}
