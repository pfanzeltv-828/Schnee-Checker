package schnee.server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Sidebar mit Slidern (Höhenschwelle, Rasterauflösung, Zoom) und dem
 * "Aktualisieren"-Button.
 *
 * Liest den initialen Zoom vom übergebenen MapPanel, schreibt Zoom-Änderungen
 * dorthin zurück, kennt aber sonst keine Anwendungslogik. Wenn der Button
 * geklickt wird, ruft ControlPanel lediglich den übergebenen Callback auf –
 * was dabei passiert (Daten laden etc.) entscheidet der Aufrufer.
 */
public class ControlPanel extends JPanel {

    private final MapPanel mapPanel;
    private final Consumer<Void> onUpdateRequested;

    private JSlider thresholdSlider;
    private JSlider gridSlider;
    private JSlider zoomSlider;
    private JLabel  thresholdLabel;
    private JLabel  gridLabel;
    private JLabel  zoomLabel;
    private JLabel  statusLabel;
    private JButton updateBtn;

    /**
     * @param mapPanel          Referenz für Zoom-Initialwert und Synchronisation
     * @param onUpdateRequested wird aufgerufen, wenn der Button geklickt wird
     */
    public ControlPanel(MapPanel mapPanel, Consumer<Void> onUpdateRequested) {
        this.mapPanel          = mapPanel;
        this.onUpdateRequested = onUpdateRequested;
        build();

        // MapPanel ruft dies bei Mausrad-Zoom auf, damit der Slider mitzieht
        mapPanel.setOnZoomChanged(z -> SwingUtilities.invokeLater(() -> {
            zoomSlider.setValue(z);
            zoomLabel.setText("Zoom: " + z);
        }));
    }

    // =========================================================================
    // Öffentliche Getter – Aufrufer liest hierüber die aktuellen Slider-Werte
    // =========================================================================

    public int getThreshold() { return thresholdSlider.getValue(); }
    public int getGridSize()  { return gridSlider.getValue(); }

    public void setStatus(String msg, Color color) {
        statusLabel.setText(msg);
        statusLabel.setForeground(color);
    }

    public void setUpdateEnabled(boolean enabled) {
        updateBtn.setEnabled(enabled);
    }

    // =========================================================================
    // Aufbau
    // =========================================================================

    private void build() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(20, 20, 32));
        setBorder(new EmptyBorder(18, 16, 18, 16));
        setPreferredSize(new Dimension(240, 0));

        JLabel title = new JLabel("⛰ SCHNEE-CHECKER");
        title.setForeground(new Color(255, 100, 100));
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(18));

        // --- Höhenschwelle ---
        thresholdLabel = makeLabel("Höhenschwelle: 500 m");
        add(thresholdLabel);
        add(Box.createVerticalStrut(5));

        thresholdSlider = new JSlider(0, 3000, 500);
        thresholdSlider.setBackground(new Color(20, 20, 32));
        thresholdSlider.setForeground(new Color(255, 100, 100));
        styleSlider(thresholdSlider);
        thresholdSlider.addChangeListener(e ->
            thresholdLabel.setText("Höhenschwelle: " + thresholdSlider.getValue() + " m"));
        add(thresholdSlider);
        add(Box.createVerticalStrut(16));

        // --- Rasterauflösung ---
        gridLabel = makeLabel("Rasterauflösung: 10 × 10");
        add(gridLabel);
        add(Box.createVerticalStrut(5));

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
        add(gridSlider);
        add(Box.createVerticalStrut(20));

        // --- Zoom-Slider (synchronisiert mit Mausrad) ---
        add(makeLabel("Zoom-Stufe:"));
        add(Box.createVerticalStrut(4));

        int initialZoom = mapPanel.getZoom();
        zoomLabel  = makeLabel("Zoom: " + initialZoom);
        zoomSlider = new JSlider(3, 18, initialZoom);
        zoomSlider.setBackground(new Color(20, 20, 32));
        zoomSlider.setForeground(new Color(107, 255, 155));
        styleSlider(zoomSlider);
        zoomSlider.addChangeListener(e -> {
            int z = zoomSlider.getValue();
            zoomLabel.setText("Zoom: " + z);
            // Mittelpunkt vom Panel lesen, nicht aus eigenen Feldern!
            mapPanel.setView(mapPanel.getCenterLat(), mapPanel.getCenterLon(), z);
        });
        add(zoomSlider);
        add(zoomLabel);
        add(Box.createVerticalStrut(20));

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
        updateBtn.addActionListener(e -> onUpdateRequested.accept(null));
        add(updateBtn);
        add(Box.createVerticalStrut(10));

        // --- Status ---
        statusLabel = new JLabel("Bereit");
        statusLabel.setForeground(new Color(120, 120, 140));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(statusLabel);

        add(Box.createVerticalGlue());
        add(buildLegend());
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
}
