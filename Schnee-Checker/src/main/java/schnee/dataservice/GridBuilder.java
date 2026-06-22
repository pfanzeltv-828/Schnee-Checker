package schnee.dataservice;

import java.io.IOException;
import java.util.*;

public class GridBuilder {

    public String buildGeoJson(double minLat, double maxLat,
                               double minLon, double maxLon,
                               int threshold, int gridSize, DataService dataService) throws IOException {

        List<double[]> points = new ArrayList<>();

        double latStep = (maxLat - minLat) / gridSize;
        double lonStep = (maxLon - minLon) / gridSize;

        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                double lat = minLat + i * latStep + latStep / 2;
                double lon = minLon + j * lonStep + lonStep / 2;
                points.add(new double[]{lat, lon});
            }
        }

        //dataService.fillCache(points); -> caching funktioniert noch nicht, direkt fetchbatch (alle punkte neuladen)
        dataService.fetchBatch(points);

        return loadFromCache(minLat, maxLat, minLon, maxLon, threshold, gridSize, dataService);
    }

    /**
     * Baut GeoJSON aus dem aktuellen Cache.
     */
    public String loadFromCache(double minLat, double maxLat,
                                 double minLon, double maxLon,
                                 int threshold, int gridSize, DataService dataService) {

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

                Double value = cache.get(key);
                if (value == null || value <= threshold) continue;

                if (!first) sb.append(",");
                first = false;

                double s = minLat + i * latStep;
                double n = s + latStep;
                double w = minLon + j * lonStep;
                double e = w + lonStep;

                sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[")
                        .append("[").append(w).append(",").append(s).append("],")
                        .append("[").append(e).append(",").append(s).append("],")
                        .append("[").append(e).append(",").append(n).append("],")
                        .append("[").append(w).append(",").append(n).append("],")
                        .append("[").append(w).append(",").append(s).append("]")
                        .append("]]},")
                        .append("\"properties\":{\"value\":").append(value).append("}")
                        .append("}");
            }
        }

        sb.append("]}");
        return sb.toString();
    }

}
