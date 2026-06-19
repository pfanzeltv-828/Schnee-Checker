package schnee.server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Sidebar mit Modus-Umschalter (Höhe/Schnee), Slidern (Schwelle,
 * Rasterauflösung, Zoom) und dem "Aktualisieren"-Button.
 *
 * Liest den initialen Zoom vom übergebenen MapPanel, schreibt Zoom-Änderungen
 * dorthin zurück, kennt aber sonst keine Anwendungslogik. Wenn der Button
 * geklickt wird, ruft ControlPanel lediglich den übergebenen Callback auf –
 * was dabei passiert (welche Daten geladen werden) entscheidet der Aufrufer,
 * der über getMode() abfragt, welcher Modus aktuell gewählt ist.
 */
public class ControlPanel extends JPanel {

    /** Welche Datenart aktuell angezeigt werden soll. */
    public enum Mode { ELEVATION, SNOW_DEPTH }

    // Slider-Bereiche je Modus: {min, max, default}
    private static final int[] ELEVATION_RANGE  = {0, 5000, 500};
    private static final int[] SNOW_DEPTH_RANGE = {1, 200, 20};

    private final MapPanel mapPanel;
    private final Consumer<Void> onUpdateRequested;

    private JRadioButton elevationRadio;
    private JRadioButton snowDepthRadio;

    private JSlider thresholdSlider;
    private JSlider gridSlider;
    private JSlider zoomSlider;
    private JLabel  thresholdLabel;
    private JLabel  gridLabel;
    private JLabel  zoomLabel;
    private JLabel  statusLabel;
    private JButton updateBtn;

    private Mode currentMode = Mode.ELEVATION;

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
    // Öffentliche Getter – Aufrufer liest hierüber die aktuellen Werte
    // =========================================================================

    /** Aktuell gewählter Modus – bestimmt, welche Lade-Methode der Aufrufer nutzen sollte. */
    public Mode getMode()     { return currentMode; }
    public int  getThreshold() { return thresholdSlider.getValue(); }
    public int  getGridSize()  { return gridSlider.getValue(); }

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

        JLabel title = new JLabel(" SCHNEE-CHECKER");
        title.setForeground(new Color(255, 100, 100));
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(18));

        // --- Modus-Umschalter ---
        add(buildModeSwitch());
        add(Box.createVerticalStrut(18));

        // --- Schwelle (Höhe oder Schnee, je nach Modus) ---
        thresholdLabel = makeLabel("Höhenschwelle: 500 m");
        add(thresholdLabel);
        add(Box.createVerticalStrut(5));

        thresholdSlider = new JSlider(ELEVATION_RANGE[0], ELEVATION_RANGE[1], ELEVATION_RANGE[2]);
        thresholdSlider.setBackground(new Color(20, 20, 32));
        thresholdSlider.setForeground(new Color(255, 100, 100));
        styleSlider(thresholdSlider);
        thresholdSlider.addChangeListener(e -> updateThresholdLabel());
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

    /** Baut die zwei Radiobuttons für die Modus-Auswahl (Höhe / Schnee). */
    private JPanel buildModeSwitch() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(20, 20, 32));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        elevationRadio = new JRadioButton(" Höhe", true);
        snowDepthRadio = new JRadioButton(" Schnee", false);

        for (JRadioButton rb : new JRadioButton[]{elevationRadio, snowDepthRadio}) {
            rb.setBackground(new Color(20, 20, 32));
            rb.setForeground(new Color(220, 220, 235));
            rb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            rb.setFocusPainted(false);
            rb.setAlignmentX(Component.LEFT_ALIGNMENT);
            rb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        ButtonGroup group = new ButtonGroup();
        group.add(elevationRadio);
        group.add(snowDepthRadio);

        elevationRadio.addActionListener(e -> switchMode(Mode.ELEVATION));
        snowDepthRadio.addActionListener(e -> switchMode(Mode.SNOW_DEPTH));

        panel.add(elevationRadio);
        panel.add(snowDepthRadio);
        return panel;
    }

    /** Wechselt den Modus, passt den Slider-Bereich an und aktualisiert das Label. */
    private void switchMode(Mode mode) {
        if (mode == currentMode) return;
        currentMode = mode;

        int[] range = (mode == Mode.ELEVATION) ? ELEVATION_RANGE : SNOW_DEPTH_RANGE;
        thresholdSlider.setMinimum(range[0]);
        thresholdSlider.setMaximum(range[1]);
        thresholdSlider.setValue(range[2]);
        updateThresholdLabel();
    }

    /** Aktualisiert die Schwellen-Beschriftung passend zum aktuellen Modus (m vs. cm). */
    private void updateThresholdLabel() {
        int value = thresholdSlider.getValue();
        if (currentMode == Mode.ELEVATION) {
            thresholdLabel.setText("Höhenschwelle: " + value + " m");
        } else {
            thresholdLabel.setText("Schneeschwelle: " + value + " cm");
        }
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