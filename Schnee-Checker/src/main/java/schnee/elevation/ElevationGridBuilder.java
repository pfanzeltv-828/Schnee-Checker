package schnee.elevation;

import java.io.IOException;
import java.util.*;

public class ElevationGridBuilder {

    private final ElevationService elevationService;

    public ElevationGridBuilder(ElevationService elevationService) {
        this.elevationService = elevationService;
    }

    /**
     * Startet Laden im Hintergrund und gibt sofort zurück was im Cache ist.
     */
    public String buildGeoJson(double minLat, double maxLat,
                               double minLon, double maxLon,
                               int threshold, int gridSize) throws IOException {

        double latStep = (maxLat - minLat) / gridSize;
        double lonStep = (maxLon - minLon) / gridSize;

        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                double lat = minLat + i * latStep + latStep / 2;
                double lon = minLon + j * lonStep + lonStep / 2;
                points.add(new double[]{lat, lon});
            }
        }

        // Im Hintergrund laden
        if (!elevationService.isLoading()) {
            new Thread(() -> {
                try {
                    elevationService.getElevations(points);

                } catch (Exception e) {
                    System.err.println("Fehler beim Laden: " + e.getMessage());
                }
            }).start();
        }

        // Sofort GeoJSON aus Cache bauen
        return buildFromCache(minLat, maxLat, minLon, maxLon, threshold, gridSize);
    }

    /**
     * Baut GeoJSON aus dem aktuellen Cache - sofort verfügbar.
     */
    public String buildFromCache(double minLat, double maxLat,
                                 double minLon, double maxLon,
                                 int threshold, int gridSize) {

        double latStep = (maxLat - minLat) / gridSize;
        double lonStep = (maxLon - minLon) / gridSize;

        Map<String, Double> cache = elevationService.getCache();

        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[");

        boolean first = true;
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                double lat = minLat + i * latStep + latStep / 2;
                double lon = minLon + j * lonStep + lonStep / 2;
                String key = elevationService.makeKey(lat, lon);

                Double elev = cache.get(key);
                if (elev == null || elev <= threshold) continue;

                if (!first) sb.append(",");
                first = false;

                double s = minLat + i * latStep;
                double n = s + latStep;
                double w = minLon + j * lonStep;
                double e = w + lonStep;

                sb.append("{\"type\":\"Feature\",");
                sb.append("\"properties\":{\"elevation\":").append((int) Math.round(elev)).append("},");
                sb.append("\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[");
                sb.append("[").append(w).append(",").append(s).append("],");
                sb.append("[").append(e).append(",").append(s).append("],");
                sb.append("[").append(e).append(",").append(n).append("],");
                sb.append("[").append(w).append(",").append(n).append("],");
                sb.append("[").append(w).append(",").append(s).append("]");
                sb.append("]]}}");
            }
        }

        sb.append("]}");
        return sb.toString();
    }
}
