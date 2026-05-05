package sample3_interaction;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class ElevationPainter {

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
    public static void main(String[] args){
        ElevationPainter p1 = new ElevationPainter();
        System.out.println(p1.getElevation(47.4138,10.9754));
    }
}
