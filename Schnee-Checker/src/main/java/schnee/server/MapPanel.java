package schnee.server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

//Zeichnet OpenStreetMap-Kacheln und Höhen-Polygone.
public class MapPanel extends JPanel {

    private double centerLat = 47.098;
    private double centerLon = 12.662;
    private int    zoom      = 14;

    private List<LocalMapServer.PolygonFeature> polygons = new ArrayList<>();
    private boolean mode = false;

    private final Map<String, Image> tileCache = new LinkedHashMap<>(256, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Image> e) {
            return size() > 200;
        }
    };
    private final ExecutorService tileExecutor = Executors.newFixedThreadPool(6);
    private final Set<String>     pending      = Collections.synchronizedSet(new HashSet<>());

    private Point  dragStart    = null;
    private double dragStartLat, dragStartLon;

    private IntConsumer onZoomChanged;

    public MapPanel() {
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
                centerLon = dragStartLon - (dx / 256.0) * tile2LonDeg(zoom);
                centerLat = dragStartLat + (dy / 256.0) * tile2LatDeg(zoom);
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        addMouseWheelListener(e -> {
            zoom = Math.max(3, Math.min(18, zoom - e.getWheelRotation()));
            repaint();
            if (onZoomChanged != null) onZoomChanged.accept(zoom);
        });
    }

    public void setOnZoomChanged(IntConsumer cb) { this.onZoomChanged = cb; }

    public void setView(double lat, double lon, int z) {
        this.centerLat = lat;
        this.centerLon = lon;
        this.zoom      = z;
        repaint();
    }

    public void setPolygons(List<LocalMapServer.PolygonFeature> p, boolean mode) {
        this.polygons      = p;
        this.mode  = mode;
        repaint();
    }

    public int    getZoom()      { return zoom; }
    public double getCenterLat() { return centerLat; }
    public double getCenterLon() { return centerLon; }

    public double[] getBoundingBox() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0) w = 800;
        if (h == 0) h = 600;

        double lonRange = (w / 256.0) * tile2LonDeg(zoom);
        double minLon   = centerLon - lonRange / 2.0;
        double maxLon   = centerLon + lonRange / 2.0;
        double maxLat   = pixelToLat(0, h);
        double minLat   = pixelToLat(h, h);
        return new double[]{minLat, maxLat, minLon, maxLon};
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        drawTiles(g2, w, h);
        drawPolygons(g2, w, h);

        g2.setColor(new Color(255, 255, 255, 100));
        g2.drawLine(w / 2 - 10, h / 2, w / 2 + 10, h / 2);
        g2.drawLine(w / 2, h / 2 - 10, w / 2, h / 2 + 10);

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

        Color ElevationFill   = new Color(220, 50, 50, 115); // feste rote Farbe für Elevation
        Color ElevationBorder = new Color(220, 50, 50, 200);

        Color SnowDepthFill = new Color(20, 130, 230, 115);
        Color SnowDepthBorder = new Color(20, 130, 230, 200);

        for (LocalMapServer.PolygonFeature feature : polygons) {
            double[][] polygon = feature.points();
            if (polygon.length < 3) continue;

            Color fill   = mode ? SnowDepthFill : ElevationFill;
            Color border = mode ? SnowDepthBorder : ElevationBorder;

            Path2D.Double path = new Path2D.Double();
            boolean first = true;
            for (double[] pt : polygon) {
                double tx = lonToTileX(pt[1], zoom);
                double ty = latToTileY(pt[0], zoom);
                int px = (int) (w / 2.0 + (tx - xTileCenter) * 256);
                int py = (int) (h / 2.0 + (ty - yTileCenter) * 256);
                if (first) { path.moveTo(px, py); first = false; }
                else         path.lineTo(px, py);
            }
            path.closePath();
            g2.setColor(fill);
            g2.fill(path);
            g2.setColor(border);
            g2.draw(path);
        }
    }

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

    private static double tile2LatDeg(int zoom) {
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI / (1 << zoom)))) * 2;
    }

    private double pixelToLat(int py, int h) {
        double yTileCenter = latToTileY(centerLat, zoom);
        double tileY = yTileCenter + (py - h / 2.0) / 256.0;
        double n = 1 << zoom;
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * tileY / n))));
    }
}
