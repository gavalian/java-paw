package org.jlab.paw;

import j4np.hipo5.data.Schema;
import twig.data.*;
import twig.graphics.TGDataCanvas;
import twig.widgets.PaveText;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;

public final class Workstation extends JPanel implements Commands.View {
    private final Workspace workspace = new Workspace();
    private final JPanel preview = new JPanel(null);
    private final boolean previewMode;
    private final TGDataCanvas canvas = new TGDataCanvas();
    private final JTextArea transcript = new JTextArea();
    private final JTextArea input = new JTextArea(3, 30);
    private final JLabel cwd = new JLabel("//PAWC");
    private final JLabel status = new JLabel("Ready  |  HIPO storage  |  Twig graphics");
    private final JCheckBox transcriptHold = new JCheckBox("H");
    private final JCheckBox inputHold = new JCheckBox("H");
    private final JCheckBox statistics = new JCheckBox("Statistics", true);
    private final JCheckBox overlay = new JCheckBox("Superimpose");
    private final JCheckBox logY = new JCheckBox("Log Y");
    private final JComboBox<String> plotMode = new JComboBox<>(new String[]{"Default", "Line", "Errors", "Filled"});
    private final JSpinner zoneSelector = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
    private final DefaultListModel<String> objectModel = new DefaultListModel<>();
    private final JList<String> objects = new JList<>(objectModel);
    private final JList<String> classes = new JList<>(new String[]{"Commands", "Files", "Macro", "HIPO", "PAWC", "1d Histograms", "2d Histograms", "Ntuples"});
    private final JLabel browserPath = new JLabel("Path: //PAWC");
    private final JLabel browserInfo = new JLabel(" ");
    private final Path launchDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    private Path macroDirectory = launchDirectory;
    private final Map<String, Path> macroEntries = new LinkedHashMap<>();
    private final JButton browserAction = new JButton("Plot");
    private final Map<String, DataSet> browserObjects = new LinkedHashMap<>();
    private final Map<String, Schema> banks = new LinkedHashMap<>();
    private final Map<String,NtupleStore.Tuple> browserTuples=new LinkedHashMap<>();
    private final List<WorkstationWindow> panels = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private final Map<Integer, List<DataSet>> displayed = new HashMap<>();
    private int historyIndex, activeRegion, contentRegion;
    private boolean modern, busy, updatingZoneSelector;
    private Path dataFile;
    private SwingWorker<?, ?> worker;
    private volatile Thread operationThread;
    private final Commands commands;
    private boolean shuttingDown;
    private final WorkstationWindow executive, browser, graphics, style;
    @FunctionalInterface private interface Task { void run() throws Exception; }

    public Workstation(boolean modern, boolean demo) { this(modern, demo, GraphicsEnvironment.isHeadless()); }
    public Workstation(boolean modern, boolean demo, boolean previewOnly) {
        super(new BorderLayout()); this.modern = modern; previewMode = previewOnly || GraphicsEnvironment.isHeadless();
        status.setName("operationStatus");
        commands = new Commands(workspace, this, this::log);
        setPreferredSize(new Dimension(1380, 940));
        if (previewMode) add(preview, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout()); footer.setBorder(new EmptyBorder(4, 8, 4, 8)); footer.add(status);
        JButton cancel = button("Cancel operation", () -> { Thread thread = operationThread; if (thread != null) { thread.interrupt(); status.setText("Cancellation requested; waiting for operation to stop..."); } }); footer.add(cancel, BorderLayout.EAST);
        JPanel executiveContent = executive(); executiveContent.add(footer, BorderLayout.SOUTH);
        executive = panel("Paw++ Executive Window", executiveContent, false);
        executive.onClosing(this::shutdown);
        browser = panel("Paw++ Main Browser", browser(), false);
        graphics = panel("Paw++ Graphics 1", graphics(), false);
        style = panel("Histogram Style Panel", style(), false);
        executive.setJMenuBar(executiveMenu()); browser.setJMenuBar(browserMenu()); style.setJMenuBar(styleMenu());
        if (previewMode) preview.addComponentListener(new ComponentAdapter() { @Override public void componentResized(ComponentEvent event) { tile(); } });
        log("PAW++ Java 0.1.0\nHIPO / Twig analysis workstation\nType HELP for commands. Double-click an object to plot or open its Ntuple Viewer.");
        if (demo) { workspace.demo(); plotNow(workspace.get("10"), false); }
        refresh();
    }
    private WorkstationWindow panel(String title, JComponent content, boolean closable) {
        final WorkstationWindow[] reference = new WorkstationWindow[1];
        WorkstationWindow window = new WorkstationWindow(title, content, closable, previewMode, () -> panels.remove(reference[0]));
        reference[0] = window;
        panels.add(window);
        if (previewMode) preview.add(window);
        else window.setWindowBounds(utilityBounds(80, 80, 640, 480));
        window.applyTheme(modern);
        return window;
    }
    /** Launch the four independent application windows after construction and layout. */
    public void showWindows() {
        if (previewMode) throw new IllegalStateException("Use snapshot mode in a headless environment");
        tile();
        for (WorkstationWindow window : panels) window.toFront();
        executive.toFront();
    }
    List<WorkstationWindow> windows() { return List.copyOf(panels); }
    private Component dialogParent() { return executive.frame() == null ? this : executive.frame(); }
    private Rectangle screenArea() {
        GraphicsConfiguration configuration = executive == null ? GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration() : executive.frame().getGraphicsConfiguration();
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
                bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
    }
    private Rectangle utilityBounds(int x, int y, int width, int height) {
        if (previewMode) return new Rectangle(x, y, width, height);
        Rectangle area = screenArea(); int w = Math.min(width, area.width), h = Math.min(height, area.height);
        return new Rectangle(area.x + Math.min(x, Math.max(0, area.width - w)),
                area.y + Math.min(y, Math.max(0, area.height - h)), w, h);
    }
    private void locate(WorkstationWindow window, int x, int y, int width, int height) {
        window.setWindowBounds(utilityBounds(x, y, width, height)); window.toFront();
    }
    public void tile() {
        if (executive == null) return;
        Rectangle area = previewMode
                ? new Rectangle(0, 0, preview.getWidth(), preview.getHeight()) : screenArea();
        int width = area.width, height = area.height; if (width < 100 || height < 100) return;
        int gap = 8, left = (int) (width * .43), execHeight = (int) (height * .46), graphicsHeight = (int) (height * .63);
        executive.setWindowBounds(area.x + gap, area.y + gap, left - gap * 2, execHeight - gap);
        browser.setWindowBounds(area.x + gap, area.y + execHeight + gap, left - gap * 2, height - execHeight - gap * 2);
        graphics.setWindowBounds(area.x + left, area.y + gap, width - left - gap, graphicsHeight - gap);
        style.setWindowBounds(area.x + left, area.y + graphicsHeight + gap, width - left - gap, height - graphicsHeight - gap * 2);
    }

