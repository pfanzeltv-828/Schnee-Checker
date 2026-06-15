package schnee.server;

import schnee.elevation.ElevationGridBuilder;
import schnee.elevation.ElevationService;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Swing-Anwendung als Ersatz für den lokalen HTTP-Server + Leaflet-Browser.
 *
 * Zeigt eine OpenStreetMap-Kachel-Karte mit eingezeichneten Höhengebieten,
 * ohne Browser und ohne HTTP-Server.
 *
 * MapPanel ist die einzige Quelle der Wahrheit für Zoom, Lat und Lon.
 * LocalMapServer liest diese Werte beim Aktualisieren immer frisch vom Panel.
 */
public class LocalMapServer {

    private final ElevationGridBuilder gridBuilder;
    private final ElevationService     elevationService;

    private MapPanel mapPanel;
    private JSlider  thresholdSlider;
    private JSlider  gridSlider;
    private JLabel   thresholdLabel;
    private JLabel   gridLabel;
    private JLabel   statusLabel;
    private JLabel   zoomLabel;
    private JSlider  zoomSlider;
    private JButton  updateBtn;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isProcessing = false;

    // -------------------------------------------------------------------------

    public LocalMapServer(ElevationGridBuilder gridBuilder,
                          ElevationService elevationService) {
        this.gridBuilder      = gridBuilder;
        this.elevationService = elevationService;
    }

    /** Startet die Swing-GUI (ersetzt server.start()). */
    public void start() {
        SwingUtilities.invokeLater(this::buildAndShowGui);
    }

    /** Kein HTTP-Server mehr – stop() beendet nur den Executor. */
    public void stop() {
        executor.shutdownNow();
    }

    // =========================================================================
    // GUI-Aufbau
    // =========================================================================

    private void buildAndShowGui() {
        JFrame frame = new JFrame("⛰ Schnee-Checker");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 780);
        frame.setMinimumSize(new Dimension(700, 500));
        frame.setLocationRelativeTo(null);

        // MapPanel zuerst erzeugen – wird im Control-Panel referenziert
        mapPanel = new MapPanel();
        mapPanel.setBackground(new Color(30, 30, 40));

        // Callback: MapPanel ruft dies auf, wenn Zoom per Mausrad geändert wird
        mapPanel.setOnZoomChanged(z -> SwingUtilities.invokeLater(() -> {
            if (zoomSlider != null) zoomSlider.setValue(z);
            if (zoomLabel  != null) zoomLabel.setText("Zoom: " + z);
        }));

        JPanel controlPanel = buildControlPanel();

        frame.setLayout(new BorderLayout(0, 0));
        frame.add(mapPanel,     BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.EAST);
        frame.setVisible(true);

