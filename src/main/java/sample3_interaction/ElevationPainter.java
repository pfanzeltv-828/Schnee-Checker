package sample3_interaction;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class ElevationPainter implements Painter<JXMapViewer> {

    private final Map<String, Double> elevationCache = new ConcurrentHashMap<>();
    private final Set<String> pendingRequests = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private boolean isFetching = false;
    private Rectangle lastBounds = null;

    @Override
    public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
        Rectangle bounds = map.getViewportBounds();

        boolean boundsChanged = !bounds.equals(lastBounds);
        lastBounds = bounds;

        int step = (int) Math.sqrt((w * h) / 20.0); // nur 20 Punkte pro Anfrage
        step = Math.max(20, step);

        for (int px = 0; px < w; px += step) {
            for (int py = 0; py < h; py += step) {

                Point2D worldPt = new Point2D.Double(
                        px + bounds.getX(),
                        py + bounds.getY()
                );
                GeoPosition geo = map.getTileFactory()
                        .pixelToGeo(worldPt, map.getZoom());

                String key = String.format(Locale.US,"%.2f,%.2f",
                        geo.getLatitude(), geo.getLongitude());
                System.out.println(key);
                System.out.println(elevationCache);
                if (elevationCache.containsKey(key)) {
                    if (elevationCache.get(key) > 1000) {
                        g.setColor(new Color(255, 0, 0, 80));
                        g.fillRect(px, py, step, step);
                        //System.out.println("draw");
                    }
                } else if (boundsChanged && !isFetching && !pendingRequests.contains(key)) {
                    pendingRequests.add(key);
                    //System.out.println("not");
                }
            }
        }

        // Alle ausstehenden Punkte sammeln
        List<GeoPosition> toFetch = new ArrayList<>();
        for (String k : pendingRequests) {
            if (!elevationCache.containsKey(k)) {
                String[] parts = k.split(",");
                toFetch.add(new GeoPosition(
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1])
                ));
            }
        }

        if (!toFetch.isEmpty() && !isFetching) {
            isFetching = true;
            List<GeoPosition> batch = toFetch.subList(0, Math.min(100, toFetch.size()));
            executor.submit(() -> {
                try {
                    Thread.sleep(5000); // 2 Sekunden warten
                    Map<String, Double> results = getElevations(batch);
                    System.out.println("batch");
                    elevationCache.putAll(results);
                    pendingRequests.removeAll(results.keySet());
                    SwingUtilities.invokeLater(() -> map.repaint());
                } catch (Exception e) {
                    System.out.println("Fehler: " + e.getMessage());
                    pendingRequests.clear();
                } finally {
                    isFetching = false;
                }
            });
        }
    }

    public Map<String, Double> getElevations(List<GeoPosition> positions) throws Exception {
        Map<String, Double> allResults = new HashMap<>();

        // Duplikate entfernen
        Set<String> seen = new HashSet<>();
        List<GeoPosition> unique = new ArrayList<>();
        for (GeoPosition pos : positions) {
            String key = String.format(Locale.US,"%.2f,%.2f", pos.getLatitude(), pos.getLongitude());
            if (seen.add(key)) {
                unique.add(pos);
            }
        }

        // Koordinaten für API zusammenbauen
        StringBuilder latitudes = new StringBuilder();
        StringBuilder longitudes = new StringBuilder();
        for (GeoPosition pos : unique) {
            if (latitudes.length() > 0) {
                latitudes.append(",");
                longitudes.append(",");
            }
            latitudes.append(pos.getLatitude());
            longitudes.append(pos.getLongitude());
        }

        String urlStr = String.format(
                "https://api.open-meteo.com/v1/elevation?latitude=%s&longitude=%s",
                latitudes, longitudes
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

            // Response: {"elevation":[731.0, 500.0, ...]}
            String body = sb.toString();
            String values = body.replaceAll(".*\\[(.*?)].*", "$1");
            String[] elevations = values.split(",");

            for (int i = 0; i < elevations.length && i < unique.size(); i++) {
                double elev = Double.parseDouble(elevations[i].trim());
                GeoPosition pos = unique.get(i);
                String key = String.format(Locale.US,"%.2f,%.2f", pos.getLatitude(), pos.getLongitude());
                allResults.put(key, elev);
            }
        }

        return allResults;
    }
}
/*
public class ElevationPainter implements Painter<JXMapViewer> {

    private final Map<String, Double> elevationCache = new ConcurrentHashMap<>();
    private final Set<String> pendingRequests = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    @Override
    public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
        //System.out.println("Paint wird aufgerufen! Cache size: " + elevationCache.size());
        Rectangle bounds = map.getViewportBounds();
        System.out.println(bounds);
        int step = 80;

        for (int px = 0; px < w; px += step) {
            for (int py = 0; py < h; py += step) {

                Point2D worldPt = new Point2D.Double(
                        px + bounds.getX(),
                        py + bounds.getY()
                );
                GeoPosition geo = map.getTileFactory()
                        .pixelToGeo(worldPt, map.getZoom());

                String key = String.format("%.5f,%.5f",
                        geo.getLatitude(), geo.getLongitude());

                if (elevationCache.containsKey(key)) {
                    //System.out.println("Cache hit: " + elevationCache.get(key)); // ← hinzufügen
                    if (elevationCache.get(key) > 1000) {
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
                            Map<String, Double> results = getElevations(limitedFetch);
                            elevationCache.putAll(results);
                            pendingRequests.clear(); // ← alle pending löschen
                            SwingUtilities.invokeLater(() -> map.repaint());
                        } catch (Exception e) {
                            System.out.println("Fehler: " + e.getMessage());
                        } finally {
                            isFetching = false;
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
*/