    private JPanel executive() {
        transcript.setEditable(false); transcript.setFont(new Font("Monospaced", Font.PLAIN, 12)); input.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JPanel result = new JPanel(new BorderLayout(4, 4)); result.setBorder(new EmptyBorder(4, 4, 4, 4));
        JPanel top = new JPanel(new BorderLayout()); JPanel label = new JPanel(new BorderLayout()); label.add(new JLabel("Transcript Pad")); label.add(transcriptHold, BorderLayout.EAST);
        top.add(label, BorderLayout.NORTH); top.add(new JScrollPane(transcript));
        JPanel bottom = new JPanel(new BorderLayout(3, 3)); JPanel inputLabel = new JPanel(new BorderLayout()); inputLabel.add(new JLabel("Input Pad"));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); right.add(cwd); right.add(inputHold); inputLabel.add(right, BorderLayout.EAST);
        bottom.add(inputLabel, BorderLayout.NORTH); bottom.add(new JScrollPane(input));
        bottom.add(button("Execute", this::submitInput), BorderLayout.EAST);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom); split.setResizeWeight(.8); result.add(split);
        input.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "execute"); input.getActionMap().put("execute", new AbstractAction() { public void actionPerformed(ActionEvent event) { if (inputHold.isSelected()) input.replaceSelection("\n"); else submitInput(); } });
        input.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
        input.getInputMap().put(KeyStroke.getKeyStroke("UP"), "previous"); input.getActionMap().put("previous", new AbstractAction() { public void actionPerformed(ActionEvent event) { if (!history.isEmpty()) { historyIndex = Math.max(0, historyIndex - 1); input.setText(history.get(historyIndex)); } } });
        input.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "next"); input.getActionMap().put("next", new AbstractAction() { public void actionPerformed(ActionEvent event) { historyIndex = Math.min(history.size(), historyIndex + 1); input.setText(historyIndex == history.size() ? "" : history.get(historyIndex)); } });
        inputHold.addActionListener(event -> { if (!inputHold.isSelected() && !input.getText().isBlank()) submitInput(); });
        return result;
    }
    private void submitInput() {
        if (busy || input.getText().isBlank()) return;
        String text = input.getText().strip(); history.add(text); historyIndex = history.size(); input.setText("");
        run("Commands", () -> { for (String line : text.split("\\R")) { if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException(); commands.executeInteractive(line); } });
    }
    private JMenuBar executiveMenu() {
        JMenuBar bar = new JMenuBar();
        bar.add(menu("File", item("Open Workspace...", this::openWorkspace), item("Save Workspace...", this::saveWorkspace), item("Open HIPO Data...", this::openData), item("Run Macro...", this::macro), item("Save Transcript...", this::saveTranscript), item("Quit", this::shutdown)));
        bar.add(menu("Edit", item("Copy", transcript::copy), item("Paste to Input", input::paste), item("Search...", this::searchTranscript)));
        bar.add(menu("View", item("Main Browser", browser::toFront), item("Graphics Window", graphics::toFront), item("Ntuple Viewer", this::selectedNtuple), item("Histogram Style Panel", style::toFront), item("Command Panel", this::commandPanel), item("Restore Window Layout", () -> { tile(); for (WorkstationWindow window : List.of(executive, browser, graphics, style)) window.toFront(); })));
        JMenu options = menu("Options", item("Clear Transcript Pad", () -> transcript.setText("")), item("Load Demo", () -> execute("DEMO")));
        JMenu themes = new JMenu("Interface"); ButtonGroup group = new ButtonGroup();
        for (boolean mode : new boolean[]{false, true}) { JRadioButtonMenuItem option = new JRadioButtonMenuItem(mode ? "Modern" : "Classic Motif", mode == modern); group.add(option); themes.add(option); option.addActionListener(event -> changeTheme(mode)); }
        options.add(themes); bar.add(options); bar.add(commandHelpMenu()); bar.add(menu("Help", item("Command Reference", this::help), item("Original PAW Manual", this::manual), item("About PAW++ Java", this::about))); return bar;
    }
    private JPanel browser() {
        JPanel result = new JPanel(new BorderLayout(4, 4)); result.setBorder(new EmptyBorder(4, 4, 4, 4)); result.add(browserPath, BorderLayout.NORTH);
        classes.setName("browserClasses"); objects.setName("browserObjects"); browserPath.setName("browserPath");
        classes.setSelectedValue("PAWC", true); classes.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) renderBrowser(); });
        objects.setLayoutOrientation(JList.HORIZONTAL_WRAP); objects.setVisibleRowCount(-1); objects.setFixedCellWidth(116); objects.setFixedCellHeight(82); objects.setCellRenderer(new ObjectRenderer());
        objects.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                int index = objects.locationToIndex(event.getPoint());
                if (index >= 0 && objects.getCellBounds(index, index).contains(event.getPoint())) { objects.setSelectedIndex(index); activateObject(); }
            } }
            @Override public void mousePressed(MouseEvent event) { popup(event); }
            @Override public void mouseReleased(MouseEvent event) { popup(event); }
            private void popup(MouseEvent event) { if (event.isPopupTrigger()) { int i = objects.locationToIndex(event.getPoint()); if (i < 0 || !objects.getCellBounds(i, i).contains(event.getPoint())) return; objects.setSelectedIndex(i); objectPopup().show(objects, event.getX(), event.getY()); } }
        });
        objects.addListSelectionListener(event -> { if (!event.getValueIsAdjusting() && objects.getSelectedValue() != null) browserInfo.setText(objects.getSelectedValue()); });
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(classes), new JScrollPane(objects)); split.setDividerLocation(128); result.add(split);
        JPanel bottom = new JPanel(new BorderLayout()); bottom.add(browserInfo); JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0)); buttons.add(button("Clone", this::cloneBrowser)); browserAction.addActionListener(event -> activateObject()); buttons.add(browserAction); bottom.add(buttons, BorderLayout.EAST); result.add(bottom, BorderLayout.SOUTH); return result;
    }
    private JMenuBar browserMenu() {
        JMenuBar bar = new JMenuBar();
        bar.add(menu("File", item("Open HIPO Data...", this::openData), item("Open Workspace...", this::openWorkspace), item("Save Workspace...", this::saveWorkspace)));
        bar.add(menu("View", item("All Memory Objects", () -> classes.setSelectedValue("PAWC", true)), item("Ntuples", () -> classes.setSelectedValue("Ntuples", true)), item("Change Directory...", this::changeDirectory)));
        bar.add(menu("Options", item("Create 1d Histogram...", () -> book(false)), item("Create 2d Histogram...", () -> book(true))));
        bar.add(commandHelpMenu());
        bar.add(menu("Help", item("Browser Help", this::help))); return bar;
    }
    private JPanel graphics() {
        JPanel result = new JPanel(new BorderLayout()); result.setBorder(new EmptyBorder(6, 6, 6, 6)); result.add(canvas);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tools.add(button("1 zone", () -> execute("ZONE 1 1"))); tools.add(button("4 zones", () -> execute("ZONE 2 2"))); tools.add(new JLabel("Active zone:"));
        zoneSelector.setName("activeZone"); zoneSelector.addChangeListener(event -> { if (!updatingZoneSelector) try { zone((Integer) zoneSelector.getValue() - 1); } catch (RuntimeException ex) { log(ex.getMessage()); } }); tools.add(zoneSelector);
        tools.add(button("Export...", this::exportChooser)); result.add(tools, BorderLayout.SOUTH);
        return result;
    }
    private JPanel style() {
        JPanel result = new JPanel(new BorderLayout(6, 6)); result.setBorder(new EmptyBorder(6, 6, 6, 6)); result.add(new JLabel("Current Style: Default"), BorderLayout.NORTH);
        JPanel info = new JPanel(new GridLayout(0, 1)); info.setBorder(BorderFactory.createTitledBorder("Plot Info")); info.add(statistics); info.add(overlay); info.add(logY);
        JPanel attributes = new JPanel(new GridLayout(0, 2, 8, 6)); attributes.setBorder(BorderFactory.createTitledBorder("Style"));
        attributes.add(button("Object Attributes...", this::attributes)); attributes.add(button("Axis Settings...", this::axisTitles));
        attributes.add(button("Axis Scaling...", this::axisScaling)); attributes.add(button("Zones...", () -> promptCommand("ZONE", "columns rows", "2 2")));
        attributes.add(button("Rebin...", this::rebin)); attributes.add(button("Fit...", this::fitDialog));
        JPanel center = new JPanel(new BorderLayout(8, 8)); center.add(info, BorderLayout.WEST); center.add(attributes); JPanel modes = new JPanel(new FlowLayout(FlowLayout.LEFT)); modes.add(new JLabel("Plot Options:")); modes.add(plotMode); modes.add(new JLabel("Cartesian")); center.add(modes, BorderLayout.SOUTH); result.add(center);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0)); actions.add(button("Plot", this::plotSelected)); actions.add(button("Reset", () -> { plotMode.setSelectedIndex(0); statistics.setSelected(true); overlay.setSelected(false); logY.setSelected(false); plotSelected(); })); actions.add(button("Export...", this::exportChooser)); result.add(actions, BorderLayout.SOUTH); return result;
    }
    private JMenuBar styleMenu() { JMenuBar bar = new JMenuBar(); bar.add(menu("File", item("Export Graphics...", this::exportChooser))); bar.add(menu("Options", item("Plot", this::plotSelected), item("Gaussian Fit", () -> selectedCommand("H/FIT", "G")))); return bar; }
    private void changeTheme(boolean mode) {
        applyTheme(mode);
        try { java.util.prefs.Preferences.userNodeForPackage(Main.class).putBoolean("modern", mode); } catch (SecurityException ex) { log("Theme changed; preference could not be saved."); }
    }
    void applyTheme(boolean mode) {
        modern = mode; Theme.install(mode); SwingUtilities.updateComponentTreeUI(this);
        for (WorkstationWindow window : panels) window.applyTheme(mode);
        executive.setJMenuBar(executiveMenu()); preview.setBackground(mode ? new Color(229, 234, 240) : Theme.CREAM); revalidate(); repaint();
    }

    private void refresh() {
        cwd.setText(workspace.ntuples().directory());
        browserTuples.clear();browserTuples.putAll(workspace.ntuples().tuples());
        browserObjects.clear(); browserObjects.putAll(workspace.objects()); renderBrowser();
    }
    private void renderBrowser() {
        String selected = objects.getSelectedValue(); objectModel.clear(); String category = classes.getSelectedValue();
        if (category == null) return;
        browserPath.setText("Path: " + (category.equals("Macro") ? macroDirectory : workspace.directory()));
        browserPath.setToolTipText(browserPath.getText());
        browserAction.setText(category.equals("Macro") ? "Open / Run" : "Plot");
        macroEntries.clear();
        if (category.equals("Macro")) {
            renderMacroDirectory();
            if (selected != null) objects.setSelectedValue(selected, true);
            if (objects.getSelectedIndex() < 0 && !objectModel.isEmpty()) objects.setSelectedIndex(0);
            return;
        }
        if (category.equals("Ntuples") || category.equals("HIPO")) for (String bank : browserTuples.keySet()) objectModel.addElement("Ntuple: " + bank);
        else if (category.equals("Commands")) for (String command : new String[]{"HELP", "H/LIST", "DEMO"}) objectModel.addElement(command);
        else if (category.equals("Files")) {
            if (dataFile != null) objectModel.addElement("Ntuple: " + browserTuples.keySet().stream().findFirst().orElse(""));
            browserInfo.setText("Use File to open HIPO data or run a macro.");
        } else {
            if (category.equals("PAWC")) {
                Set<String> directories = new TreeSet<>(); String prefix = workspace.directory() + "/";
                browserObjects.keySet().forEach(path -> { if (path.startsWith(prefix)) { String rest = path.substring(prefix.length()); int slash = rest.indexOf('/'); if (slash >= 0) directories.add("Directory: " + prefix + rest.substring(0, slash)); } });
                if (!workspace.directory().equals("//PAWC")) objectModel.addElement("Directory: " + workspace.directory().substring(0, workspace.directory().lastIndexOf('/')));
                directories.forEach(objectModel::addElement);
            }
            browserObjects.forEach((path, ds) -> { if (path.substring(0, path.lastIndexOf('/')).equals(workspace.directory()) && (!category.equals("1d Histograms") || ds instanceof H1F) && (!category.equals("2d Histograms") || ds instanceof H2F)) objectModel.addElement(path); });
        }
        if (selected != null) objects.setSelectedValue(selected, true);
        if (objects.getSelectedIndex() < 0 && !objectModel.isEmpty()) objects.setSelectedIndex(0);
        browserInfo.setText("Objects: " + objectModel.size() + (dataFile == null ? "" : "   File: " + dataFile.getFileName()));
    }
    private void renderMacroDirectory() {
        if (macroDirectory.getParent() != null) macroEntries.put("../", macroDirectory.getParent());
        try (var files = Files.list(macroDirectory)) {
            files.filter(path -> Files.isDirectory(path) || isMacro(path))
                    .sorted(Comparator.<Path, Boolean>comparing(path -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(path -> macroEntries.put(path.getFileName() + (Files.isDirectory(path) ? "/" : ""), path));
            macroEntries.keySet().forEach(objectModel::addElement);
            browserInfo.setText("Double-click a folder to browse or a macro to run.");
        } catch (java.io.IOException | SecurityException ex) {
            macroEntries.keySet().forEach(objectModel::addElement);
            browserInfo.setText("Cannot browse directory: " + ex.getMessage());
        }
    }
    private static boolean isMacro(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && (name.endsWith(".kumac") || name.endsWith(".jmacro"));
    }
    private void activateObject() {
        String selected = objects.getSelectedValue(); if (selected == null) return;
        if ("Macro".equals(classes.getSelectedValue())) {
            Path entry = macroEntries.get(selected); if (entry == null) return;
            if (Files.isDirectory(entry)) { macroDirectory = entry; objects.clearSelection(); renderBrowser(); }
            else if (isMacro(entry)) execute("MACRO/EXEC " + Commands.quote(entry.toString()));
            else renderBrowser();
            return;
        }
        if (selected.startsWith("Directory: ")) execute("CDIR " + Commands.quote(selected.substring(11)));
        else if (selected.startsWith("Ntuple: ")) openNtuple(browserTuples.get(selected.substring(8)));
        else if (browserObjects.containsKey(selected)) plotSelected(); else execute(selected);
    }
    private JPopupMenu objectPopup() {
        JPopupMenu popup = new JPopupMenu(); String selected = objects.getSelectedValue();
        if ("Macro".equals(classes.getSelectedValue())) {
            Path entry = macroEntries.get(selected);
            if (entry != null) popup.add(item(Files.isDirectory(entry) ? "Open Folder" : "Run Macro", this::activateObject));
            return popup;
        }
        if (selected != null && selected.startsWith("Directory: ")) { popup.add(item("Change Directory", this::activateObject)); return popup; }
        if (selected != null && selected.startsWith("Ntuple: ")) { popup.add(item("Ntuple Viewer", this::selectedNtuple)); return popup; }
        popup.add(item("Plot", this::plotSelected)); popup.add(item("Plot Superimposed", () -> selectedCommand("H/PLOT", "S")));
        popup.add(item("Fit Gauss", () -> selectedCommand("H/FIT", "G"))); popup.add(item("Fit Exp", () -> selectedCommand("H/FIT", "E"))); popup.add(item("Fit Linear", () -> selectedCommand("H/FIT", "P1")));
        popup.add(item("Smooth...", () -> { String id = selectedId(); if (id != null) { String dest = JOptionPane.showInputDialog(dialogParent(), "Output identifier", "smooth"); if (dest != null) execute("HISTOGRAM/SMOOTH " + Commands.quote(id) + " " + Commands.quote(dest)); } }));
        popup.add(item("Rebin...", this::rebin)); popup.add(item("Copy...", () -> { String id = selectedId(); if (id != null) { String dest = JOptionPane.showInputDialog(dialogParent(), "Output identifier", "copy"); if (dest != null) execute("H/COPY " + Commands.quote(id) + " " + Commands.quote(dest)); } }));
        popup.add(item("Statistics", () -> selectedCommand("HISTOGRAM/SHOW", ""))); popup.add(item("Reset", () -> selectedCommand("H/RESET", ""))); popup.add(item("Delete", () -> selectedCommand("H/DELETE", ""))); return popup;
    }
    private String selectedId() {
        String id = objects.getSelectedValue(); if (id != null && browserObjects.containsKey(id)) return id;
        List<DataSet> datasets = displayed.getOrDefault(contentRegion, List.of());
        if (!datasets.isEmpty()) for (var entry : browserObjects.entrySet()) if (entry.getValue() == datasets.get(0) || entry.getKey().equals(workspace.path(datasets.get(0).getName()))) return entry.getKey();
        log("Select a histogram in the Main Browser."); return null;
    }
    private void selectedCommand(String op, String suffix) { String id = selectedId(); if (id != null) execute(op + " " + Commands.quote(id) + (suffix.isBlank() ? "" : " " + suffix)); }
    private void plotSelected() { selectedCommand("H/PLOT", overlay.isSelected() ? "S" : ""); }
    public void execute(String text) { run("Command", () -> commands.executeInteractive(text)); }
    void previewCommand(String text) throws Exception { commands.execute(text); refresh(); }
    private void run(String label, Task task) {
        if (busy) { log("An operation is running. Cancel it or wait for completion."); return; }
        busy = true; status.setText(label + "... ");
        worker = new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { operationThread = Thread.currentThread(); try { task.run(); return null; } finally { operationThread = null; } }
            @Override protected void done() {
                try { get(); status.setText("Ready  |  " + label + " complete"); } catch (Exception ex) { Throwable cause = ex.getCause() == null ? ex : ex.getCause(); log("Error: " + cause.getMessage()); status.setText("Operation ended: " + cause.getClass().getSimpleName()); }
                busy = false; refresh();
            }
        }; worker.execute();
    }
    private void log(String text) { if (SwingUtilities.isEventDispatchThread()) { transcript.append(text + "\n"); if (!transcriptHold.isSelected()) transcript.setCaretPosition(transcript.getDocument().getLength()); } else SwingUtilities.invokeLater(() -> log(text)); }
    private static void edt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run(); else try { SwingUtilities.invokeAndWait(action); } catch (Exception ex) { throw new IllegalStateException(ex.getCause() == null ? ex : ex.getCause()); }
    }
    private void showGraphicsIfHidden() {
        if (!previewMode && !graphics.frame().isVisible()) graphics.toFront();
    }
    @Override public void plot(DataSet ds, boolean same) { edt(() -> { plotNow(ds, same); showGraphicsIfHidden(); }); }
    private void plotNow(DataSet ds, boolean same) {
        String options = ds instanceof H1F ? switch (plotMode.getSelectedIndex()) { case 1 -> "F"; case 2 -> "EP"; case 3 -> "A"; default -> ""; } : ds instanceof GraphErrors ? ds.attr().getDrawOptions() : "";
        int region = same ? contentRegion : activeRegion;
        if (!same) displayed.put(region, new ArrayList<>());
        displayed.computeIfAbsent(region, key -> new ArrayList<>()).add(ds);
        canvas.region(region).drawFrame(true);
        canvas.region(region).draw(ds, (same ? "same" : "") + options);
        canvas.region(region).getAxisFrame().setLogY(logY.isSelected());
        if (!same && statistics.isSelected() && ds instanceof H1F h) {
            PaveText box = new PaveText(List.of("ID  " + h.getName(), "Entries  " + h.getEntries(), String.format(Locale.ROOT, "Mean  %.4g", h.getMean()), String.format(Locale.ROOT, "RMS  %.4g", h.getRMS())), .98, .98);
            box.setFont(new Font("SansSerif",Font.PLAIN,canvas.count()>1?11:13));
            box.setNDF(true).setDrawBox(true).setAlign(twig.widgets.LatexText.TextAlign.TOP_RIGHT); canvas.region(region).draw(box);
        }
        finishDrawing(region, same);
        canvas.repaint();
    }
    private void finishDrawing(int region, boolean same) {
        contentRegion = region;
        if (!same) {
            canvas.cd(region).next();
            activeRegion = (region + 1) % canvas.count();
            updateZoneSelector();
        }
    }
    private void updateZoneSelector() {
        updatingZoneSelector = true;
        try {
            SpinnerNumberModel model = (SpinnerNumberModel) zoneSelector.getModel();
            model.setMaximum(canvas.count()); model.setValue(activeRegion + 1);
        } finally { updatingZoneSelector = false; }
    }
    @Override public void waitBetweenPages(double seconds) {
        if (previewMode) return;
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Page delays must run on the analysis worker");
        edt(() -> {
            showGraphicsIfHidden(); canvas.paintImmediately(0, 0, canvas.getWidth(), canvas.getHeight());
            status.setText("Next screen in " + seconds + " seconds...");
        });
        try { java.util.concurrent.TimeUnit.NANOSECONDS.sleep((long) Math.ceil(seconds * 1_000_000_000d)); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new java.util.concurrent.CancellationException("Page delay cancelled");
        }
    }
    @Override public List<String> requestArguments(CommandArguments.Spec spec, List<String> supplied) throws Exception {
        if (previewMode) return supplied;
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Command input must run on the analysis worker");
        CommandArgumentPrompt[] request = new CommandArgumentPrompt[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                if (shuttingDown) return;
                request[0] = new CommandArgumentPrompt(spec, supplied, this::showCommandHelp);
                request[0].show(executive.frame()); status.setText("Waiting for command arguments...");
            });
            if (request[0] == null) throw new java.util.concurrent.CancellationException("Workstation closed");
            return request[0].answer().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new java.util.concurrent.CancellationException("Command cancelled");
        } finally {
            SwingUtilities.invokeLater(() -> { if (request[0] != null) request[0].close(); });
        }
    }
    @Override public String readInput(String prompt, String defaultValue) throws Exception {
        if (previewMode) return defaultValue;
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Macro input must run on the analysis worker");
        MacroInputPrompt[] request = new MacroInputPrompt[1];
        try {
            // Queue display after previous plotting actions, with the canvas painted before asking.
            SwingUtilities.invokeAndWait(() -> {
                if (shuttingDown) return;
                showGraphicsIfHidden(); canvas.paintImmediately(0, 0, canvas.getWidth(), canvas.getHeight());
                request[0] = new MacroInputPrompt(prompt, defaultValue);
                request[0].show(graphics.frame()); status.setText("Waiting for macro input...");
            });
            if (request[0] == null) throw new java.util.concurrent.CancellationException("Workstation closed");
            return request[0].answer().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new java.util.concurrent.CancellationException("Macro input cancelled");
        } finally {
            SwingUtilities.invokeLater(() -> {
                if (request[0] != null) request[0].close();
                if (!shuttingDown) status.setText("Running macro...");
            });
        }
    }
    @Override public List<double[]> graphicalCut(String id,List<String> columns,List<double[]> points)throws Exception {
        if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Graphical cuts must run on the analysis worker");
        GraphicalCutPrompt[]prompt=new GraphicalCutPrompt[1];
        try{SwingUtilities.invokeAndWait(()->{prompt[0]=new GraphicalCutPrompt(id,columns,points);prompt[0].show(graphics.frame(),id);});return prompt[0].answer.get();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException("Graphical cut cancelled");}
        finally{SwingUtilities.invokeLater(()->{if(prompt[0]!=null)prompt[0].close();});}
    }
    @Override public void editFile(Path file){edt(()->{try{JTextArea text=new JTextArea(Files.readString(file),24,80);JPanel body=new JPanel(new BorderLayout());body.add(new JScrollPane(text));body.add(button("Save",()->run("Save callback",()->Files.writeString(file,text.getText()))),BorderLayout.SOUTH);WorkstationWindow editor=panel("Callback: "+file.getFileName(),body,true);locate(editor,90,70,850,550);}catch(Exception e){log(e.getMessage());}});}
    private DataSet viewerProjection(NtupleStore.Tuple tuple,String id,String x,String y,String selection,int bins,double low,double high,int ybins,double ylow,double yhigh,long first,long count)throws Exception{
        Workspace.checkAxis(bins,low,high);if(!y.isBlank())Workspace.checkAxis(ybins,ylow,yhigh);
        DataSet ds=y.isBlank()?Workspace.book1(id,tuple.title,bins,low,high):new H2F(id,bins,low,high,ybins,ylow,yhigh);
        try(var q=commands.ntuples.new Query(new NtupleCommands.Selector(tuple,y.isBlank()?List.of(x):List.of(x,y),List.of()),selection.isBlank()?"1.":selection,null,0)){
            tuple.rows(first,count,(i,row)->{double w=q.weight(i,row);if(w==0)return;double[]v=q.values(row);if(Arrays.stream(v).anyMatch(n->!Double.isFinite(n)))return;if(ds instanceof H1F h)h.fill(v[0],w);else((H2F)ds).fill(v[0],v[1],w);});
        }ds.attr().setTitleX(x);ds.attr().setTitleY(y.isBlank()?"Counts":y);return ds;
    }
    @Override public java.awt.image.BufferedImage capture() { final java.awt.image.BufferedImage[] image=new java.awt.image.BufferedImage[1]; edt(() -> image[0]=canvas.getScreenShot()); return image[0]; }
    @Override public void ntuple(Path path, List<Schema> schemas) { edt(() -> { dataFile=path; banks.clear(); for(Schema schema:schemas) banks.put(schema.getName(),schema); }); }
    @Override public void axisLabels(boolean show){configure(region->{for(var axis:List.of(region.getAxisFrame().getAxisX(),region.getAxisFrame().getAxisY())){axis.getAttributes().setAxisLabelsDraw(show);axis.getAttributes().setAxisTicksDraw(show);}});}
    @Override public void updatePlot(DataSet ds,String options){edt(()->{displayed.put(contentRegion,new ArrayList<>(List.of(ds)));canvas.region(contentRegion).drawFrame(true).draw(ds,options);canvas.repaint();});}
    @Override public void draw(DataSet ds, String options, boolean same) {
        edt(() -> {
            int region = same ? contentRegion : activeRegion;
            if(!same) displayed.put(region,new ArrayList<>());
            displayed.computeIfAbsent(region,k->new ArrayList<>()).add(ds);
            canvas.region(region).drawFrame(true).draw(ds,(same?"same":"")+options);
            finishDrawing(region, same);
            showGraphicsIfHidden(); canvas.repaint();
        });
    }
    @Override public void widget(twig.widgets.Widget widget, boolean clear) {
        edt(() -> {
            int region = clear ? activeRegion : contentRegion;
            if (clear) { canvas.region(region).clear(); canvas.region(region).drawFrame(false); displayed.remove(region); }
            canvas.region(region).draw(widget); finishDrawing(region, !clear);
            showGraphicsIfHidden(); canvas.repaint();
        });
    }
    @Override public void configure(Consumer<twig.graphics.TGRegion> action) { edt(() -> { action.accept(canvas.region(contentRegion)); canvas.repaint(); }); }
    @Override public void panel(String title, Map<String,String> buttons) {
        edt(() -> {
            JPanel panel = new JPanel(new GridLayout(0,2,5,5));
            buttons.forEach((label,command) -> { JButton button=new JButton(label); button.addActionListener(event -> execute(command)); panel.add(button); });
            WorkstationWindow frame = panel(title,panel,true); locate(frame, 100, 100, 550, 260);
            JPopupMenu edit = new JPopupMenu(); JMenuItem item=new JMenuItem("Edit button commands...");
            item.addActionListener(event -> { String text=buttons.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining("\n")); JTextArea area=new JTextArea(text,12,45); if(JOptionPane.showConfirmDialog(dialogParent(),new JScrollPane(area),"Button label=command",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) { Map<String,String> next=new LinkedHashMap<>(); for(String line:area.getText().split("\n")) { int eq=line.indexOf('='); if(eq>0)next.put(line.substring(0,eq),line.substring(eq+1)); } frame.dispose(); panel(title,next); } });
            edit.add(item);panel.setComponentPopupMenu(edit);
        });
    }
    @Override public void zones(int columns, int rows) { edt(() -> { canvas.divide(columns, rows); displayed.clear(); activeRegion = contentRegion = 0; updateZoneSelector(); canvas.repaint(); }); }
    @Override public void zone(int index) { edt(() -> { if (index < 0 || index >= canvas.count()) throw new IllegalArgumentException("Active region must be 1.." + canvas.count()); activeRegion = contentRegion = index; canvas.cd(index); updateZoneSelector(); }); }
    @Override public void export(Path file) {
        edt(() -> {
            String suffix = file.toString().toLowerCase(Locale.ROOT);
            if (!(suffix.endsWith(".png") || suffix.endsWith(".pdf") || suffix.endsWith(".ps") || suffix.endsWith(".eps"))) throw new IllegalArgumentException("Export filename must end in .png, .pdf, .ps or .eps");
            try { GraphicsExport.write(canvas, file); } catch (java.io.IOException ex) { throw new IllegalStateException(ex.getMessage(), ex); }
            log("Exported " + file);
        });
    }
    private Path choose(boolean save, String title, String extension) {
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle(title); if (save) chooser.setSelectedFile(new java.io.File("paw-workspace." + extension));
        if ((save ? chooser.showSaveDialog(dialogParent()) : chooser.showOpenDialog(dialogParent())) != JFileChooser.APPROVE_OPTION) return null;
        Path file = chooser.getSelectedFile().toPath();
        if (save && Files.exists(file) && JOptionPane.showConfirmDialog(dialogParent(), "Replace " + file.getFileName() + "?", "Replace file", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return null;
        return file;
    }
    private void openWorkspace() { Path file = choose(false, "Open PAW Java HIPO Workspace", "hipo"); if (file != null) run("Load workspace", () -> { workspace.load(file); edt(() -> { displayed.clear(); for (int i = 0; i < canvas.count(); i++) canvas.region(i).clear(); canvas.repaint(); }); }); }
    private void saveWorkspace() { Path file = choose(true, "Save PAW Java HIPO Workspace", "hipo"); if (file != null) execute("WORKSPACE/SAVE " + Commands.quote(file.toString())); }
    private void openData() {
        Path file = choose(false, "Open HIPO Event Data", "hipo"); if (file == null) return;
        run("Read HIPO dictionary", () -> { List<Schema> schemas = Workspace.schemas(file); workspace.ntuples().importFile(file); if (schemas.isEmpty()) throw new IllegalArgumentException("File has no bank schemas. For saved analysis use Open Workspace."); edt(() -> { dataFile = file; banks.clear(); for (Schema schema : schemas) banks.put(schema.getName(), schema); classes.setSelectedValue("Ntuples", true); }); log("Opened " + file + ": " + schemas.size() + " banks"); });
    }
    private void macro() { Path file = choose(false, "Run Macro (.kumac or .jmacro)", "kumac"); if (file != null) execute("MACRO/EXEC " + Commands.quote(file.toString())); }
    private void exportChooser() { Path file = choose(true, "Export Graphics", "png"); if (file != null) run("Export graphics", () -> export(file)); }
    private void saveTranscript() { Path file = choose(true, "Save Transcript", "txt"); if (file != null) { String text = transcript.getText(); run("Save transcript", () -> Files.writeString(file, text)); } }
    private void searchTranscript() { String query = JOptionPane.showInputDialog(dialogParent(), "Find in transcript"); if (query != null && !query.isEmpty()) { int index = transcript.getText().indexOf(query, transcript.getSelectionEnd()); if (index < 0) index = transcript.getText().indexOf(query); if (index >= 0) { transcript.requestFocusInWindow(); transcript.select(index, index + query.length()); } else log("Text not found: " + query); } }
    private void changeDirectory() { String path = JOptionPane.showInputDialog(dialogParent(), "Memory directory", workspace.directory()); if (path != null) execute("CDIR " + Commands.quote(path)); }
    private void promptCommand(String command, String label, String initial) { String args = JOptionPane.showInputDialog(dialogParent(), label, initial); if (args != null) execute(command + " " + args); }
    private void book(boolean twoDimensional) {
        execute("HISTOGRAM/CREATE/" + (twoDimensional ? "2D" : "1D"));
    }
    private void rebin() { String id = selectedId(); if (id == null) return; String args = JOptionPane.showInputDialog(dialogParent(), "Output identifier and merge factor", "rebinned 2"); if (args != null) execute("HISTOGRAM/REBIN " + Commands.quote(id) + " " + args); }
    private void fitDialog() { String id = selectedId(); if (id == null) return; Object kind = JOptionPane.showInputDialog(dialogParent(), "Fit model", "Fit", JOptionPane.PLAIN_MESSAGE, null, new String[]{"G", "E", "P0", "P1"}, "G"); if (kind != null) execute("H/FIT " + Commands.quote(id) + " " + kind); }
    private void attributes() {
        String id = selectedId(); if (id == null || busy) return; DataSet ds = browserObjects.get(id);
        JTextField title = new JTextField(ds.attr().getTitle()), color = new JTextField("" + ds.attr().getLineColor()), fill = new JTextField("" + ds.attr().getFillColor()), width = new JTextField("" + ds.attr().getLineWidth());
        if (form("Object Attributes", "Title", title, "Line color index / RGB code", color, "Fill color (-1 = none)", fill, "Line width", width)) run("Apply attributes", () -> { int c = Integer.parseInt(color.getText()), f = Integer.parseInt(fill.getText()), w = Integer.parseInt(width.getText()); if ((c < 0 && c > -200) || (f < 0 && f > -200 && f != -1) || w < 1 || w > 10) throw new IllegalArgumentException("Use palette indices or encoded RGB colors, fill -1 for none, line width 1..10"); ds.attr().setTitle(title.getText()); ds.attr().setLineColor(c); ds.attr().setFillColor(f); ds.attr().setLineWidth(w); plot(ds, false); });
    }
    private void axisTitles() {
        String id = selectedId(); if (id == null || busy) return; DataSet ds = browserObjects.get(id);
        JTextField x = new JTextField(ds.attr().getTitleX()), y = new JTextField(ds.attr().getTitleY());
        if (form("Axis Settings", "X title", x, "Y title", y)) run("Apply axis titles", () -> { ds.attr().setTitleX(x.getText()); ds.attr().setTitleY(y.getText()); plot(ds, false); });
    }
    private void axisScaling() {
        JTextField xmin = new JTextField("-5"), xmax = new JTextField("5"), ymin = new JTextField("0"), ymax = new JTextField("1000");
        if (form("Axis Scaling", "X minimum", xmin, "X maximum", xmax, "Y minimum", ymin, "Y maximum", ymax)) {
            try { double a = Double.parseDouble(xmin.getText()), b = Double.parseDouble(xmax.getText()), c = Double.parseDouble(ymin.getText()), d = Double.parseDouble(ymax.getText()); Workspace.checkAxis(1, a, b); Workspace.checkAxis(1, c, d); canvas.region(contentRegion).axisLimitsX(a, b).axisLimitsY(c, d); canvas.repaint(); } catch (Exception ex) { log(ex.getMessage()); }
        }
    }
    private boolean form(String title, Object... pairs) { JPanel form = new JPanel(new GridLayout(0, 2, 8, 8)); for (int i = 0; i < pairs.length; i += 2) { form.add(new JLabel((String) pairs[i])); form.add((Component) pairs[i + 1]); } return JOptionPane.showConfirmDialog(dialogParent(), form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION; }
    private void cloneBrowser() {
        DefaultListModel<String> model = new DefaultListModel<>(); browserObjects.keySet().forEach(model::addElement); JList<String> list = new JList<>(model); list.setCellRenderer(new ObjectRenderer());
        list.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2 && list.getSelectedValue() != null) execute("H/PLOT " + Commands.quote(list.getSelectedValue())); } });
        WorkstationWindow clone = panel("Paw++ Browser (snapshot)", new JScrollPane(list), true); locate(clone, 80, 120, 480, 360);
    }
    private void commandPanel() {
        JPanel buttons = new JPanel(new GridLayout(0, 2, 8, 8)); buttons.setBorder(new EmptyBorder(12, 12, 12, 12));
        for (String command : new String[]{"HELP", "H/LIST", "DEMO", "ZONE 1 1", "ZONE 2 2", "H/PLOT 10", "H/FIT 10 G", "HISTOGRAM/SHOW 10"}) buttons.add(button(command, () -> execute(command)));
        WorkstationWindow panel = panel("PAW++ Command Panel", buttons, true); locate(panel, 120, 140, 440, 280);
    }
    private void selectedNtuple() { String selected = objects.getSelectedValue(); if (selected != null && selected.startsWith("Ntuple: ")) openNtuple(browserTuples.get(selected.substring(8))); else if (!browserTuples.isEmpty()) openNtuple(browserTuples.values().iterator().next()); else log("Open a HIPO data file first."); }
    private void openNtuple(NtupleStore.Tuple tuple) {
        if (tuple == null) return;
        new NtupleViewer(tuple).open();
    }
    @Override public boolean showCommandHelp(String path) {
        CommandCatalog.matching(path);
        edt(() -> {
            WorkstationWindow[] window = new WorkstationWindow[1];
            CommandHelpPanel content = new CommandHelpPanel(path, workspace,
                    command -> { input.setText(command + " "); executive.toFront(); input.requestFocusInWindow(); },
                    this::execute, () -> window[0].close());
            window[0] = panel("PAW++ Command Help" + (path.isBlank() ? "" : " — " + path), content, true);
            locate(window[0], 50, 50, 940, 640);
        });
        return true;
    }
    private void help() { showCommandHelp(""); }
    private JMenu commandHelpMenu() {
        JMenu commands = menu("Commands", item("All commands...", this::help));
        commands.addSeparator();
        for (String group : CommandCatalog.groups()) commands.add(item(group + "...", () -> showCommandHelp(group)));
        commands.addSeparator(); commands.add(item("Command Panel", this::commandPanel));
        return commands;
    }
    private void manual() { Path manual = Path.of("../paw.pdf").toAbsolutePath().normalize(); try { if (Files.isRegularFile(manual) && Desktop.isDesktopSupported()) Desktop.getDesktop().open(manual.toFile()); else log("Manual: " + manual); } catch (Exception ex) { log(ex.getMessage()); } }
    private void about() { JOptionPane.showMessageDialog(dialogParent(), "PAW++ Java 0.1.0\nJava workstation based on the PAW manual, chapter 9.\nj4np HIPO event storage and Twig graphics.\nA new implementation; see README for compatibility coverage."); }
    private JButton button(String text, Runnable action) { JButton button = new JButton(text); button.addActionListener(event -> action.run()); return button; }
    private JMenuItem item(String text, Runnable action) { JMenuItem item = new JMenuItem(text); item.addActionListener(event -> action.run()); return item; }
    private JMenu menu(String text, JMenuItem... items) { JMenu menu = new JMenu(text); for (JMenuItem item : items) menu.add(item); return menu; }
    public void shutdown() {
        edt(() -> {
            if (shuttingDown) return;
            shuttingDown = true;
            Thread thread = operationThread; if (thread != null) thread.interrupt();
            for (WorkstationWindow window : List.copyOf(panels)) window.dispose();
        });
    }

    private final class ObjectRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            String path = value.toString(); DataSet ds = browserObjects.get(path); String name = path.startsWith("Ntuple: ") ? path.substring(8) : path.substring(path.lastIndexOf('/') + 1);
            Path macroEntry = list == objects ? macroEntries.get(path) : null;
            if (macroEntry != null) name = path.equals("../") ? ".. (Up)" : path;
            label.setText(name); label.setHorizontalAlignment(SwingConstants.CENTER); label.setVerticalTextPosition(SwingConstants.BOTTOM); label.setHorizontalTextPosition(SwingConstants.CENTER);
            label.setIcon(new ObjectIcon(ds instanceof H2F ? 2 : path.startsWith("Ntuple:") ? 3 : ds == null ? 0 : 1));
            if (macroEntry != null) label.setIcon(Files.isDirectory(macroEntry) ? ObjectIcon.FOLDER : ObjectIcon.MACRO);
            else if (path.startsWith("Directory: ")) label.setIcon(ObjectIcon.FOLDER);
            label.setToolTipText((macroEntry == null ? path : macroEntry.toString()) + (ds == null ? "" : " — " + ds.attr().getTitle())); label.setBorder(new EmptyBorder(6, 4, 6, 4)); return label;
        }
    }
    private static final class ObjectIcon implements Icon {
        static final Icon FOLDER = new ObjectIcon(4), MACRO = new ObjectIcon(5);
        final int type; ObjectIcon(int type) { this.type = type; }
        public int getIconWidth() { return 32; } public int getIconHeight() { return 30; }
        public void paintIcon(Component component, Graphics g, int x, int y) {
            if (type == 4 || type == 5) {
                Graphics2D drawing = (Graphics2D) g.create();
                try {
                    drawing.translate(x, y);
                    drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    drawing.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    if (type == 4) {
                        Polygon back = new Polygon(new int[]{2, 2, 12, 16, 29, 29}, new int[]{25, 5, 5, 9, 9, 25}, 6);
                        drawing.setColor(new Color(229, 174, 62)); drawing.fill(back);
                        drawing.setColor(new Color(153, 111, 30)); drawing.draw(back);
                        Polygon front = new Polygon(new int[]{2, 5, 31, 28}, new int[]{25, 12, 12, 25}, 4);
                        drawing.setColor(new Color(250, 205, 99)); drawing.fill(front);
                        drawing.setColor(new Color(153, 111, 30)); drawing.draw(front);
                    } else {
                        Polygon paper = new Polygon(new int[]{5, 20, 27, 27, 5}, new int[]{1, 1, 8, 28, 28}, 5);
                        drawing.setColor(Color.WHITE); drawing.fill(paper);
                        drawing.setColor(new Color(133, 148, 166)); drawing.draw(paper);
                        Polygon fold = new Polygon(new int[]{20, 20, 27}, new int[]{1, 8, 8}, 3);
                        drawing.setColor(new Color(222, 231, 242)); drawing.fill(fold);
                        drawing.setColor(new Color(133, 148, 166)); drawing.draw(fold);
                        drawing.setColor(new Color(31, 104, 201));
                        drawing.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12)); drawing.drawString(".M", 7, 14);
                        drawing.setColor(new Color(159, 171, 186));
                        drawing.drawLine(9, 18, 23, 18); drawing.drawLine(9, 21, 23, 21); drawing.drawLine(9, 24, 19, 24);
                    }
                } finally { drawing.dispose(); }
                return;
            }
            g.setColor(Color.WHITE); g.fillRect(x, y, 30, 28); g.setColor(type == 1 ? new Color(181, 140, 18) : type == 2 ? new Color(196, 33, 117) : type == 3 ? new Color(57, 129, 182) : new Color(70, 70, 195)); g.drawRect(x, y, 30, 28);
            if (type == 1) for (int i = 0; i < 7; i++) { int height = (int) (20 * Math.exp(-Math.pow((i - 3) / 2.0, 2))); g.fillRect(x + 3 + 3 * i, y + 24 - height, 2, height); }
            else for (int i = 4; i < 27; i += 5) { g.drawLine(x + i, y + 3, x + i, y + 25); g.drawLine(x + 3, y + i, x + 27, y + i); }
        }
    }

    private final class NtupleViewer {
        final Schema schema; final NtupleStore.Tuple tuple;
        final JTextField x = new JTextField(), y = new JTextField(), id = new JTextField("100"), cut = new JTextField();
        final JTextField min = new JTextField("-5"), max = new JTextField("5"), yMin = new JTextField("-5"), yMax = new JTextField("5");
        final JSpinner bins = new JSpinner(new SpinnerNumberModel(100, 1, 2000, 1)), yBins = new JSpinner(new SpinnerNumberModel(60, 1, 2000, 1));
        final JTextField first = new JTextField("1"), count = new JTextField("1000000");
        final JCheckBox cuts = new JCheckBox("Apply Cuts", true), same = new JCheckBox("Superimpose");
        NtupleViewer(NtupleStore.Tuple tuple) { this.tuple=tuple;this.schema=tuple.schema(32200); if (!schema.getEntryList().isEmpty()) x.setText(schema.getEntryList().get(0)); }
        void open() {
            JPanel body = new JPanel(new BorderLayout(8, 8)); body.setBorder(new EmptyBorder(8, 8, 8, 8)); body.add(new JLabel("Ntuple: " + tuple.id + "   " + tuple.title), BorderLayout.NORTH);
            JList<String> variables = new JList<>(schema.getEntryList().toArray(String[]::new)); variables.setSelectedIndex(0);
            variables.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2 && variables.getSelectedValue() != null) { x.setText(variables.getSelectedValue()); y.setText(""); project(); } } });
            for (JTextField field : List.of(x, y)) field.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2) field.setText(variables.getSelectedValue()); } });
            JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
            Object[] values = {"X expression", x, "Y expression (blank = 1D)", y, "Histogram ID", id, "First row (1-based)", first, "Number of rows", count, "X bins", bins, "X minimum", min, "X maximum", max, "Y bins", yBins, "Y minimum", yMin, "Y maximum", yMax, "Cut expression", cut};
            for (int i = 0; i < values.length; i += 2) { fields.add(new JLabel((String) values[i])); fields.add((Component) values[i + 1]); }
            fields.add(cuts); fields.add(same);
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(variables), fields); split.setDividerLocation(220); body.add(split);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER)); actions.add(button("Plot", this::project)); actions.add(button("Scan", this::scan)); actions.add(button("Cut Editor...", this::editCuts)); actions.add(button("Help", () -> log("Expressions: sqrt(px^2+py^2), abs(vz); cuts: pid==11 && abs(vz)<5. First/count refer to bank rows. Scan displays up to 1000 accepted rows."))); body.add(actions, BorderLayout.SOUTH);
            WorkstationWindow panel = panel("PAW++ Ntuple Viewer", body, true); locate(panel, 70, 55, 860, 620);
        }
        void project() {
            String name = id.getText().trim(), xText = x.getText(), yText = y.getText(), selection = cuts.isSelected() ? cut.getText() : "";
            try {
                int nx = (Integer) bins.getValue(), ny = (Integer) yBins.getValue(); double a = Double.parseDouble(min.getText()), b = Double.parseDouble(max.getText()), c = Double.parseDouble(yMin.getText()), d = Double.parseDouble(yMax.getText()); long start = Long.parseLong(first.getText()), n = Long.parseLong(count.getText()); boolean over = same.isSelected();
                // Capture the destination directory before starting the worker.
                String dest = workspace.path(name);
                run("Project " + schema.getName(), () -> { DataSet ds = viewerProjection(tuple, name, xText, yText, selection, nx, a, b, ny, c, d, start, n); if (Thread.currentThread().isInterrupted()) return; workspace.put(dest, ds); plot(ds, over); log("Projected " + schema.getName() + " into " + dest + (selection.isBlank() ? "" : " with cut " + selection)); });
            } catch (Exception ex) { log("Error: " + ex.getMessage()); }
        }
        void scan() {
            try {
                long start = Long.parseLong(first.getText()), n = Long.parseLong(count.getText()); String selection = cuts.isSelected() ? cut.getText() : "";

                run("Scan " + schema.getName(), () -> {
                    List<Object[]> rows = new ArrayList<>(); try(var query=commands.ntuples.new Query(new NtupleCommands.Selector(tuple,tuple.columns,List.of()),selection.isBlank()?"1.":selection,null,0)){tuple.rows(start,n,(index,values) -> { if (rows.size() < 1000 && Expression.truth(query.weight(index,values))) { Object[] row = new Object[values.length]; for (int i = 0; i < values.length; i++) row[i] = values[i]; rows.add(row); } });}
                    edt(() -> { DefaultTableModel model = new DefaultTableModel(schema.getEntryList().toArray(), 0) { public boolean isCellEditable(int row, int col) { return false; } }; rows.forEach(model::addRow); JTable table = new JTable(model); table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); table.setAutoCreateRowSorter(true); WorkstationWindow panel = panel("PAW++ Ntuple Scanner (first 1000 accepted rows)", new JScrollPane(table), true); locate(panel, 110, 100, 900, 460); });
                });
            } catch (Exception ex) { log("Error: " + ex.getMessage()); }
        }
        void editCuts() {
            JPanel body = new JPanel(new BorderLayout(8, 8)); body.setBorder(new EmptyBorder(10, 10, 10, 10));
            JTextArea expression = new JTextArea(cut.getText(), 5, 40); body.add(new JScrollPane(expression), BorderLayout.NORTH);
            JPanel lines = new JPanel(new GridLayout(4, 5, 6, 6)); List<JCheckBox> enabled = new ArrayList<>(); List<JComboBox<String>> columns = new ArrayList<>(), operators = new ArrayList<>(); List<JTextField> thresholds = new ArrayList<>(); List<JCheckBox> negated = new ArrayList<>();
            for (int i = 0; i < 4; i++) { JCheckBox on = new JCheckBox("$" + (i + 1)); JComboBox<String> col = new JComboBox<>(schema.getEntryList().toArray(String[]::new)), op = new JComboBox<>(new String[]{"<", "<=", "==", "!=", ">=", ">"}); JTextField value = new JTextField("0"); JCheckBox not = new JCheckBox("!"); enabled.add(on); columns.add(col); operators.add(op); thresholds.add(value); negated.add(not); lines.add(on); lines.add(col); lines.add(op); lines.add(value); lines.add(not); }
            body.add(lines); JPanel actions = new JPanel(new FlowLayout());
            actions.add(button("Build AND expression", () -> { List<String> parts = new ArrayList<>(); for (int i = 0; i < 4; i++) if (enabled.get(i).isSelected()) { String part = "(" + columns.get(i).getSelectedItem() + operators.get(i).getSelectedItem() + thresholds.get(i).getText() + ")"; parts.add((negated.get(i).isSelected() ? "!" : "") + part); } expression.setText(String.join(" && ", parts)); }));
            actions.add(button("Apply", () -> applyCut(expression.getText(), false))); actions.add(button("Apply & Plot", () -> applyCut(expression.getText(), true))); actions.add(button("Open...", () -> { Path path = choose(false, "Open cuts", "cut"); if (path != null) run("Read cut file", () -> { String text = Files.readString(path); edt(() -> expression.setText(text)); }); }));
            actions.add(button("Save...", () -> { Path path = choose(true, "Save cuts", "cut"); if (path != null) { String text = expression.getText(); run("Save cuts", () -> Files.writeString(path, text)); } })); body.add(actions, BorderLayout.SOUTH);
            WorkstationWindow panel = panel("PAW++ Cut Editor", body, true); locate(panel, 130, 180, 900, 400);
        }
        void applyCut(String text, boolean replot) { try { Expression.compile(text.isBlank() ? "true" : text, schema.getEntryList()); cut.setText(text); cuts.setSelected(true); if (replot) project(); } catch (Exception ex) { log("Error: " + ex.getMessage()); } }
    }
}
