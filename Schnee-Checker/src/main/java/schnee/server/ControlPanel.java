package schnee.server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

//Zeigt die Steuerelemente an und nimmt Eingaben des USers entgegen
public class ControlPanel extends JPanel {

    // Welche Datenart aktuell angezeigt werden soll
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
    private JProgressBar statusProgressBar;
    private JButton updateBtn;
    private Timer progressAnimationTimer;

    private JPanel legendLeg;
    private JPanel legendBox;
    private JLabel legendTxt;

    private Mode currentMode = Mode.ELEVATION;

    public ControlPanel(MapPanel mapPanel, Consumer<Void> onUpdateRequested) {
        this.mapPanel          = mapPanel;
        this.onUpdateRequested = onUpdateRequested;
        build();

        // MapPanel ruft bei Mausrad-Zoom auf, damit der Slider mitzieht
        mapPanel.setOnZoomChanged(z -> SwingUtilities.invokeLater(() -> {
            zoomSlider.setValue(z);
            zoomLabel.setText("Zoom: " + z);
        }));
    }

    public Mode getMode()     { return currentMode; }
    public int  getThreshold() { return thresholdSlider.getValue(); }
    public int  getGridSize()  { return gridSlider.getValue(); }

    public void setStatus(String msg, int targetProgress, Color color) {
        if (targetProgress < 0) {
            statusLabel.setText(msg);
            statusLabel.setForeground(color);
        }

        statusProgressBar.setVisible(targetProgress > 0 && targetProgress < 100);
        animateProgressTo(targetProgress);
    }

    private void animateProgressTo(int target) {
        if (progressAnimationTimer != null && progressAnimationTimer.isRunning()) {
            progressAnimationTimer.stop();
        }

        progressAnimationTimer = new Timer(15, e -> {
            int current = statusProgressBar.getValue();
            int diff    = target - current;

            if (diff == 0) {
                ((Timer) e.getSource()).stop();
                return;
            }

            int step = Math.max(1, Math.abs(diff) / 8);
            int next = current + Integer.signum(diff) * step;
            statusProgressBar.setValue(next);
        });
        progressAnimationTimer.start();
    }

    public void setUpdateEnabled(boolean enabled) {
        updateBtn.setEnabled(enabled);
    }

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

        add(buildModeSwitch());
        add(Box.createVerticalStrut(18));

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
            mapPanel.setView(mapPanel.getCenterLat(), mapPanel.getCenterLon(), z);
        });
        add(zoomSlider);
        add(zoomLabel);
        add(Box.createVerticalStrut(20));

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

        statusLabel = new JLabel("Bereit");
        statusLabel.setForeground(new Color(120, 120, 140));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(statusLabel);
        add(Box.createVerticalStrut(10));

        statusProgressBar = new JProgressBar(0, 100);
        statusProgressBar.setValue(0);
        statusProgressBar.setVisible(false);
        statusProgressBar.setStringPainted(false);
        statusProgressBar.setForeground(new Color(107, 197, 255));
        statusProgressBar.setBackground(new Color(35, 35, 50));
        statusProgressBar.setBorderPainted(false);
        statusProgressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusProgressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        statusProgressBar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 6));
        add(statusProgressBar);

        add(Box.createVerticalGlue());

        legendLeg = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        legendLeg.setBackground(new Color(20, 20, 32));
        legendLeg.setAlignmentX(Component.LEFT_ALIGNMENT);

        legendBox = new JPanel();
        legendBox.setBackground(new Color(220, 50, 50, 140));
        legendBox.setBorder(BorderFactory.createLineBorder(new Color(220, 50, 50)));
        legendBox.setPreferredSize(new Dimension(16, 16));

        legendTxt = new JLabel("Über Schwelle");
        legendTxt.setForeground(new Color(170, 170, 190));
        legendTxt.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        legendLeg.add(legendBox);
        legendLeg.add(legendTxt);
        add(legendLeg);
    }

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

    private void switchMode(Mode mode) {
        if (mode == currentMode) return;
        currentMode = mode;

        int[] range = (mode == Mode.ELEVATION) ? ELEVATION_RANGE : SNOW_DEPTH_RANGE;
        thresholdSlider.setMinimum(range[0]);
        thresholdSlider.setMaximum(range[1]);
        thresholdSlider.setValue(range[2]);
        updateThresholdLabel();
    }

    private void updateThresholdLabel() {
        int value = thresholdSlider.getValue();
        if (currentMode == Mode.ELEVATION) {
            thresholdLabel.setText("Höhenschwelle: " + value + " m");
            legendBox.setBackground(new Color(220, 50, 50, 140));
            legendBox.setBorder(BorderFactory.createLineBorder(new Color(220, 50, 50)));
        } else {
            thresholdLabel.setText("Schneeschwelle: " + value + " cm");
            legendBox.setBackground(new Color(10, 89, 165, 255));
            legendBox.setBorder(BorderFactory.createLineBorder(new Color(102, 155, 236)));
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
}