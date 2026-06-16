import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WetterApp {

    public static void main(String[] args) {
        // Die Open-Meteo URL (Beispiel für die Zugspitze)
        String url = "https://api.open-meteo.com/v1/forecast?latitude=47.42&longitude=10.98&hourly=freezing_level_height";

        // 1. HTTP-Client erstellen (Das ist wie dein eingebauter Mini-Browser in Java)
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            System.out.println("Lade Daten von Open-Meteo herunter...");

            // 2. Daten von der Webseite abrufen
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Statuscode 200 bedeutet "OK" (Erfolgreich verbunden)
            if (response.statusCode() == 200) {
                String jsonRohdaten = response.body();

                // 3. JSON-Daten parsen (Extrahieren) mit Hilfe von GSON
                JsonObject jsonObjekt = JsonParser.parseString(jsonRohdaten).getAsJsonObject();
                JsonObject hourly = jsonObjekt.getAsJsonObject("hourly");

                JsonArray zeiten = hourly.getAsJsonArray("time");
                JsonArray nullgradGrenzen = hourly.getAsJsonArray("freezing_level_height");

                System.out.println("\n--- Wetterdaten erfolgreich geladen ---");

                // Wir geben die Vorhersage für die nächsten 10 Stunden aus
                for (int i = 0; i < 10; i++) {
                    String zeitRaw = zeiten.get(i).getAsString(); // z.B. "2026-03-15T12:00"
                    double hoehe = nullgradGrenzen.get(i).getAsDouble(); // z.B. 1250.0

                    // Formatierung der Zeit, damit es schöner aussieht
                    LocalDateTime dateTime = LocalDateTime.parse(zeitRaw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    String formatierteZeit = dateTime.format(DateTimeFormatter.ofPattern("dd.MM.  HH:mm"));

                    System.out.println("Datum/Uhrzeit: " + formatierteZeit + " Uhr | Schneefall-/Nullgradgrenze: " + hoehe + " Meter");
                }

            } else {
                System.out.println("Fehler! Server meldet Statuscode: " + response.statusCode());
            }

        } catch (Exception e) {
            System.out.println("Es gab einen Fehler bei der Verbindung:");
            e.printStackTrace();
        }
    }
}