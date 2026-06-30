package schnee.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import schnee.dataservice.DataService;
import schnee.dataservice.GridBuilder;
import schnee.dataservice.ElevationService;
import schnee.dataservice.SnowDepthService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Koordiniert MapPanel, ControlPanel und die Datenbeschaffung.
public class LocalMapServer {

    private final GridBuilder gridBuilder;
    private final ElevationService     elevationService;
    private final SnowDepthService     snowDepthService;

    private MapPanel     mapPanel;
    private ControlPanel controlPanel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isProcessing = false;


    public LocalMapServer(GridBuilder gridBuilder,
                          ElevationService elevationService,
                          SnowDepthService snowDepthService) {
        this.gridBuilder      = gridBuilder;
        this.elevationService = elevationService;
        this.snowDepthService = snowDepthService;
    }

    public void start() {
        SwingUtilities.invokeLater(this::buildAndShowGui);
    }

    public void stop() {
        executor.shutdownNow();
    }

    private void buildAndShowGui() {
        JFrame frame = new JFrame("⛰ Schnee-Checker");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 780);
        frame.setMinimumSize(new Dimension(700, 500));
        frame.setLocationRelativeTo(null);

        mapPanel     = new MapPanel();
        controlPanel = new ControlPanel(
                mapPanel,
                v -> loadSelectedMode()
        );

        frame.setLayout(new BorderLayout(0, 0));
        frame.add(mapPanel,     BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.EAST);
        frame.setVisible(true);
    }

    private void loadSelectedMode() {
        if (controlPanel.getMode() == ControlPanel.Mode.ELEVATION) {
            loadElevation();
        } else {
            loadSnowDepth();
        }
    }

    private void loadElevation() {
        if (isProcessing) return;

        startStatusPoller(elevationService);
        isProcessing = true;
        controlPanel.setUpdateEnabled(false);

        int threshold = controlPanel.getThreshold();
        int grid      = controlPanel.getGridSize();

        double[] bbox = mapPanel.getBoundingBox();
        double minLat = bbox[0], maxLat = bbox[1];
        double minLon = bbox[2], maxLon = bbox[3];

        controlPanel.setStatus("Lade Höhendaten...", -1, new Color(107, 197, 255));
        System.out.printf("Anfrage: bbox=[%.4f,%.4f,%.4f,%.4f] threshold=%dm grid=%dx%d%n",
                minLat, maxLat, minLon, maxLon, threshold, grid, grid);

        executor.submit(() -> {
            try {
                String geoJson = gridBuilder.buildGeoJson(
                        minLat, maxLat, minLon, maxLon, threshold, grid, elevationService);

                List<PolygonFeature> polygons = parsePolygons(geoJson);

                int count = polygons.size();

                SwingUtilities.invokeLater(() -> {
                    mapPanel.setPolygons(polygons, false);
                    controlPanel.setStatus(count + " Flächen über " + threshold + " m", -1,
                            new Color(107, 197, 255));
                    controlPanel.setUpdateEnabled(true);
                    isProcessing = false;
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    controlPanel.setStatus("Fehler: " + ex.getMessage(), -1, new Color(255, 100, 100));
                    controlPanel.setUpdateEnabled(true);
                    isProcessing = false;
                });
                System.err.println("Fehler: " + ex.getMessage());
            }
        });
    }

    private void loadSnowDepth() {
        if (isProcessing) return;

        startStatusPoller(snowDepthService);
        isProcessing = true;
        controlPanel.setUpdateEnabled(false);

        int threshold = controlPanel.getThreshold();
        int grid      = controlPanel.getGridSize();

        double[] bbox = mapPanel.getBoundingBox();
        double minLat = bbox[0], maxLat = bbox[1];
        double minLon = bbox[2], maxLon = bbox[3];

        controlPanel.setStatus("Lade Schneedaten...", -1, new Color(107, 197, 255));
        System.out.printf("Anfrage: bbox=[%.4f,%.4f,%.4f,%.4f] threshold=%dcm grid=%dx%d%n",
                minLat, maxLat, minLon, maxLon, threshold, grid, grid);

        executor.submit(() -> {
            try {
                String geoJson = gridBuilder.buildGeoJson(
                        minLat, maxLat, minLon, maxLon, threshold, grid, snowDepthService);

                List<PolygonFeature> polygons = parsePolygons(geoJson);
                int count = polygons.size();

                SwingUtilities.invokeLater(() -> {
                    mapPanel.setPolygons(polygons, true);
                    controlPanel.setStatus(count + " Flächen über " + threshold + " cm Schnee", -1,
                            new Color(107, 197, 255));
                    controlPanel.setUpdateEnabled(true);
                    isProcessing = false;
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    controlPanel.setStatus("Fehler: " + ex.getMessage(), -1, new Color(255, 100, 100));
                    controlPanel.setUpdateEnabled(true);
                    isProcessing = false;
                });
                System.err.println("Fehler: " + ex.getMessage());
            }
        });
    }

    public record PolygonFeature(double[][] points, double value) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<PolygonFeature> parsePolygons(String geoJson) throws Exception {
        List<PolygonFeature> result = new ArrayList<>();

        JsonNode root = MAPPER.readTree(geoJson);
        JsonNode features = root.path("features");

        for (JsonNode feature : features) {
            // GeoJSON liefert Koordinaten als [lon, lat] – pro Polygon ein Ring
            // mit beliebig vielen Punkten: [[[lon, lat], [lon, lat], ...]]
            JsonNode ring = feature.path("geometry").path("coordinates").get(0);
            if (ring == null) continue;

            double value = feature.path("properties").path("value").asDouble();

            List<double[]> points = new ArrayList<>();
            for (JsonNode point : ring) {
                double lon = point.get(0).asDouble();
                double lat = point.get(1).asDouble();
                points.add(new double[]{lat, lon});
            }

            if (!points.isEmpty()) {
                result.add(new PolygonFeature(points.toArray(new double[0][]), value));
            }
        }

        return result;
    }

    // =========================================================================
    // Status-Anzeige für den Ladefortschritt von
    // =========================================================================

    private void startStatusPoller(DataService dataService) {
        new Timer(500, e -> {
            if (dataService.isLoading()) {
                long loaded = dataService.getLoadedPoints();
                long total  = dataService.getTotalPoints();
                String msg  = total > 0
                        ? String.format("Lade Punkte: %,d / %,d", loaded, total)
                        : String.format("Lade Punkte: %,d", loaded);
                int progress = (total > 0) ? (int) (loaded * 100 / total) : 0;
                controlPanel.setStatus(msg, progress, new Color(107, 197, 255));
            }
        }).start();
    }
}
