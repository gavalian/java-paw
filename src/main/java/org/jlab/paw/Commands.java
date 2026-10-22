package org.jlab.paw;

import twig.data.*;
import twig.math.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** A documented subset of PAW command spelling, backed entirely by j4np/Twig. */
public final class Commands {
    public interface View {
        void plot(DataSet ds, boolean same);
        void zones(int columns, int rows);
        void zone(int index);
        void export(Path file);
        /** Noninteractive views retain the default without opening a window. */
        default String readInput(String prompt, String defaultValue) throws Exception { return defaultValue; }
        /** Return supplied arguments unchanged when no interactive parameter panel is available. */
        default List<String> requestArguments(CommandArguments.Spec spec, List<String> supplied) throws Exception { return supplied; }
        /** Return false to print help in the transcript instead of opening a browser. */
        default boolean showCommandHelp(String path) { return false; }
        default void waitBetweenPages(double seconds) throws Exception { }
        default void updatePlot(DataSet ds,String options) { }
        default void axisLabels(boolean show) { }
        default void draw(DataSet ds, String options, boolean same) { ds.attr().setDrawOptions(options); plot(ds,same); }
        default void widget(twig.widgets.Widget widget, boolean clear) { throw new UnsupportedOperationException("Widgets require a graphics view"); }
        default void configure(Consumer<twig.graphics.TGRegion> action) { throw new UnsupportedOperationException("Graphics configuration requires a graphics view"); }
        default java.awt.image.BufferedImage capture() { throw new UnsupportedOperationException("Picture capture requires a graphics view"); }
        default void ntuple(Path file, List<j4np.hipo5.data.Schema> schemas) { throw new UnsupportedOperationException("Ntuple registration requires a desktop view"); }
        default List<double[]> graphicalCut(String id,List<String> columns,List<double[]> points) throws Exception { throw new UnsupportedOperationException("GCUT requires an interactive view"); }
        default void editFile(Path file) { }
        default void panel(String title, Map<String,String> buttons) { throw new UnsupportedOperationException("Panels require a desktop view"); }
    }
        MacroContext callbackContext(Path file,int depth) { if(depth>=8)throw new IllegalArgumentException("Callback nesting exceeds eight levels");return new MacroContext(workspace,this,view,output,file.getParent(),List.of(),globals,depth+1); }
    public static final String HELP = CommandCatalog.text("/", false);
    private final Workspace workspace;
    private final View view;
    private final Consumer<String> output;
    private final Vectors vectors;
    final NtupleCommands ntuples;
    private final Map<String,Object> globals = new LinkedHashMap<>();
    private final Deque<Map<String,String>> macroVariables = new ArrayDeque<>();
    private Map<String,String> variables() { return macroVariables.peek(); }
    private Integer histogramColor, graphColor, histogramWidth, graphWidth;
    public Commands(Workspace workspace, View view, Consumer<String> output) { this.workspace = workspace; this.view = view; this.output = output; vectors = new Vectors(workspace, output, this::styledPlot); ntuples=new NtupleCommands(workspace,this,view,output); macroVariables.push(new LinkedHashMap<>()); }
    public void execute(String command) throws Exception { execute(command, 0, null); }
    public void executeInteractive(String command) throws Exception {
        String stripped = command.stripLeading();
        if (stripped.isBlank() || stripped.startsWith("#") || stripped.startsWith("*")) return;
        List<String> args = tokenize(command);
        if (args.isEmpty()) return;
        String op = canonical(args.get(0));
        CommandArguments.Spec spec = CommandArguments.spec(op, workspace, args);
        while (spec != null && args.size() - 1 < spec.required()) {
            List<String> completed = view.requestArguments(spec, List.copyOf(args.subList(1, args.size())));
            if (completed == null) throw new java.util.concurrent.CancellationException("Command cancelled");
            if (completed.equals(args.subList(1, args.size()))) break;
            if (op.equals("VECTOR/INPUT") && completed.size() == 2) {
                List<String> values = new ArrayList<>(); values.add(completed.get(0)); values.addAll(tokenize(completed.get(1))); completed = values;
            }
            command = op + completed.stream().map(Commands::quote).collect(java.util.stream.Collectors.joining(" ", " ", ""));
            args = tokenize(command);
            spec = CommandArguments.spec(op, workspace, args);
        }
        execute(command);
    }
    static String canonical(String command) {
        String op = command.toUpperCase(Locale.ROOT).replaceFirst("^/", "").replaceFirst("^(H|HI|HISTO)/", "HISTOGRAM/").replaceFirst("^V/", "VECTOR/");
        op=NtupleCommands.canonical(op);
        return switch (op) { case "HISTOGRAM/DEL" -> "HISTOGRAM/DELETE"; case "HISTOGRAM/BOOK", "1D" -> "HISTOGRAM/CREATE/1D"; case "2D" -> "HISTOGRAM/CREATE/2D"; default -> op; };
    }
    void executeNested(String command, int depth, Path macroDirectory) throws Exception { execute(command,depth,macroDirectory); }
    private void execute(String command, int depth, Path macroDirectory) throws Exception {
        String stripped = command.stripLeading();
        if (stripped.isBlank() || stripped.startsWith("#") || stripped.startsWith("*")) return;
        List<String> args = tokenize(command);
        if (args.isEmpty() || args.get(0).startsWith("#") || args.get(0).startsWith("*")) return;
        for (int i = 1; i < args.size(); i++) {
            var matcher = java.util.regex.Pattern.compile("\\[([A-Za-z_][A-Za-z0-9_]*)\\]").matcher(args.get(i));
            args.set(i, matcher.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(variables().getOrDefault(match.group(1), match.group()))));
        }
        String op = canonical(args.get(0));
        output.accept("Paw++> " + command);
        if (op.equals("HELP") || op.equals("CHELP") || op.equals("USAGE") || op.endsWith("/HELP") || CommandCatalog.groups().contains(CommandCatalog.normalize(op))) {
            String path;
            if (op.endsWith("/HELP")) { require(args, 1); path = op.substring(0, op.length() - 5); }
            else if (CommandCatalog.groups().contains(CommandCatalog.normalize(op))) { require(args, 1); path = op; }
            else { if (args.size() > 2) throw new IllegalArgumentException(op + " [command or group]"); path = args.size() == 2 ? args.get(1) : ""; }
            String reference = CommandCatalog.text(path, op.equals("USAGE"));
            if (op.equals("USAGE") || !view.showCommandHelp(CommandCatalog.normalize(path))) output.accept(reference);
            return;
        }
        if (ntuples.execute(op,args,depth,macroDirectory)) return;
        if (vectors.execute(op, args)) return;
        switch (op) {
            case "MACRO/DELAY" -> {
                require(args, 2);
                if (!args.get(1).matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("MACRO/DELAY variable");
                String prompt = "Delay between screens in seconds: positive = automatic advance, negative = Continue after each screen, 0 = no delay.";
                String prior = "-1";
                while (true) {
                    String answer = view.readInput(prompt, prior);
                    if (answer == null) throw new java.util.concurrent.CancellationException("Run all cancelled");
                    if (answer.isBlank()) answer = prior;
                    try {
                        double delay = Double.parseDouble(answer.strip());
                        if (!Double.isFinite(delay)) throw new NumberFormatException();
                        variables().put(args.get(1), Double.toString(delay)); break;
                    } catch (NumberFormatException ex) {
                        prompt = "Enter a finite number of seconds (for example 2, 0.5, or -1). Positive = automatic; negative = Continue; 0 = no delay.";
                        prior = answer;
                    }
                }
            }
            case "MACRO/PAGE" -> {
                require(args, 3); double delay = number(args, 1);
                if (!Double.isFinite(delay)) throw new IllegalArgumentException("Screen delay must be finite");
                if (delay < 0) {
                    String prior = variables().getOrDefault("continue", "");
                    String answer = view.readInput(args.get(2), prior);
                    if (answer == null) throw new java.util.concurrent.CancellationException("Run all cancelled");
                    variables().put("continue", answer.isEmpty() ? prior : answer);
                } else if (delay > 0) view.waitBetweenPages(delay);
            }
            case "READ" -> {
                if (args.size() < 2 || args.size() > 3 || !args.get(1).matches("[A-Za-z_][A-Za-z0-9_]*"))
                    throw new IllegalArgumentException("READ name ['prompt']");
                String name = args.get(1), prior = variables().getOrDefault(name, "");
                String prompt = args.size() == 3 ? args.get(2) : "Macro input: " + name + " ?";
                String answer = view.readInput(prompt, prior);
                if (answer == null) throw new java.util.concurrent.CancellationException("Macro cancelled at READ " + name);
                variables().put(name, answer.isEmpty() ? prior : answer);
            }
            case "SET" -> {
                require(args, 3); String attribute = args.get(1).toUpperCase(Locale.ROOT); int value = integer(args, 2);
                switch (attribute) {
                    case "HCOL" -> { decodeHistogramColor(value); histogramColor = value; }
                    case "PCOL" -> { pawColor(value); graphColor = value; }
                    case "HWID", "PWID" -> { if (value < 1 || value > 10) throw new IllegalArgumentException("Line width is 1..10"); if (attribute.equals("HWID")) histogramWidth = value; else graphWidth = value; }
                    default -> throw new IllegalArgumentException("SET supports HCOL, PCOL, HWID, PWID");
                }
            }
            case "DEMO" -> { require(args, 1); workspace.demo(); view.plot(workspace.get("10"), false); }
            case "CDIR" -> { if (args.size() == 2) { workspace.ntuples().cd(args.get(1)); if(args.get(1).startsWith("//PAWC")||!args.get(1).startsWith("//"))workspace.cd(args.get(1)); } else require(args, 1); output.accept(workspace.ntuples().directory()); }
            case "HISTOGRAM/LIST" -> { require(args, 1); workspace.objects().forEach((path, ds) -> output.accept(path + "  " + ds.getClass().getSimpleName() + "  " + ds.attr().getTitle())); }
            case "HISTOGRAM/CREATE/1D" -> { require(args, 6); workspace.put(args.get(1), Workspace.book1(args.get(1), args.get(2), integer(args, 3), number(args, 4), number(args, 5))); }
            case "HISTOGRAM/CREATE/2D" -> {
                require(args, 9); int nx = integer(args, 3), ny = integer(args, 6);
                Workspace.checkAxis(nx, number(args, 4), number(args, 5)); Workspace.checkAxis(ny, number(args, 7), number(args, 8));
                if ((long) nx * ny > 4_000_000) throw new IllegalArgumentException("2-D histogram limited to four million bins");
                H2F h = new H2F(args.get(1), nx, number(args, 4), number(args, 5), ny, number(args, 7), number(args, 8)); h.attr().setTitle(args.get(2)); workspace.put(args.get(1), h);
            }
            case "HISTOGRAM/FILL" -> {
                if (args.size() < 3) throw new IllegalArgumentException("FILL id x [y] [weight]");
                DataSet ds = workspace.get(args.get(1));
                if (ds instanceof H1F h) { if (args.size() != 3 && args.size() != 4) throw new IllegalArgumentException("1-D FILL id x [weight]"); h.fill(number(args, 2), args.size() == 4 ? number(args, 3) : 1); }
                else if (ds instanceof H2F h) { if (args.size() != 4 && args.size() != 5) throw new IllegalArgumentException("2-D FILL id x y [weight]"); h.fill(number(args, 2), number(args, 3), args.size() == 5 ? number(args, 4) : 1); }
                else throw new IllegalArgumentException("Select a histogram");
            }
            case "HISTOGRAM/PLOT" -> { if (args.size() != 2 && args.size() != 3) throw new IllegalArgumentException("PLOT id [S]"); if (args.size() == 3 && !args.get(2).equalsIgnoreCase("S")) throw new IllegalArgumentException("Overlay option is S"); styledPlot(workspace.get(args.get(1)), args.size() == 3); }
            case "HISTOGRAM/COPY" -> { require(args, 3); workspace.copy(args.get(1), args.get(2)); }
            case "HISTOGRAM/DELETE" -> { require(args, 2);String id=args.get(1);if(id.equals("*")||id.equals("0")){workspace.deleteAll();workspace.ntuples().delete(id);}else {boolean h=workspace.objects().containsKey(workspace.path(id)),n=workspace.ntuples().tuples().containsKey(workspace.ntuples().path(id));if(!h&&!n)throw new IllegalArgumentException("Object not found: "+id);if(h)workspace.delete(id);if(n)workspace.ntuples().delete(id);} }
            case "HISTOGRAM/RESET" -> { require(args, 2); DataSet ds = workspace.get(args.get(1)); if (ds instanceof H1F h) h.reset(); else if (ds instanceof H2F h) h.reset(); else throw new IllegalArgumentException("Select a histogram"); view.plot(ds, false); }
            case "HISTOGRAM/REBIN" -> { require(args, 4); view.plot(workspace.rebin(args.get(1), args.get(2), integer(args, 3)), false); }
            case "HISTOGRAM/ADD", "HISTOGRAM/SUB", "HISTOGRAM/MUL", "HISTOGRAM/DIV" -> { require(args, 4); view.plot(workspace.arithmetic(args.get(1), args.get(2), args.get(3), op.substring(op.lastIndexOf('/') + 1)), false); }
            case "HISTOGRAM/SHOW" -> { require(args, 2); H1F h = workspace.histogram(args.get(1)); output.accept(String.format(Locale.ROOT, "Entries: %d   Integral: %.6g   Mean: %.6g   RMS: %.6g   Underflow: %d   Overflow: %d", h.getEntries(), h.integral(), h.getMean(), h.getRMS(), h.getUnderflow(), h.getOverflow())); }
            case "HISTOGRAM/FIT" -> { require(args, 3); fit(args.get(1), args.get(2)); }
            case "HISTOGRAM/SMOOTH" -> {
                require(args, 3); H1F h = workspace.histogram(args.get(1)), result = h.copy(); int n = h.getAxis().getNBins();
                for (int i = 0; i < n; i++) {
                    double sum = 0, variance = 0, weights = 0;
                    for (int j = Math.max(0, i - 1); j <= Math.min(n - 1, i + 1); j++) { double w = j == i ? 2 : 1; sum += w * h.getBinContent(j); variance += w * w * Math.pow(h.getBinError(j), 2); weights += w; }
                    result.setBinContent(i, sum / weights); result.setBinError(i, Math.sqrt(variance) / weights);
                }
                workspace.put(args.get(2), result); view.plot(result, false);
            }
            case "FUNCTION/PLOT" -> { require(args, 4); double min = number(args, 2), max = number(args, 3); Workspace.checkAxis(1, min, max); view.plot(new F1D("function", args.get(1), min, max), false); }
            case "ZONE" -> { require(args, 3); int cols = integer(args, 1), rows = integer(args, 2); if (cols < 1 || rows < 1 || cols * (long) rows > 16) throw new IllegalArgumentException("Use 1..16 regions"); view.zones(cols, rows); }
            case "CD" -> { require(args, 2); if(args.get(1).startsWith("//")){workspace.ntuples().cd(args.get(1));if(args.get(1).startsWith("//PAWC"))workspace.cd(args.get(1));}else view.zone(integer(args, 1) - 1); }
            case "WORKSPACE/SAVE" -> { require(args, 2); workspace.save(Path.of(args.get(1))); output.accept("Saved " + args.get(1)); }
            case "WORKSPACE/OPEN" -> { require(args, 2); workspace.load(Path.of(args.get(1))); output.accept("Loaded " + workspace.objects().size() + " objects"); }
            case "GRAPHICS/EXPORT" -> { require(args, 2); view.export(Path.of(args.get(1))); }
            case "MACRO/EXEC" -> {
                if(args.size()<2)throw new IllegalArgumentException("MACRO/EXEC file [Java macro arguments...]"); if (depth >= 8) throw new IllegalArgumentException("Macro nesting exceeds eight levels");
                Path file = Path.of(args.get(1)); if (!file.isAbsolute() && macroDirectory != null) file = macroDirectory.resolve(file); file = file.toAbsolutePath().normalize(); if (Files.size(file) > 1_000_000) throw new IllegalArgumentException("Macro too large");
                if(file.toString().endsWith(".jmacro")) {
                    JavaMacros.run(file,new MacroContext(workspace,this,view,output,file.getParent(),args.subList(2,args.size()),globals,depth+1)); break;
                }
                require(args,2);
                macroVariables.push(new LinkedHashMap<>());
                try {
                    int line = 0;
                    for (String text : Files.readAllLines(file)) {
                        line++; if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                        try { execute(text, depth + 1, file.getParent()); }
                        catch (java.util.concurrent.CancellationException ex) { throw ex; }
                        catch (Exception ex) { throw new IllegalArgumentException(file + ":" + line + ": " + ex.getMessage(), ex); }
                    }
                } finally { macroVariables.pop(); }
            }
            default -> throw new IllegalArgumentException("Unsupported command: " + op + ". Type HELP for the implemented commands.");
        }
    }
    private static int pawColor(int index) {
        java.awt.Color color = switch (index) { case 0 -> java.awt.Color.WHITE; case 1 -> java.awt.Color.BLACK; case 2 -> java.awt.Color.RED; case 3 -> java.awt.Color.GREEN; case 4 -> java.awt.Color.BLUE; case 5 -> java.awt.Color.YELLOW; case 6 -> java.awt.Color.MAGENTA; case 7 -> java.awt.Color.CYAN; default -> throw new IllegalArgumentException("The book's basic PAW colors 0..7 are supported"); };
        return twig.config.TPalette.createColor(color.getRed(), color.getGreen(), color.getBlue(), 255);
    }
    private static int[] decodeHistogramColor(int value) {
        if (value >= 0 && value <= 7) return new int[]{pawColor(value), -1};
        if (value >= 1000 && value < 2000) { int border = (value - 1000) / 100, fill = value % 100; return new int[]{pawColor(border == 0 ? fill : border), pawColor(fill)}; }
        throw new IllegalArgumentException("HCOL uses 0..7, 1000+fill, or 1000+100*border+fill; colors 0..7");
    }
    private void styledPlot(DataSet dataset, boolean same) {
        boolean changed = false;
        if (dataset instanceof H1F || dataset instanceof H2F) {
            if (histogramColor != null) { int[] colors = decodeHistogramColor(histogramColor); dataset.attr().setLineColor(colors[0]); dataset.attr().setFillColor(colors[1]); changed = true; }
            if (histogramWidth != null) { dataset.attr().setLineWidth(histogramWidth); changed = true; }
        } else if (dataset instanceof GraphErrors) {
            if (graphColor != null) { int color = pawColor(graphColor); dataset.attr().setLineColor(color); dataset.attr().setMarkerColor(color); changed = true; }
            if (graphWidth != null) { dataset.attr().setLineWidth(graphWidth); changed = true; }
        }
        // Keep previously drawn copies stable when SET changes the same object in another region.
        view.plot(changed ? Workspace.snapshot(dataset) : dataset, same);
    }
    private void fit(String id, String kind) {
        H1F h = workspace.histogram(id); if (h.integral() <= 0 || h.getRMS() <= 0) throw new IllegalArgumentException("Fit requires a populated histogram with nonzero spread");
        String expression = switch (kind.toUpperCase(Locale.ROOT)) { case "G", "GAUSS" -> "[amp]*gaus(x,[mean],[sigma])"; case "E", "EXP" -> "[amp]*exp([slope]*x)"; case "P0" -> "[constant]"; case "P1" -> "[constant]+[slope]*x"; default -> throw new IllegalArgumentException("Fit type: G, E, P0, P1"); };
        F1D fit = new F1D("fit_" + h.getName(), expression, h.getAxis().min(), h.getAxis().max());
        if (kind.equalsIgnoreCase("G") || kind.equalsIgnoreCase("GAUSS")) { fit.setParameters(h.getBinContent(h.getMaximumBin()), h.getMean(), h.getRMS()); fit.setParLimits(2, h.getAxis().getBinWidth(0) * .01, (h.getAxis().max() - h.getAxis().min()) * 10); }
        else if (kind.equalsIgnoreCase("E") || kind.equalsIgnoreCase("EXP")) fit.setParameters(h.getBinContent(h.getMaximumBin()), -1.0);
        else if (kind.equalsIgnoreCase("P0")) fit.setParameters(h.integral() / h.getAxis().getNBins());
        else fit.setParameters(h.integral() / h.getAxis().getNBins(), 0.0);
        DataFitter.fit(fit, h, "");
        for (int i = 0; i < fit.getNPars(); i++) if (!Double.isFinite(fit.getParameter(i))) throw new IllegalArgumentException("Fit produced nonfinite parameters");
        fit.attr().setLineColor(2); fit.attr().setLineWidth(2);
        view.plot(h, false); view.plot(fit, true);
        output.accept("Twig/Minuit fit: " + expression + "   chi-square=" + fit.getChiSquare());
        for (int i = 0; i < fit.getNPars(); i++) output.accept(fit.parameter(i).toString());
    }
    static double number(List<String> args, int index) { double n = Double.parseDouble(args.get(index)); if (!Double.isFinite(n)) throw new IllegalArgumentException("Numbers must be finite"); return n; }
    static int integer(List<String> args, int index) { return Integer.parseInt(args.get(index)); }
    private static void require(List<String> args, int count) { if (args.size() != count) throw new IllegalArgumentException("Expected " + (count - 1) + " arguments; received " + (args.size() - 1) + ". Type HELP."); }
    public static String quote(String text) { return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>(); StringBuilder token = new StringBuilder(); char quote = 0; boolean started = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                else if (c == '\\' && i + 1 < text.length() && (text.charAt(i + 1) == quote || text.charAt(i + 1) == '\\')) token.append(text.charAt(++i));
                else token.append(c);
            } else if (c == '|' && (i == 0 || text.charAt(i - 1) != '|') && (i + 1 == text.length() || text.charAt(i + 1) != '|')) { if (started) tokens.add(token.toString()); return tokens; }
            else if (c == '\'' || c == '"') { quote = c; started = true; }
            else if (Character.isWhitespace(c)) { if (started) { tokens.add(token.toString()); token.setLength(0); started = false; } }
            else { token.append(c); started = true; }
        }
        if (quote != 0) throw new IllegalArgumentException("Unclosed quotation mark");
        if (started) tokens.add(token.toString()); return tokens;
    }
}
