package schnee.server;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimaler GeoJSON-Parser ohne externe Bibliotheken.
 *
 * Wandelt einen GeoJSON-FeatureCollection-String (wie von
 * {@code ElevationGridBuilder.buildGeoJson(...)} erzeugt) in eine Liste
 * von Polygonen um. Jedes Polygon ist ein Array von {@code [lat, lon]}-Punkten.
 *
 * Bewusst einfach gehalten: unterstützt nur Polygone mit genau einem Ring
 * (keine Löcher, keine MultiPolygons). Erwartet wird das Standardformat
 * {@code "coordinates": [[[lon, lat], [lon, lat], ...]]}.
 */
public final class GeoJsonParser {

    private GeoJsonParser() {
        // Nur statische Nutzung – kein Zustand, keine Instanz nötig
    }

    /**
     * Extrahiert alle Polygone aus einem GeoJSON-String.
     *
     * @param geoJson GeoJSON-FeatureCollection als Text
     * @return Liste von Polygonen, jedes als Array von {lat, lon}-Punkten
     */
    public static List<double[][]> parsePolygons(String geoJson) {
        List<double[][]> result = new ArrayList<>();
        int idx = 0;
        while ((idx = geoJson.indexOf("\"coordinates\"", idx)) != -1) {
            idx += "\"coordinates\"".length();
            while (idx < geoJson.length() && geoJson.charAt(idx) != '[') idx++;

            int depth = 0;
            int end   = idx;
            StringBuilder sb = new StringBuilder();
            for (int i = idx; i < geoJson.length(); i++) {
                char c = geoJson.charAt(i);
                if (c == '[') depth++;
                if (c == ']') depth--;
                sb.append(c);
                if (depth == 0) { end = i; break; }
            }

            List<double[]> ring = parseFirstRing(sb.toString());
            if (!ring.isEmpty()) result.add(ring.toArray(new double[0][]));
            idx = end + 1;
        }
        return result;
    }

    /** Parst den ersten Ring eines Polygons aus einem [[lon,lat],...]-String. */
    private static List<double[]> parseFirstRing(String block) {
        List<double[]> points = new ArrayList<>();
        int i = 0, openBrackets = 0;
        while (i < block.length()) {
            char c = block.charAt(i);
            if (c == '[') {
                openBrackets++;
                if (openBrackets == 3) {
                    int end = block.indexOf(']', i);
                    if (end == -1) break;
                    String[] parts = block.substring(i + 1, end).trim().split(",");
                    if (parts.length >= 2) {
                        try {
                            double lon = Double.parseDouble(parts[0].trim());
                            double lat = Double.parseDouble(parts[1].trim());
                            points.add(new double[]{lat, lon});
                        } catch (NumberFormatException ignored) {}
                    }
                    i = end;
                    openBrackets--;
                }
            } else if (c == ']') {
                openBrackets--;
                if (openBrackets < 2 && !points.isEmpty()) break;
            }
            i++;
        }
        return points;
    }
}
