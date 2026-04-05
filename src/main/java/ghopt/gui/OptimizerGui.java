package ghopt.gui;

import ghopt.core.io.ChooseChartSource;
import ghopt.core.io.ChartSource;
import ghopt.core.model.ChartData;
import ghopt.core.process.StarPowerOptimizer;
import ghopt.core.render.ChartRenderer;
import ghopt.core.util.TickConverter;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class OptimizerGui extends JFrame {

    private JTextField fileField;
    private JButton browseButton;
    private JButton runButton;
    private JTextArea resultsArea;
    private JLabel imageLabel;
    private JScrollPane imageScroll;
    private JComboBox<String> difficultyBox;

    private BufferedImage chartImage;
    private double zoomLevel = 1.0;
    private static final double ZOOM_STEP = 0.25;
    private static final double ZOOM_MIN  = 0.25;
    private static final double ZOOM_MAX  = 3.0;

    public OptimizerGui() {
        super("Clone Hero Star Power Optimizer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
        buildUi();
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        // ── Top: file chooser ─────────────────────────────────────────────
        JPanel topPanel = new JPanel(new BorderLayout(6, 0));
        fileField    = new JTextField();
        fileField.setEditable(false);
        fileField.setToolTipText("Select a .chart or .mid file");
        browseButton = new JButton("Browse…");
        runButton    = new JButton("Find Optimal SP Path");
        runButton.setEnabled(false);

        browseButton.addActionListener(e -> chooseFile());
        runButton.addActionListener(e -> runOptimizer());

        difficultyBox = new JComboBox<>(new String[]{"Easy", "Medium", "Hard", "Expert"});
        difficultyBox.setSelectedItem("Expert");

        JPanel fileRow = new JPanel(new BorderLayout(4, 0));
        fileRow.add(new JLabel("Chart file: "), BorderLayout.WEST);
        fileRow.add(fileField, BorderLayout.CENTER);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rightControls.add(new JLabel("Difficulty:"));
        rightControls.add(difficultyBox);
        rightControls.add(browseButton);
        fileRow.add(rightControls, BorderLayout.EAST);

        topPanel.add(fileRow, BorderLayout.CENTER);
        topPanel.add(runButton, BorderLayout.EAST);
        root.add(topPanel, BorderLayout.NORTH);

        // ── Center: results text + image ──────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.25);

        resultsArea = new JTextArea(8, 40);
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        resultsArea.setText("Select a .chart or .mid file and click \"Find Optimal SP Path\".");
        split.setTopComponent(new JScrollPane(resultsArea));

        // Image panel with zoom controls
        imageLabel  = new JLabel("Chart will appear here", SwingConstants.CENTER);
        imageLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
        imageLabel.setForeground(Color.GRAY);
        imageScroll = new JScrollPane(imageLabel);

        JButton zoomIn  = new JButton("+ Zoom In");
        JButton zoomOut = new JButton("- Zoom Out");
        JLabel  zoomLbl = new JLabel("100%");
        zoomLbl.setPreferredSize(new Dimension(55, 24));

        zoomIn.addActionListener(e -> {
            if (zoomLevel < ZOOM_MAX) {
                zoomLevel = Math.min(ZOOM_MAX, zoomLevel + ZOOM_STEP);
                zoomLbl.setText((int)(zoomLevel * 100) + "%");
                applyZoom();
            }
        });
        zoomOut.addActionListener(e -> {
            if (zoomLevel > ZOOM_MIN) {
                zoomLevel = Math.max(ZOOM_MIN, zoomLevel - ZOOM_STEP);
                zoomLbl.setText((int)(zoomLevel * 100) + "%");
                applyZoom();
            }
        });

        JPanel zoomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        zoomBar.add(zoomOut);
        zoomBar.add(zoomLbl);
        zoomBar.add(zoomIn);

        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.add(zoomBar,     BorderLayout.NORTH);
        imagePanel.add(imageScroll, BorderLayout.CENTER);
        split.setBottomComponent(imagePanel);

        root.add(split, BorderLayout.CENTER);
    }

    private void applyZoom() {
        if (chartImage == null) return;
        int w = (int)(chartImage.getWidth()  * zoomLevel);
        int h = (int)(chartImage.getHeight() * zoomLevel);
        Image scaled = chartImage.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        imageLabel.setIcon(new ImageIcon(scaled));
        imageLabel.setText(null);
        imageLabel.revalidate();
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select a .chart or .mid file");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Chart / MIDI files", "chart", "mid"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            fileField.setText(fc.getSelectedFile().getAbsolutePath());
            runButton.setEnabled(true);
        }
    }

    private void runOptimizer() {
        String path = fileField.getText().trim();
        if (path.isEmpty()) return;

        runButton.setEnabled(false);
        resultsArea.setText("Running optimizer…");
        imageLabel.setIcon(null);
        imageLabel.setText("Generating chart…");
        chartImage = null;

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            BufferedImage result;
            String resultText;
            String errorText;

            @Override
            protected Void doInBackground() {
                try {
                    File input = new File(path);
                    ChartSource source = ChooseChartSource.choose(input);
                    String difficulty = (String) difficultyBox.getSelectedItem();
                    ChartData data = source.parse(input, difficulty);

                    StarPowerOptimizer.OptimalPath optimalPath =
                            StarPowerOptimizer.findOptimalPath(data);

                    String outPath = path.replaceAll("\\.(chart|mid)$", "_sp_opt.png");
                    ChartRenderer.render(data, optimalPath.activationTimes, outPath);
                    result = ImageIO.read(new File(outPath));

                    StringBuilder sb = new StringBuilder();
                    sb.append("Notes:          ").append(data.getNotes().size()).append("\n");
                    sb.append("SP phrases:     ").append(data.getStarPowerPhrases().size()).append("\n");
                    sb.append("Optimal score:  ").append(optimalPath.totalScore).append("\n\n");
                    sb.append("Activation points (activate SP here):\n");
                    sb.append("─────────────────────────────────────\n");

                    List<Integer> times = optimalPath.activationTimes;
                    if (times.isEmpty()) {
                        sb.append("  No activations found.\n");
                    } else {
                        for (int i = 0; i < times.size(); i++) {
                            int tick    = times.get(i);
                            double secs = TickConverter.tickToSeconds(tick, data);
                            String ts   = TickConverter.formatTimestamp(secs);
                            sb.append(String.format("  Activation %d:  tick %-8d  → %s\n",
                                    i + 1, tick, ts));
                        }
                    }

                    if (data.getTempoEvents().isEmpty()) {
                        sb.append("\n(No tempo map found — timestamps estimated at 120 BPM)");
                    }

                    resultText = sb.toString();

                } catch (Exception ex) {
                    errorText = "Error: " + ex.getMessage();
                    ex.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                if (errorText != null) {
                    resultsArea.setText(errorText);
                    imageLabel.setText("Failed to generate chart.");
                    return;
                }
                resultsArea.setText(resultText);
                if (result != null) {
                    chartImage = result;
                    zoomLevel  = 1.0;
                    applyZoom();
                }
            }
        };

        worker.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OptimizerGui().setVisible(true));
    }
}