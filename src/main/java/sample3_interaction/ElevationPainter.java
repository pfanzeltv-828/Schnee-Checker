package sample3_interaction;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class ElevationPainter {
//hallo julian
    public double getElevation(double lat, double lon)  {
        try {
            String url = "https://api.open-elevation.com/api/v1/lookup?locations=" + lat + "," + lon;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "jxmapviewer-example/1.0");

            Scanner scanner = new Scanner(conn.getInputStream());
            String response = scanner.useDelimiter("\\A").next();

         // JSON parsen - einfach mit String-Suche
            int idx = response.indexOf("\"elevation\":");
            String elevStr = response.substring(idx + 12).split("[,}]")[0];
            return Double.parseDouble(elevStr.trim());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static double getElevation2(double lat, double lon) throws IOException {
        String urlStr = String.format(
                "https://api.open-meteo.com/v1/elevation?latitude=%s&longitude=%s",
                lat, lon
        );

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            // Response: {"elevation":[731.0]}
            String body = sb.toString();
            String value = body.replaceAll(".*\\[(.*?)\\].*", "$1");
            return Double.parseDouble(value.trim());
        }
    }
    public static void main(String[] args) throws IOException {
        ElevationPainter p1 = new ElevationPainter();
        System.out.println(p1.getElevation2(48.348760,11.987477));
    }
}
