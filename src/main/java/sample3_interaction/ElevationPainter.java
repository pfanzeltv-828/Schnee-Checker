package sample3_interaction;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ElevationPainter implements Painter<JXMapViewer> {

    private final Map<String, Double> elevationCache = new ConcurrentHashMap<>();
    private final Set<String> pendingRequests = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    @Override
    public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
        System.out.println("Paint wird aufgerufen! Cache size: " + elevationCache.size());
        Rectangle bounds = map.getViewportBounds();
        int step = 100;

        for (int px = 0; px < w; px += step) {
            for (int py = 0; py < h; py += step) {

                Point2D worldPt = new Point2D.Double(
                        px + bounds.getX(),
                        py + bounds.getY()
                );
                GeoPosition geo = map.getTileFactory()
                        .pixelToGeo(worldPt, map.getZoom());

                String key = String.format("%.1f,%.1f",
                        geo.getLatitude(), geo.getLongitude());

                if (elevationCache.containsKey(key)) {
                    System.out.println("Cache hit: " + elevationCache.get(key)); // ← hinzufügen
                    if (elevationCache.get(key) > 500) {
                        System.out.println("Zeichne rot!"); // ← hinzufügen
                        g.setColor(new Color(255, 0, 0, 80));
                        g.fillRect(px, py, step, step);
                    }
                } else if (!pendingRequests.contains(key)) {
                pendingRequests.add(key);
                // Gerundete Koordinaten für API verwenden
                double roundedLat = Math.round(geo.getLatitude() * 10.0) / 10.0;
                double roundedLon = Math.round(geo.getLongitude() * 10.0) / 10.0;
                    executor.submit(() -> {
                        try {
                            Thread.sleep(500); // 500ms warten zwischen Anfragen
                            double elev = getElevation(roundedLat, roundedLon);
                            elevationCache.put(key, elev);
                            SwingUtilities.invokeLater(map::repaint);
                        } catch (Exception e) {
                            System.out.println("Fehler: " + e.getMessage());
                            pendingRequests.remove(key);
                        }
                    });
            }
            }
        }
    }


    public double getElevation(double lat, double lon) throws IOException {
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
            String value = body.replaceAll(".*\\[(.*?)].*", "$1");
            return Double.parseDouble(value.trim());
        }
    }



}
