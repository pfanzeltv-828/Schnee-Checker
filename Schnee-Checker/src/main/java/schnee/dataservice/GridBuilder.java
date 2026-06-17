package schnee.dataservice;

import java.io.IOException;
import java.util.*;

public class GridBuilder {

    private final DataService elevationService;
    private final DataService snowDepthService;

    public GridBuilder(DataService elevationService, DataService snowDepthService) {
        this.elevationService = elevationService;
        this.snowDepthService = snowDepthService;
    }

    /**
     * Startet Laden im Hintergrund und gibt sofort zurück was im Cache ist.
     */
    public String buildGeoJson(double minLat, double maxLat,
                               double minLon, double maxLon,
                               int data, int gridSize, DataService dataService) throws IOException {

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
        if (!dataService.isLoading()) {
            new Thread(() -> {
                try {
                    dataService.getData(points);

                } catch (Exception e) {
                    System.err.println("Fehler beim Laden: " + e.getMessage());
                }
            }).start();
        }

        // Sofort GeoJSON aus Cache bauen
        return buildFromCache(minLat, maxLat, minLon, maxLon, data, gridSize, dataService);
    }

    /**
     * Baut GeoJSON aus dem aktuellen Cache - sofort verfügbar.
     */
    public String buildFromCache(double minLat, double maxLat,
                                 double minLon, double maxLon,
                                 int data, int gridSize, DataService dataService) {

        double latStep = (maxLat - minLat) / gridSize;
        double lonStep = (maxLon - minLon) / gridSize;

        Map<String, Double> cache = dataService.getCache();

        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[");

        boolean first = true;
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                double lat = minLat + i * latStep + latStep / 2;
                double lon = minLon + j * lonStep + lonStep / 2;
                String key = dataService.makeKey(lat, lon);

                Double inf = cache.get(key);
                if (inf == null || inf <= data) continue;

                if (!first) sb.append(",");
                first = false;

                double s = minLat + i * latStep;
                double n = s + latStep;
                double w = minLon + j * lonStep;
                double e = w + lonStep;

                sb.append("{\"type\":\"Feature\",");
                sb.append("\"properties\":{\"elevation\":").append((int) Math.round(inf)).append("},");
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
