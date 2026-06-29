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

public class SnowDepthService extends DataService {

    private static final String API_URL = "https://api.open-meteo.com/v1/forecast";

    private final Map<String, Double> cache =
            Collections.synchronizedMap(
                    new LinkedHashMap<>() {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
                            return size() > 1000;
                        }
                    }
            );

    private final Map<String, Double> polygonCache =
            Collections.synchronizedMap(
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

    public void fetchMissing(List<double[]> points, double latStep, double lonStep) throws IOException {
        List<double[]> toFetch = new ArrayList<>();
        int hits = 0;

        for (double[] pt : points) {
            String key = makeKey(pt[0], pt[1]);
            Double value = checkPolygonCache(pt[0], pt[1], latStep, lonStep);

            if (value != -1) {
                cache.put(key, value);
                hits++;
            } else {
                toFetch.add(pt);
            }
        }

        System.out.println("Cache hits: " + hits);

        if (toFetch.isEmpty()) {
            return;
        }

        loading = true;
        totalPoints = toFetch.size();
        loadedPoints = 0;

        try {

            for (int i = 0; i < totalPoints; i += 100) {

                List<double[]> batch = toFetch.subList(i, Math.min(i + 100, totalPoints));

                try {
                    fetchBatch(batch, latStep, lonStep);
                    loadedPoints += batch.size();
                    System.out.println("Batch fertig: " + loadedPoints + "/" + totalPoints);
                } catch (IOException e) {
                    if (e.getMessage().contains("429") || e.getMessage().contains("400")) {
                        System.out.println("Rate limit - warte 60s...");
                        try {
                            Thread.sleep(60000);
                        } catch (InterruptedException ignored) {}
                        fetchBatch(batch, latStep, lonStep);
                    } else {
                        throw e;
                    }
                }

                if (i + 100 < totalPoints) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {}
                }

            }
        } finally {
            loading = false;
        }
    }


    public Double checkPolygonCache(double lat, double lon, double currentLatStep, double currentLonStep) {
        for (Map.Entry<String, Double> entry : polygonCache.entrySet()) {
            String[] parts = entry.getKey().split(",");

            double centerLat = Double.parseDouble(parts[0]);
            double centerLon = Double.parseDouble(parts[1]);
            double storedLatStep = Double.parseDouble(parts[2]);
            double storedLonStep = Double.parseDouble(parts[3]);

            // nur gleich oder feiner verwenden
            if (storedLatStep > currentLatStep || storedLonStep > currentLonStep) {
                continue;
            }

            double minLat = centerLat - storedLatStep / 2;
            double maxLat = centerLat + storedLatStep / 2;
            double minLon = centerLon - storedLonStep / 2;
            double maxLon = centerLon + storedLonStep / 2;

            if (lat >= minLat && lat < maxLat && lon >= minLon && lon < maxLon) {
                return entry.getValue();
            }
        }
        return -1.0;
    }


    private final ObjectMapper mapper = new ObjectMapper();

    public void fetchBatch(List<double[]> points, double latStep, double lonStep) throws IOException {
        StringBuilder lats = new StringBuilder();
        StringBuilder lons = new StringBuilder();

        for (double[] pt : points) {
            if (!lats.isEmpty()) {
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

        String currentDayKey = currentHourKey.substring(0, currentHourKey.length() - 6);

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
            JsonNode hourly = root.get(i).get("hourly");
            JsonNode times = hourly.get("time");
            JsonNode snow = hourly.get("snow_depth");

            for (int j = 0; j < times.size(); j++) {
                if (currentHourKey.equals(times.get(j).asText())) {
                    double value = snow.get(j).asDouble() * 100;
                    double[] pt = points.get(i);

                    String key = makeKey(pt[0], pt[1]);
                    cache.put(key, value);

                    String polygon = makePolygonKey(pt[0], pt[1],latStep,lonStep);
                    polygonCache.putIfAbsent(polygon, value);

                    break;
                }
            }
        }
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

    public boolean isLoading() {return loading;}
    public int getTotalPoints() {return totalPoints;}
    public int getLoadedPoints() {return loadedPoints;}
    public Map<String, Double> getCache() {return cache;}
    public String makeKey(double lat, double lon) {
        return String.format(Locale.US, "%.4f,%.4f", lat, lon);
    }
    public String makePolygonKey(double lat, double lon, double latStep, double lonStep) {
        return String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", lat, lon, latStep, lonStep);
    }
}