        startStatusPoller();
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(20, 20, 32));
        panel.setBorder(new EmptyBorder(18, 16, 18, 16));
        panel.setPreferredSize(new Dimension(240, 0));

        // Titel
        JLabel title = new JLabel("⛰ SCHNEE-CHECKER");
        title.setForeground(new Color(255, 100, 100));
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(18));

        // --- Höhenschwelle ---
        thresholdLabel = makeLabel("Höhenschwelle: 500 m");
        panel.add(thresholdLabel);
        panel.add(Box.createVerticalStrut(5));

        thresholdSlider = new JSlider(0, 3000, 500);
        thresholdSlider.setBackground(new Color(20, 20, 32));
        thresholdSlider.setForeground(new Color(255, 100, 100));
        styleSlider(thresholdSlider);
        thresholdSlider.addChangeListener(e ->
                thresholdLabel.setText("Höhenschwelle: " + thresholdSlider.getValue() + " m"));
        panel.add(thresholdSlider);
        panel.add(Box.createVerticalStrut(16));

        // --- Rasterauflösung ---
        gridLabel = makeLabel("Rasterauflösung: 10 × 10");
        panel.add(gridLabel);
        panel.add(Box.createVerticalStrut(5));

        gridSlider = new JSlider(5, 30, 10);
        gridSlider.setSnapToTicks(true);
        gridSlider.setMajorTickSpacing(5);
        gridSlider.setBackground(new Color(20, 20, 32));
        gridSlider.setForeground(new Color(107, 197, 255));
        styleSlider(gridSlider);
        gridSlider.addChangeListener(e -> {
            int v = gridSlider.getValue();
            gridLabel.setText("Rasterauflösung: " + v + " × " + v);
        });
        panel.add(gridSlider);
        panel.add(Box.createVerticalStrut(20));

        // --- Zoom-Slider (synchronisiert mit Mausrad) ---
        panel.add(makeLabel("Zoom-Stufe:"));
        panel.add(Box.createVerticalStrut(4));

        // Initialwert direkt vom Panel holen
        int initialZoom = mapPanel.getZoom();
        zoomLabel  = makeLabel("Zoom: " + initialZoom);
        zoomSlider = new JSlider(3, 18, initialZoom);
        zoomSlider.setBackground(new Color(20, 20, 32));
        zoomSlider.setForeground(new Color(107, 255, 155));
        styleSlider(zoomSlider);

        // Slider → Panel (Benutzer zieht den Slider)
        zoomSlider.addChangeListener(e -> {
            int z = zoomSlider.getValue();
            zoomLabel.setText("Zoom: " + z);
            // Mittelpunkt vom Panel lesen, nicht aus äußeren Feldern!
            mapPanel.setView(mapPanel.getCenterLat(), mapPanel.getCenterLon(), z);
        });

        panel.add(zoomSlider);
        panel.add(zoomLabel);
        panel.add(Box.createVerticalStrut(20));

        // --- Aktualisieren-Button ---
        updateBtn = new JButton("Layer aktualisieren");
        updateBtn.setBackground(new Color(220, 60, 60));
        updateBtn.setForeground(Color.WHITE);
        updateBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        updateBtn.setFocusPainted(false);
        updateBtn.setBorderPainted(false);
        updateBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        updateBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        updateBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Alle Werte kommen direkt vom Panel – kein äußerer Zustand nötig
        updateBtn.addActionListener(e -> loadElevation());
        panel.add(updateBtn);
        panel.add(Box.createVerticalStrut(10));

        // --- Status ---
        statusLabel = new JLabel("Bereit");
        statusLabel.setForeground(new Color(120, 120, 140));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(statusLabel);

        panel.add(Box.createVerticalGlue());

        // --- Legende ---
        panel.add(buildLegend());

        return panel;
    }

    private void styleSlider(JSlider s) {
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(170, 170, 190));
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JPanel buildLegend() {
        JPanel leg = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leg.setBackground(new Color(20, 20, 32));
        leg.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel box = new JPanel();
        box.setBackground(new Color(220, 50, 50, 140));
        box.setBorder(BorderFactory.createLineBorder(new Color(220, 50, 50)));
        box.setPreferredSize(new Dimension(16, 16));

        JLabel txt = new JLabel("Über Schwelle");
        txt.setForeground(new Color(170, 170, 190));
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        leg.add(box);
        leg.add(txt);
        return leg;
    }

    // =========================================================================
    // Höhendaten laden – liest Zoom/Lat/Lon immer frisch vom MapPanel
    // =========================================================================

    private void loadElevation() {
        if (isProcessing) return;

        isProcessing = true;
        updateBtn.setEnabled(false);

        int threshold = thresholdSlider.getValue();
        int grid      = gridSlider.getValue();

        // BBox direkt vom Panel – enthält aktuellen Zoom nach Mausrad und Drag
        double[] bbox = mapPanel.getBoundingBox();
        double minLat = bbox[0], maxLat = bbox[1];
        double minLon = bbox[2], maxLon = bbox[3];

        setStatus("Lade Höhendaten...", new Color(107, 197, 255));
        System.out.printf("Anfrage: bbox=[%.4f,%.4f,%.4f,%.4f] threshold=%dm grid=%dx%d%n",
                minLat, maxLat, minLon, maxLon, threshold, grid, grid);

        executor.submit(() -> {
            try {
                String geoJson = gridBuilder.buildGeoJson(
                        minLat, maxLat, minLon, maxLon, threshold, grid);

                List<double[][]> polygons = parseGeoJsonPolygons(geoJson);
                int count = polygons.size();

                SwingUtilities.invokeLater(() -> {
                    mapPanel.setPolygons(polygons);
                    setStatus(count + " Flächen über " + threshold + " m",
                            new Color(107, 255, 155));
                    updateBtn.setEnabled(true);
                    isProcessing = false;
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Fehler: " + ex.getMessage(), new Color(255, 100, 100));
                    updateBtn.setEnabled(true);
                    isProcessing = false;
                });
                System.err.println("Fehler: " + ex.getMessage());
            }
        });
    }

    // =========================================================================
    // Minimaler GeoJSON-Parser (kein JSON-Framework nötig)
    // =========================================================================

    private List<double[][]> parseGeoJsonPolygons(String geoJson) {
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

    private List<double[]> parseFirstRing(String block) {
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

    // =========================================================================
    // Status-Anzeige
    // =========================================================================

    private void setStatus(String msg, Color color) {
        statusLabel.setText(msg);
        statusLabel.setForeground(color);
    }

    private void startStatusPoller() {
        new Timer(500, e -> {
            if (elevationService.isLoading()) {
                long loaded = elevationService.getLoadedPoints();
                long total  = elevationService.getTotalPoints();
                String msg  = total > 0
                        ? String.format("Lade Punkte: %,d / %,d", loaded, total)
                        : String.format("Lade Punkte: %,d", loaded);
                setStatus(msg, new Color(107, 197, 255));
            }
        }).start();
    }

    // =========================================================================
    // MapPanel – einzige Quelle der Wahrheit für Zoom, Lat, Lon
    // =========================================================================

    private static class MapPanel extends JPanel {

        // --- interner Zustand: NUR hier wird Zoom/Lat/Lon verwaltet ---
        private double centerLat = 47.5;
        private double centerLon = 11.5;
        private int    zoom      = 9;

        private List<double[][]> polygons = new ArrayList<>();

        // Kachel-Cache
        private final Map<String, Image> tileCache = new LinkedHashMap<>(256, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Image> e) {
                return size() > 200;
            }
        };
        private final ExecutorService tileExecutor = Executors.newFixedThreadPool(6);
        private final Set<String>     pending      = Collections.synchronizedSet(new HashSet<>());

        // Drag-Zustand
        private Point  dragStart    = null;
        private double dragStartLat, dragStartLon;

        // Callback → synchronisiert den Zoom-Slider im Control-Panel
        private java.util.function.IntConsumer onZoomChanged;

        MapPanel() {
            setBackground(new Color(30, 30, 40));

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    dragStart    = e.getPoint();
                    dragStartLat = centerLat;
                    dragStartLon = centerLon;
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragStart == null) return;
                    int dx = e.getX() - dragStart.x;
                    int dy = e.getY() - dragStart.y;
                    // Lat/Lon des Panels direkt aktualisieren
                    centerLon = dragStartLon - (dx / 256.0) * tile2LonDeg(zoom);
                    centerLat = dragStartLat + (dy / 256.0) * tile2LatDeg(dragStartLat, zoom);
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);

            // Mausrad: Zoom des Panels direkt aktualisieren + Callback auslösen
            addMouseWheelListener(e -> {
                zoom = Math.max(3, Math.min(18, zoom - (int) e.getWheelRotation()));
                repaint();
                if (onZoomChanged != null) onZoomChanged.accept(zoom);
            });
        }

        // --- Setter & Getter ---

        void setOnZoomChanged(java.util.function.IntConsumer cb) { this.onZoomChanged = cb; }

        /** Setzt Ansicht von außen (z.B. durch Zoom-Slider). */
        void setView(double lat, double lon, int z) {
            this.centerLat = lat;
            this.centerLon = lon;
            this.zoom      = z;
            repaint();
        }

        void setPolygons(List<double[][]> p) { this.polygons = p; repaint(); }

        /** Aktueller Zoom – für den Zoom-Slider-Initialwert. */
        int    getZoom()      { return zoom; }
        double getCenterLat() { return centerLat; }
        double getCenterLon() { return centerLon; }

        /**
         * Berechnet die Bounding Box des sichtbaren Kartenausschnitts.
         * Verwendet immer den internen Zoom/Lat/Lon – korrekt nach Mausrad und Drag.
         */
        double[] getBoundingBox() {
            int w = getWidth();
            int h = getHeight();
            if (w == 0) w = 800;
            if (h == 0) h = 600;

            double lonRange = (w / 256.0) * tile2LonDeg(zoom);
            double minLon   = centerLon - lonRange / 2.0;
            double maxLon   = centerLon + lonRange / 2.0;
            double maxLat   = pixelToLat(0,   h);
            double minLat   = pixelToLat(h,   h);
            return new double[]{minLat, maxLat, minLon, maxLon};
        }

        // ------------------------------------------------------------------
        // Zeichnen
        // ------------------------------------------------------------------

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            drawTiles(g2, w, h);
            drawPolygons(g2, w, h);

            // Fadenkreuz Mitte
            g2.setColor(new Color(255, 255, 255, 100));
            g2.drawLine(w / 2 - 10, h / 2, w / 2 + 10, h / 2);
            g2.drawLine(w / 2, h / 2 - 10, w / 2, h / 2 + 10);

            // Zoom-Info
            g2.setColor(new Color(200, 200, 220, 180));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g2.drawString(String.format("Zoom: %d  |  %.4f, %.4f", zoom, centerLat, centerLon),
                    10, h - 10);
        }

        private void drawTiles(Graphics2D g2, int w, int h) {
            int numTiles = 1 << zoom;

            double xTileCenter = lonToTileX(centerLon, zoom);
            double yTileCenter = latToTileY(centerLat, zoom);
            int tileX0 = (int) xTileCenter;
            int tileY0 = (int) yTileCenter;
            int offX   = (int) ((xTileCenter - tileX0) * 256);
            int offY   = (int) ((yTileCenter - tileY0) * 256);
            int startX = w / 2 - offX;
            int startY = h / 2 - offY;

            int rangeX = w / 256 / 2 + 2;
            int rangeY = h / 256 / 2 + 2;

            for (int dy = -rangeY; dy <= rangeY; dy++) {
                for (int dx = -rangeX; dx <= rangeX; dx++) {
                    int tx = ((tileX0 + dx) % numTiles + numTiles) % numTiles;
                    int ty = tileY0 + dy;
                    if (ty < 0 || ty >= numTiles) continue;

                    int px = startX + dx * 256;
                    int py = startY + dy * 256;

                    String key = zoom + "/" + tx + "/" + ty;
                    Image  img = tileCache.get(key);

                    if (img != null) {
                        g2.drawImage(img, px, py, 256, 256, this);
                    } else {
                        g2.setColor(new Color(40, 40, 55));
                        g2.fillRect(px, py, 256, 256);
                        g2.setColor(new Color(60, 60, 80));
                        g2.drawRect(px, py, 255, 255);
                        loadTile(key, tx, ty, zoom);
                    }
                }
            }
        }

        private void loadTile(String key, int tx, int ty, int z) {
            if (pending.contains(key)) return;
            pending.add(key);
            tileExecutor.submit(() -> {
                try {
                    String url = "https://tile.openstreetmap.org/" + z + "/" + tx + "/" + ty + ".png";
                    java.net.HttpURLConnection conn =
                            (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    conn.setRequestProperty("User-Agent", "SchneeChecker/1.0");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(8000);
                    Image img = javax.imageio.ImageIO.read(conn.getInputStream());
                    if (img != null) {
                        tileCache.put(key, img);
                        SwingUtilities.invokeLater(this::repaint);
                    }
                } catch (Exception ignored) {
                } finally {
                    pending.remove(key);
                }
            });
        }

        private void drawPolygons(Graphics2D g2, int w, int h) {
            if (polygons.isEmpty()) return;

            double xTileCenter = lonToTileX(centerLon, zoom);
            double yTileCenter = latToTileY(centerLat, zoom);

            g2.setStroke(new BasicStroke(0.8f));
            Color fill   = new Color(220, 50, 50, 115);
            Color border = new Color(220, 50, 50, 200);

            for (double[][] polygon : polygons) {
                if (polygon.length < 3) continue;
                Path2D.Double path = new Path2D.Double();
                boolean first = true;
                for (double[] pt : polygon) {
                    double tx = lonToTileX(pt[1], zoom);
                    double ty = latToTileY(pt[0], zoom);
                    int px    = (int) (w / 2.0 + (tx - xTileCenter) * 256);
                    int py    = (int) (h / 2.0 + (ty - yTileCenter) * 256);
                    if (first) { path.moveTo(px, py); first = false; }
                    else         path.lineTo(px, py);
                }
                path.closePath();
                g2.setColor(fill);   g2.fill(path);
                g2.setColor(border); g2.draw(path);
            }
        }

        // ------------------------------------------------------------------
        // Projektion
        // ------------------------------------------------------------------

        private static double lonToTileX(double lon, int zoom) {
            return (lon + 180.0) / 360.0 * (1 << zoom);
        }

        private static double latToTileY(double lat, int zoom) {
            double r = Math.toRadians(lat);
            return (1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * (1 << zoom);
        }

        private static double tile2LonDeg(int zoom) {
            return 360.0 / (1 << zoom);
        }

        private static double tile2LatDeg(double lat, int zoom) {
            return Math.toDegrees(Math.atan(Math.sinh(Math.PI / (1 << zoom)))) * 2;
        }

        /** Konvertiert einen y-Pixel-Wert zur geografischen Breite. */
        private double pixelToLat(int py, int h) {
            double yTileCenter = latToTileY(centerLat, zoom);
            double tileY = yTileCenter + (py - h / 2.0) / 256.0;
            double n = 1 << zoom;
            return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * tileY / n))));
        }
    }
}

