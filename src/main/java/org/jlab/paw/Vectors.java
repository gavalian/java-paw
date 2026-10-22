package org.jlab.paw;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import twig.data.*;

/** The one-dimensional vector operations needed by the manual's introductory examples. */
final class Vectors {
    private final Map<String, double[]> values = new LinkedHashMap<>();
    private final Workspace workspace;
    private final Consumer<String> output;
    private final java.util.function.BiConsumer<DataSet, Boolean> draw;
    Vectors(Workspace workspace, Consumer<String> output, java.util.function.BiConsumer<DataSet, Boolean> draw) { this.workspace = workspace; this.output = output; this.draw = draw; }
    private String name(String text) { if (!text.matches("[A-Za-z][A-Za-z0-9_]*")) throw new IllegalArgumentException("Vector names start with a letter and contain letters, digits, or underscores"); return text.toLowerCase(Locale.ROOT); }
    private double[] get(String text) { String key = name(text); double[] vector = values.get(key); if (vector == null) throw new IllegalArgumentException("Unknown vector: " + text); return vector; }
    double[] select(String text) {
        var matcher = Pattern.compile("([A-Za-z][A-Za-z0-9_]*)(?:\\(([^()]*)\\))?").matcher(text);
        if (!matcher.matches()) throw new IllegalArgumentException("Use vector, vector(index), or vector(first:last)");
        double[] vector = get(matcher.group(1)); String selection = matcher.group(2);
        if (selection == null) return vector.clone();
        int start, end;
        if (selection.contains(":")) { String[] range = selection.split(":", -1); if (range.length != 2) throw new IllegalArgumentException("Invalid slice"); start = range[0].isBlank() ? 1 : Integer.parseInt(range[0]); end = range[1].isBlank() ? vector.length : Integer.parseInt(range[1]); }
        else start = end = Integer.parseInt(selection);
        if (start < 1 || end < start || end > vector.length) throw new IllegalArgumentException("Vector indices are 1.." + vector.length);
        return Arrays.copyOfRange(vector, start - 1, end);
    }
    private static void require(List<String> args, int count) { if (args.size() != count) throw new IllegalArgumentException("Expected " + (count - 1) + " arguments for " + args.get(0)); }
    boolean execute(String op, List<String> args) {
        switch (op) {
            case "VECTOR/CREATE" -> {
                if (args.size() < 2) throw new IllegalArgumentException("V/CREATE name(size) [R|I] [values...]");
                var declaration = Pattern.compile("([A-Za-z][A-Za-z0-9_]*)(?:\\((\\d+)\\))?").matcher(args.get(1));
                if (!declaration.matches()) throw new IllegalArgumentException("Only one-dimensional vector declarations are supported");
                int size = declaration.group(2) == null ? 1 : Integer.parseInt(declaration.group(2));
                if (size < 1 || size > 1_000_000) throw new IllegalArgumentException("Vector size must be 1..1000000");
                int first = 2; boolean integer = false;
                if (args.size() > 2 && (args.get(2).equalsIgnoreCase("R") || args.get(2).equalsIgnoreCase("I"))) { integer = args.get(2).equalsIgnoreCase("I"); first++; }
                if (args.size() - first > size) throw new IllegalArgumentException("Too many vector values");
                double[] vector = new double[size];
                for (int i = first; i < args.size(); i++) { double value = Commands.number(args, i); if (integer && value != Math.rint(value)) throw new IllegalArgumentException("I vectors require integer initial values"); vector[i - first] = value; }
                values.put(name(declaration.group(1)), vector);
            }
            case "VECTOR/INPUT" -> {
                if (args.size() < 3) throw new IllegalArgumentException("V/INPUT name values...");
                double[] existing = get(args.get(1)); if (args.size() - 2 != existing.length) throw new IllegalArgumentException("Supply exactly " + existing.length + " values");
                double[] replacement = new double[existing.length]; for (int i = 0; i < replacement.length; i++) replacement[i] = Commands.number(args, i + 2); values.put(name(args.get(1)), replacement);
            }
            case "VECTOR/LIST" -> { require(args, 1); values.forEach((key, vector) -> output.accept(key + "  length=" + vector.length)); }
            case "VECTOR/PRINT" -> { require(args, 2); double[] vector = select(args.get(1)); for (int i = 0; i < vector.length; i++) output.accept(String.format(Locale.ROOT, "%s[%d] = %.7g", args.get(1), i + 1, vector[i])); }
            case "VECTOR/COPY" -> { require(args, 3); values.put(name(args.get(2)), select(args.get(1))); }
            case "VECTOR/DELETE" -> { require(args, 2); if (args.get(1).equals("*")) values.clear(); else { get(args.get(1)); values.remove(name(args.get(1))); } }
            case "VBIAS", "VSCALE", "VADD", "VMULTI", "VSUBTR", "VDIVID" -> {
                require(args, 4); double[] a = select(args.get(1)); double[] b = op.equals("VBIAS") || op.equals("VSCALE") ? new double[]{Commands.number(args, 2)} : select(args.get(2));
                boolean scalar = op.equals("VBIAS") || op.equals("VSCALE"); int length = scalar ? a.length : Math.min(a.length, b.length);
                String destination = name(args.get(3)); double[] previous = values.get(destination); double[] result = previous == null ? new double[a.length] : previous.clone(); length = Math.min(length, result.length);
                for (int i = 0; i < length; i++) { double right = scalar ? b[0] : b[i]; result[i] = switch (op) { case "VBIAS", "VADD" -> a[i] + right; case "VSCALE", "VMULTI" -> a[i] * right; case "VSUBTR" -> a[i] - right; default -> { if (right == 0) throw new IllegalArgumentException("Zero denominator at vector element " + (i + 1)); yield a[i] / right; } }; if (!Double.isFinite(result[i])) throw new IllegalArgumentException("Nonfinite vector result"); }
                values.put(destination, result);
            }
            case "VECTOR/HFILL" -> { require(args, 3); H1F histogram = workspace.histogram(args.get(2)); for (double value : select(args.get(1))) histogram.fill(value); }
            case "HISTOGRAM/PUT_VECTOR/CONTENT" -> { require(args, 3); H1F histogram = workspace.histogram(args.get(1)); double[] vector = select(args.get(2)); if (vector.length != histogram.getAxis().getNBins()) throw new IllegalArgumentException("Vector length must match bin count"); for (int i = 0; i < vector.length; i++) histogram.setBinContent(i, vector[i]); }
            case "HISTOGRAM/GET_VECTOR/CONTENT" -> { require(args, 3); H1F histogram = workspace.histogram(args.get(1)); double[] vector = new double[histogram.getAxis().getNBins()]; for (int i = 0; i < vector.length; i++) vector[i] = histogram.getBinContent(i); values.put(name(args.get(2)), vector); }
            case "VECTOR/DRAW", "VECTOR/PLOT" -> {
                if (args.size() < 2 || args.size() > 4) throw new IllegalArgumentException("V/DRAW|PLOT vector [id] [S]");
                if (args.size() == 4 && !args.get(3).equalsIgnoreCase("S")) throw new IllegalArgumentException("Overlay option is S");
                String id = args.size() >= 3 ? args.get(2) : "vector_plot";
                DataSet dataset;
                if (op.equals("VECTOR/PLOT") && args.get(1).contains("%")) {
                    String[] names = args.get(1).split("%", -1); if (names.length != 2) throw new IllegalArgumentException("Use xvector%yvector");
                    double[] x = select(names[0]), y = select(names[1]); if (x.length != y.length) throw new IllegalArgumentException("Scatter vectors must have the same length");
                    GraphErrors graph = new GraphErrors(id, x, y); graph.attr().setTitleX(names[0]); graph.attr().setTitleY(names[1]); graph.attr().setDrawOptions("P"); dataset = graph;
                } else {
                    double[] vector = select(args.get(1));
                    if (op.equals("VECTOR/DRAW")) { H1F histogram = Workspace.book1(id, "Bin contents of " + args.get(1), vector.length, .5, vector.length + .5); for (int i = 0; i < vector.length; i++) histogram.setBinContent(i, vector[i]); histogram.attr().setTitleX("Vector element (1-based)"); histogram.attr().setTitleY("Value"); dataset = histogram; }
                    else { double min = Arrays.stream(vector).min().orElseThrow(), max = Arrays.stream(vector).max().orElseThrow(), margin = max == min ? Math.max(.5, Math.abs(min) * .05) : (max - min) * .05; H1F histogram = Workspace.book1(id, "Distribution of " + args.get(1), 100, min - margin, max + margin); for (double value : vector) histogram.fill(value); histogram.attr().setTitleX(args.get(1)); histogram.attr().setTitleY("Counts"); dataset = histogram; }
                }
                workspace.put(id, dataset); draw.accept(dataset, args.size() == 4);
            }
            case "GRAPH" -> {
                if (args.size() != 4 && args.size() != 5) throw new IllegalArgumentException("GRAPH count xvector yvector [L|P|LP]");
                int count = Commands.integer(args, 1); double[] x = select(args.get(2)), y = select(args.get(3)); if (count < 1 || count > x.length || count > y.length) throw new IllegalArgumentException("GRAPH count must fit both vectors");
                String options = args.size() == 5 ? args.get(4).toUpperCase(Locale.ROOT) : "L"; if (!Set.of("L", "P", "LP").contains(options)) throw new IllegalArgumentException("GRAPH supports L, P, LP");
                GraphErrors graph = new GraphErrors("graph", Arrays.copyOf(x, count), Arrays.copyOf(y, count)); graph.attr().setTitleX(args.get(2)); graph.attr().setTitleY(args.get(3)); graph.attr().setDrawOptions(options); workspace.put("graph", graph); draw.accept(graph, false);
            }
            case "SIGMA" -> sigma(String.join(" ", args.subList(1, args.size())));
            default -> { return false; }
        }
        return true;
    }
    private void sigma(String assignment) {
        int equals = assignment.indexOf('='); if (equals < 1) throw new IllegalArgumentException("SIGMA destination=expression");
        String destination = name(assignment.substring(0, equals).trim()), expression = assignment.substring(equals + 1).trim().toLowerCase(Locale.ROOT);
        var array = Pattern.compile("array\\((\\d+)(?:,(.*))?\\)").matcher(expression.replace(" ", ""));
        double[] result;
        if (array.matches()) {
            int length = Integer.parseInt(array.group(1)); if (length < 1 || length > 1_000_000) throw new IllegalArgumentException("ARRAY size must be 1..1000000"); result = new double[length];
            if (array.group(2) == null) Arrays.fill(result, 1);
            else { String[] range = array.group(2).split("#", -1); if (range.length != 2) throw new IllegalArgumentException("ARRAY uses start#end"); double start = Expression.compile(range[0], List.of()).applyAsDouble(new double[0]), end = Expression.compile(range[1], List.of()).applyAsDouble(new double[0]); for (int i = 0; i < length; i++) result[i] = length == 1 ? start : start + (end - start) * i / (length - 1); }
        } else {
            List<String> columns = new ArrayList<>(values.keySet()); var calculation = Expression.compile(expression, columns);
            Set<String> referenced = new HashSet<>(); var names = Pattern.compile("[a-z][a-z0-9_]*").matcher(expression); while (names.find()) if (values.containsKey(names.group())) referenced.add(names.group());
            int length = 1; for (String ref : referenced) if (values.get(ref).length > 1) { if (length > 1 && length != values.get(ref).length) throw new IllegalArgumentException("SIGMA vector lengths must match (scalars broadcast)"); length = values.get(ref).length; }
            result = new double[length]; double[] row = new double[columns.size()];
            for (int i = 0; i < length; i++) { for (int j = 0; j < row.length; j++) { double[] vector = values.get(columns.get(j)); row[j] = vector[Math.min(i, vector.length - 1)]; } result[i] = calculation.applyAsDouble(row); }
        }
        for (double value : result) if (!Double.isFinite(value)) throw new IllegalArgumentException("SIGMA produced a nonfinite result");
        values.put(destination, result);
    }
}
