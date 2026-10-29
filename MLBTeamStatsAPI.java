package baseballModel;

import java.io.IOException;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MLBTeamStatsAPI {
    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws IOException {
        String apiKey = System.getenv("SPORTSDATA_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Set the SPORTSDATA_API_KEY environment variable to your SportsData.io API key.");
            System.exit(1);
        }
        String url = "https://api.sportsdata.io/v3/mlb/projections/json/InjuredPlayers?key=" + apiKey;

        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Authorization", "Bearer " + apiKey);

        CloseableHttpResponse response = client.execute(httpGet);
        HttpEntity entity = response.getEntity();
        String responseBody = null;
        try {
            try {
                responseBody = EntityUtils.toString(entity);
            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.err.println("Error reading entity: " + e.getMessage());
            throw new RuntimeException("Error reading entity: " + e.getMessage());
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json;
        try {
            json = mapper.readTree(responseBody);
            // rest of your code
        } catch (JsonParseException e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
            throw new RuntimeException("Error parsing JSON: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error reading JSON tree: " + e.getMessage());
            throw new RuntimeException("Error reading JSON tree: " + e.getMessage());
        }

        json.forEach(player -> {
            JsonNode nameNode = player.get("FantasyDraftName");
            JsonNode teamNode = player.get("Team");
            JsonNode positionNode = player.get("Position");
            JsonNode injuryStatusNode = player.get("Status");
            String nameText = nameNode.asText();
            String teamText = teamNode.asText();
            String positionText = positionNode.asText();
            String injuryText = injuryStatusNode.asText();

            if (nameText != "null" && teamText != "null" && positionText != "null" && injuryText != "null") {
                String playerName = nameNode.asText();
                String team = teamNode.asText();
                String position = positionNode.asText();
                String injuryStatus = injuryStatusNode.asText();

                System.out.println("Player: " + playerName + ", Team: " + team + ", Position: " + position + ", Injury Status: " + injuryStatus);
            } else {
                
            }
        });
    }
}
