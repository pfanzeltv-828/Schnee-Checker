package schnee.weather;

import java.net.URI;
import java.net.http.*;

public class WeatherService {
    String url = "https://api.open-meteo.com/v1/forecast?latitude=47.42&longitude=10.98&hourly=freezing_level_height";

    public static void main(String[] args) {
        WeatherService weatherService = new WeatherService();
        weatherService.getFreezingLevel(1,1);
    }

    public double getFreezingLevel(double lat, double lon) {
        String jsonRohdaten = null;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Statuscode 200 bedeutet "OK" (Erfolgreich verbunden)

            if (response.statusCode() == 200) {
                jsonRohdaten = response.body();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println(jsonRohdaten);
        return 0;
    }
}
