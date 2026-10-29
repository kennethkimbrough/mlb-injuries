package mlbinjuries;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the API-shaped sample responses under src/test/resources/mlb and serves them by request path. */
final class Fixtures {

    private Fixtures() {
    }

    static String read(String name) throws IOException {
        try (InputStream in = Fixtures.class.getResourceAsStream("/mlb/" + name)) {
            if (in == null) {
                throw new IOException("Missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A fake API: known paths return fixtures, everything requested is recorded, unknown paths fail. */
    static final class FakeApi implements MlbStatsClient.JsonSource {
        final Map<String, String> responses = new HashMap<>();
        final List<String> requested = new ArrayList<>();

        FakeApi() throws IOException {
            responses.put("/teams?sportId=1", read("teams.json"));
            for (int team : new int[] {147, 111}) {
                for (String type : MlbStatsClient.ROSTER_TYPES) {
                    responses.put("/teams/" + team + "/roster?rosterType=" + type,
                            read("roster-" + team + "-" + type + ".json"));
                }
            }
        }

        FakeApi withTransactions(String path) throws IOException {
            responses.put(path, read("transactions.json"));
            return this;
        }

        @Override
        public String get(String pathAndQuery) throws IOException {
            requested.add(pathAndQuery);
            String body = responses.get(pathAndQuery);
            if (body == null) {
                throw new IOException("HTTP 404 for " + pathAndQuery);
            }
            return body;
        }
    }
}
