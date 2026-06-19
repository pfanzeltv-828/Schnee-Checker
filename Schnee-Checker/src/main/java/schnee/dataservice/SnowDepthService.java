package schnee.dataservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SnowDepthService extends DataService{

    private static final String API_URL = "https://api.open-meteo.com/v1/forecast";

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

    public Map<String, Double> getData(List<double[]> points) throws IOException {
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

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Double> fetchBatch(List<double[]> points)
            throws IOException {

        Map<String, Double> results = new LinkedHashMap<>();

        StringBuilder lats = new StringBuilder();
        StringBuilder lons = new StringBuilder();

        for (double[] pt : points) {

            if (lats.length() > 0) {
                lats.append(",");
                lons.append(",");
            }

            lats.append(String.format(Locale.US, "%.4f", pt[0]));
            lons.append(String.format(Locale.US, "%.4f", pt[1]));
        }

        String currentHourKey =
                LocalDateTime.now()
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0)
                        .format(
                                DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd'T'HH:mm"
                                )
                        );

        String currentDayKey = currentHourKey.substring(0,currentHourKey.length()-6);

        String url =
                API_URL
                        + "?latitude=" + lats
                        + "&longitude=" + lons
                        + "&hourly=snow_depth"
                        + "&start_date=" + currentDayKey
                        + "&end_date=" + currentDayKey;

        String response = httpGet(url);

        JsonNode root = mapper.readTree(response);

        for (int i = 0; i < root.size() && i < points.size(); i++) {

            JsonNode hourly =
                    root.get(i).get("hourly");

            JsonNode times =
                    hourly.get("time");

            JsonNode snow =
                    hourly.get("snow_depth");

            for (int j = 0; j < times.size(); j++) {

                if (currentHourKey.equals(times.get(j).asText())) {

                    double value =
                            snow.get(j).asDouble() * 100;

                    double[] pt =
                            points.get(i);

                    String key =
                            makeKey(
                                    pt[0],
                                    pt[1]
                            );

                    cache.put(key, value);
                    results.put(key, value);

                    break;
                }
            }
        }
System.out.println(results);
        return results;
    }

    private String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "SchneeChecker");
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
