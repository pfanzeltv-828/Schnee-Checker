package schnee.server;

import schnee.elevation.ElevationGridBuilder;
import schnee.elevation.ElevationService;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Koordiniert MapPanel, ControlPanel und die Datenbeschaffung.
 *
 * Kennt als einzige Klasse sowohl MapPanel als auch ControlPanel und
 * verdrahtet sie: reicht den "Aktualisieren"-Klick aus dem ControlPanel
 * an loadElevation() weiter, ruft gridBuilder auf, parst das Ergebnis
 * mit GeoJsonParser und übergibt es an mapPanel.setPolygons(...).
 *
 * Enthält selbst keine Zeichen-, Parse- oder reine UI-Aufbaulogik mehr –
 * die liegt in MapPanel, GeoJsonParser bzw. ControlPanel.
 */
public class LocalMapServer {

    private final ElevationGridBuilder gridBuilder;
    private final ElevationService     elevationService;

    private MapPanel     mapPanel;
    private ControlPanel controlPanel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isProcessing = false;

    public LocalMapServer(ElevationGridBuilder gridBuilder,
                          ElevationService elevationService) {
        this.gridBuilder      = gridBuilder;
        this.elevationService = elevationService;
    }

    public void start() {
        SwingUtilities.invokeLater(this::buildAndShowGui);
    }

    public void stop() {
        executor.shutdownNow();
    }

    // =========================================================================
    // GUI-Aufbau: nur Verdrahtung, kein eigenes Layout-Detail mehr
    // =========================================================================

    private void buildAndShowGui() {
        JFrame frame = new JFrame("⛰ Schnee-Checker");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 780);
        frame.setMinimumSize(new Dimension(700, 500));
        frame.setLocationRelativeTo(null);

        mapPanel     = new MapPanel();
        controlPanel = new ControlPanel(mapPanel, v -> loadElevation());

        frame.setLayout(new BorderLayout(0, 0));
        frame.add(mapPanel,     BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.EAST);
        frame.setVisible(true);

        startStatusPoller();
    }

    // =========================================================================
    // Höhendaten laden – liest Zoom/Lat/Lon immer frisch vom MapPanel,
    // Threshold/Grid immer frisch vom ControlPanel
    // =========================================================================

    private void loadElevation() {
        if (isProcessing) return;

        isProcessing = true;
        controlPanel.setUpdateEnabled(false);

        int threshold = controlPanel.getThreshold();
        int grid      = controlPanel.getGridSize();

        double[] bbox = mapPanel.getBoundingBox();
        double minLat = bbox[0], maxLat = bbox[1];
        double minLon = bbox[2], maxLon = bbox[3];

        controlPanel.setStatus("Lade Höhendaten...", new Color(107, 197, 255));
        System.out.printf("Anfrage: bbox=[%.4f,%.4f,%.4f,%.4f] threshold=%dm grid=%dx%d%n",
                minLat, maxLat, minLon, maxLon, threshold, grid, grid);

        executor.submit(() -> {
            try {
                String geoJson = gridBuilder.buildGeoJson(
                        minLat, maxLat, minLon, maxLon, threshold, grid);

                List<double[][]> polygons = GeoJsonParser.parsePolygons(geoJson);
                int count = polygons.size();

                SwingUtilities.invokeLater(() -> {
                    mapPanel.setPolygons(polygons);
                    controlPanel.setStatus(count + " Flächen über " + threshold + " m",
                                            new Color(107, 255, 155));
                    controlPanel.setUpdateEnabled(true);
                    isProcessing = false;
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    controlPanel.setStatus("Fehler: " + ex.getMessage(), new Color(255, 100, 100));
                    controlPanel.setUpdateEnabled(true);
                    isProcessing = false;
                });
                System.err.println("Fehler: " + ex.getMessage());
            }
        });
    }

    // =========================================================================
    // Status-Anzeige für den Ladefortschritt von ElevationService
    // =========================================================================

    private void startStatusPoller() {
        new Timer(500, e -> {
            if (elevationService.isLoading()) {
                long loaded = elevationService.getLoadedPoints();
                long total  = elevationService.getTotalPoints();
                String msg  = total > 0
                        ? String.format("Lade Punkte: %,d / %,d", loaded, total)
                        : String.format("Lade Punkte: %,d", loaded);
                controlPanel.setStatus(msg, new Color(107, 197, 255));
            }
        }).start();
    }
}
