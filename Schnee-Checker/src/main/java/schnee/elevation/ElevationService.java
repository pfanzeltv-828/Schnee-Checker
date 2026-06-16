package schnee.elevation;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class ElevationService {

    private static final String API_URL = "https://api.open-meteo.com/v1/elevation";

    private final Map<String, Double> cache = Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
                    return size() > 50000;
                }
            }
    );

    // Aktueller Ladestatus
    private volatile boolean loading = false;
    private volatile int totalPoints = 0;
    private volatile int loadedPoints = 0;

    public Map<String, Double> getElevations(List<double[]> points) throws IOException {
        Map<String, Double> results = new LinkedHashMap<>();

        List<double[]> toFetch = new ArrayList<>();
        for (double[] pt : points) {
            String key = makeKey(pt[0], pt[1]);
            if (cache.containsKey(key)) {
                results.put(key, cache.get(key));
            } else {
                toFetch.add(pt);
            }
        }

        if (toFetch.isEmpty()) return results;

        loading = true;
        totalPoints = toFetch.size();
        loadedPoints = 0;

        try {
            for (int i = 0; i < toFetch.size(); i += 100) {
                List<double[]> batch = toFetch.subList(i, Math.min(i + 100, toFetch.size()));

                try {
                    Map<String, Double> batchResults = fetchBatch(batch);
                    results.putAll(batchResults);
                    loadedPoints += batchResults.size();
                    System.out.println("Batch fertig: " + loadedPoints + "/" + totalPoints);
                } catch (IOException e) {
                    if (e.getMessage().contains("429") || e.getMessage().contains("400")) {
                        System.out.println("Rate limit - warte 60s...");
                        try { Thread.sleep(60000); } catch (InterruptedException ignored) {}
                        Map<String, Double> batchResults = fetchBatch(batch);
                        results.putAll(batchResults);
                        loadedPoints += batchResults.size();
                    } else {
                        throw e;
                    }
                }

                if (i + 100 < toFetch.size()) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }
        } finally {
            loading = false;
        }

        return results;
    }


    private Map<String, Double> fetchBatch(List<double[]> points) throws IOException {
        Map<String, Double> results = new LinkedHashMap<>();

        StringBuilder lats = new StringBuilder();
        StringBuilder lons = new StringBuilder();
        for (double[] pt : points) {
            if (lats.length() > 0) { lats.append(","); lons.append(","); }
            lats.append(String.format(Locale.US, "%.4f", pt[0]));
            lons.append(String.format(Locale.US, "%.4f", pt[1]));
        }

        String urlStr = API_URL + "?latitude=" + lats + "&longitude=" + lons;
        String response = httpGet(urlStr);

        String valuesStr = response.replaceAll(".*\\[(.*)\\].*", "$1");
        String[] values = valuesStr.split(",");

        for (int i = 0; i < values.length && i < points.size(); i++) {
            double elev = Double.parseDouble(values[i].trim());
            String key = makeKey(points.get(i)[0], points.get(i)[1]);
            cache.put(key, elev);
            results.put(key, elev);
        }

        return results;
    }

    private String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "SchneeChecker/3.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " für " + urlStr);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    public boolean isLoading() { return loading; }
    public int getTotalPoints() { return totalPoints; }
    public int getLoadedPoints() { return loadedPoints; }
    public Map<String, Double> getCache() { return cache; }
    public String makeKey(double lat, double lon) {
        return String.format(Locale.US, "%.4f,%.4f", lat, lon);
    }
}
