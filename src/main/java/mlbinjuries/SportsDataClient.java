package mlbinjuries;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Fetches the injured-players feed from SportsData.io. */
public final class SportsDataClient {

    public static final String DEFAULT_URL =
            "https://api.sportsdata.io/v3/mlb/projections/json/InjuredPlayers";

    private final HttpClient http;
    private final String url;
    private final String apiKey;

    public SportsDataClient(String apiKey) {
        this(apiKey, DEFAULT_URL, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    SportsDataClient(String apiKey, String url, HttpClient http) {
        this.apiKey = apiKey;
        this.url = url;
        this.http = http;
    }

    /** Downloads the feed and parses it. The key is sent as a header, so it never appears in a URL or log line. */
    public List<InjuredPlayer> fetchInjuredPlayers() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("SportsData.io returned HTTP " + response.statusCode()
                    + (response.statusCode() == 401 ? " (check SPORTSDATA_API_KEY)" : ""));
        }
        return InjuryParser.parse(response.body());
    }
}
