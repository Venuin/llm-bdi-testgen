package tools;

import cartago.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

@ARTIFACT_INFO(outports = { @OUTPORT(name = "out-1") })
public class LLMTool extends Artifact {

    // API key is read at runtime from the OPENAI_API_KEY environment variable or system property.
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    @OPERATION
    public void askChatGPT(String prompt, OpFeedbackParam<String> responseParam) {
        try {
            // Check system property first (set via -DOPENAI_API_KEY=...), then env var
            String apiKey = System.getProperty("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = System.getenv("OPENAI_API_KEY");
            }
            if (apiKey == null || apiKey.isBlank()) {
                responseParam.set("ERROR: OPENAI_API_KEY is not set. Use env var or -DOPENAI_API_KEY=... JVM arg.");
                return;
            }
            
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", prompt);

            JsonArray messages = new JsonArray();
            messages.add(userMessage);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "gpt-4o-mini");
            requestBody.add("messages", messages);
            requestBody.addProperty("temperature", 0.7);

            String jsonBody = requestBody.toString();
            // --------------------------

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray choices = jsonResponse.getAsJsonArray("choices");
                String content = choices.get(0).getAsJsonObject()
                                        .getAsJsonObject("message")
                                        .get("content").getAsString();
                
                responseParam.set(content);
            } else {
                // Hata durumunda loga detaylı bilgi bas
                System.out.println("API Hatası: " + response.body());
                responseParam.set("Hata Kodu: " + response.statusCode());
                failed("API Hatasi: " + response.statusCode());
            }

        } catch (Exception e) {
            e.printStackTrace();
            failed("Bağlantı Hatası: " + e.toString()); // e.getMessage() bazen null olabilir, e.toString() daha güvenlidir.
        }
    }
}