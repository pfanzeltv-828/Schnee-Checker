package sample3_interaction;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class ElevationPainter implements Painter<Object> {


    @Override
    public void paint(Graphics2D g, JXMapViewer map, int w, int h) {

        Rectangle bounds = map.getViewportBounds();

        // Raster über den sichtbaren Bereich legen (z.B. alle 10px)
        int step = 10;
        for (int px = 0; px < w; px += step) {
            for (int py = 0; py < h; py += step) {

                // Pixel → GeoPosition
                Point2D worldPt = new Point2D.Double(
                    px + bounds.getX(), 
                    py + bounds.getY()
                );
                GeoPosition geo = map.getTileFactory()
                    .pixelToGeo(worldPt, map.getZoom());

                try {
                    double elevation = getElevation(geo.getLatitude(), 
                                                    geo.getLongitude());
                    if (elevation > 1000) {
                        g.setColor(new Color(255, 0, 0, 80)); // rot, transparent
                        g.fillRect(px, py, step, step);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }



    //mapViewer.setOverlayPainter(elevationPainter);



    public static double getElevation(double lat, double lon) throws IOException {
        String urlStr = String.format(
                "https://api.open-meteo.com/v1/elevation?latitude=%s&longitude=%s",
                lat, lon
        );

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            // Response: {"elevation":[731.0]}
            String body = sb.toString();
            String value = body.replaceAll(".*\\[(.*?)\\].*", "$1");
            return Double.parseDouble(value.trim());
        }
    }
    public static void main(String[] args) throws IOException {
        ElevationPainter p1 = new ElevationPainter();
        System.out.println(p1.getElevation(48.348760,11.987477));
    }

}
