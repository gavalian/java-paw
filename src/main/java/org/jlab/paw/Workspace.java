package org.jlab.paw;

import j4np.hipo5.data.*;
import j4np.hipo5.io.*;
import twig.data.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Analysis state, independent of Swing. Histograms are Twig objects; persistence is HIPO. */
public final class Workspace {
    private final Map<String, DataSet> objects = new LinkedHashMap<>();
    private final NtupleStore ntuples = new NtupleStore();
    public NtupleStore ntuples() { return ntuples; }
    private String directory = "//PAWC";
    public String directory() { return directory; }
    public void cd(String path) { directory = normalize(path); }
    public static String normalize(String path) {
        String p = path.replace('\\', '/').trim();
        if (!p.startsWith("//")) p = "//PAWC/" + p;
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (!p.startsWith("//PAWC") || (p.length() > 6 && p.charAt(6) != '/')) throw new IllegalArgumentException("Workspace paths start with //PAWC");
        for (String segment : p.substring(2).split("/")) if (segment.isBlank() || segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("Invalid directory");
        return p;
    }
    public String path(String id) { return id.startsWith("//") ? id : directory + "/" + id; }
    public Map<String, DataSet> objects() { return Collections.unmodifiableMap(objects); }
    public DataSet get(String id) {
        DataSet ds = objects.get(path(id));
        if (ds == null) throw new IllegalArgumentException("Object not found: " + id);
        return ds;
    }
    public void put(String id, DataSet ds) {
        if (id.isBlank() || (!id.startsWith("//") && id.contains("/"))) throw new IllegalArgumentException("Use a simple identifier or full //PAWC path");
        String path = path(id); int slash = path.lastIndexOf('/');
        normalize(path.substring(0, slash));
        String name = path.substring(slash + 1);
        if (name.isBlank() || name.equals(".") || name.equals("..")) throw new IllegalArgumentException("Invalid object name");
        ds.setName(name); objects.put(path, ds);
    }
    public void deleteAll(){objects.keySet().removeIf(k->k.startsWith(directory+"/"));}
    public void delete(String id) { get(id); objects.remove(path(id)); }
    public void copy(String source, String dest) {
        DataSet ds = decode(DataSetSerializer.toJson(get(source))); put(dest, ds);
    }
    static DataSet snapshot(DataSet dataset) { return decode(DataSetSerializer.toJson(dataset)); }
    public static H1F book1(String id, String title, int bins, double min, double max) {
        checkAxis(bins, min, max); return new H1F(id, title, bins, min, max);
    }
    public static void checkAxis(int bins, double min, double max) {
        if (bins < 1 || bins > 100_000 || !Double.isFinite(min) || !Double.isFinite(max) || min >= max) throw new IllegalArgumentException("Axis needs 1..100000 bins and finite min < max");
    }
    public H1F rebin(String id, String output, int factor) {
        H1F source = histogram(id); int n = source.getAxis().getNBins();
        if (factor < 1 || n % factor != 0) throw new IllegalArgumentException("Rebin factor must divide the bin count");
        double[] edges = new double[n / factor + 1];
        for (int i = 0; i < edges.length; i++) edges[i] = source.getAxis().getLimits()[i * factor];
        H1F target = new H1F(output, source.attr().getTitle(), edges);
        for (int i = 0; i < n / factor; i++) {
            double sum = 0, variance = 0;
            for (int j = 0; j < factor; j++) { int b = i * factor + j; sum += source.getBinContent(b); variance += Math.pow(source.getBinError(b), 2); }
            target.setBinContent(i, sum); target.setBinError(i, Math.sqrt(variance));
        }
        var sourceJson = j4np.utils.json.Json.parse(DataSetSerializer.toJson(source)).asObject();
        DataSetSerializer.attributesFromJson(target.attr(), sourceJson);
        var targetJson = j4np.utils.json.Json.parse(DataSetSerializer.toJson(target)).asObject();
        targetJson.set("stats", sourceJson.get("stats"));
        target = DataSetSerializer.deserialize_H1F(targetJson.toString());
        put(output, target); return target;
    }
    public H1F histogram(String id) { if (get(id) instanceof H1F h) return h; throw new IllegalArgumentException("Select a 1-D histogram"); }
    public H1F arithmetic(String a, String b, String output, String operation) {
        H1F x = histogram(a), y = histogram(b);
        if (x.getAxis().getNBins() != y.getAxis().getNBins() || x.getAxis().min() != y.getAxis().min() || x.getAxis().max() != y.getAxis().max()) throw new IllegalArgumentException("Histogram axes must match");
        if (!Arrays.equals(x.getAxis().getLimits(), y.getAxis().getLimits())) throw new IllegalArgumentException("Bin edges must match");
        H1F result = new H1F(output, operation + "(" + a + "," + b + ")", x.getAxis().getLimits().clone());
        for (int i = 0; i < x.getAxis().getNBins(); i++) {
            double v = x.getBinContent(i), w = y.getBinContent(i), ev = x.getBinError(i), ew = y.getBinError(i);
            double value, error;
            switch (operation.toUpperCase(Locale.ROOT)) {
                case "ADD" -> { value = v + w; error = Math.hypot(ev, ew); }
                case "SUB" -> { value = v - w; error = Math.hypot(ev, ew); }
                case "MUL" -> { value = v * w; error = Math.hypot(w * ev, v * ew); }
                case "DIV" -> { if (w == 0) throw new IllegalArgumentException("Zero denominator at bin " + i); value = v / w; error = Math.hypot(ev / w, v * ew / (w * w)); }
                default -> throw new IllegalArgumentException("Operation must be ADD, SUB, MUL, or DIV");
            }
            result.setBinContent(i, value); result.setBinError(i, error);
        }
        put(output, result); return result;
    }
    public void demo() {
        Random random = new Random(42);
        H1F mass = book1("10", "Gaussian sample", 100, -5, 5);
        H1F energy = book1("20", "Exponential sample", 80, 0, 8);
        H2F correlation = new H2F("30", 60, -4, 4, 60, -4, 4);
        correlation.attr().setTitle("Correlated sample"); correlation.attr().setTitleX("x"); correlation.attr().setTitleY("y");
        mass.attr().setTitleX("x"); mass.attr().setTitleY("Counts"); mass.attr().setLineColor(4);
        energy.attr().setTitleX("Energy"); energy.attr().setTitleY("Counts"); energy.attr().setLineColor(2);
        for (int i = 0; i < 15000; i++) { double x = random.nextGaussian(); mass.fill(x); energy.fill(-Math.log(1 - random.nextDouble())); correlation.fill(x, .65 * x + .7 * random.nextGaussian()); }
        put("10", mass); put("20", energy); put("30", correlation);
    }
    /** Reserved group 32000, item 1 = version marker; item 2 = object path; item 3 = Twig dataset JSON. */
    public void save(Path destination) throws IOException {
        if(!ntuples.tuples().isEmpty()){ntuples.write(destination,ntuples.tuples().values(),objects);return;}
        for (DataSet ds : objects.values()) if (!(ds instanceof H1F || ds instanceof H2F || ds instanceof GraphErrors)) throw new IllegalArgumentException("Only histograms and graphs can be saved");
        Path absolute = destination.toAbsolutePath(); Path temp = Files.createTempFile(absolute.getParent(), ".paw-", ".hipo");
        try {
            HipoWriter writer = new HipoWriter();
            if (!writer.open(temp.toString())) throw new IOException("Could not create workspace");
            try {
                Event header = new Event(); header.write(new Node(32000, 1, "PAW-JAVA-WORKSPACE:1")); writer.addEvent(header);
                for (var entry : objects.entrySet()) {
                    String json = DataSetSerializer.toJson(entry.getValue());
                    Event event = new Event(Math.max(1024 * 1024, json.length() * 4 + 1024));
                    event.write(new Node(32000, 2, entry.getKey())); event.write(new Node(32000, 3, json)); writer.addEvent(event);
                }
            } finally { writer.close(); }
            try { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public void load(Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("File not found: " + source);
        HipoReader reader = new HipoReader();
        Workspace loaded = new Workspace();
        try {
            reader.setDebugMode(0); reader.open(source.toString()); Event event = new Event();
            if (!reader.hasNext()) throw new IOException("Not a PAW Java workspace");
            reader.nextEvent(event);
            if (event.scan(32000, 1) < 0 || !event.read(32000, 1).getString().equals("PAW-JAVA-WORKSPACE:1")) throw new IOException("Not a PAW Java workspace; use Open HIPO data for event files");
            while (reader.hasNext()) {
                reader.nextEvent(event);
                if(event.scan(32000,2)<0 && reader.getSchemaFactory().getSchemaList().stream().anyMatch(schema->event.scan(schema.getGroup(),schema.getItem())>=0))continue;
                if (event.scan(32000, 2) < 0 || event.scan(32000, 3) < 0) throw new IOException("Invalid workspace object record");
                String path = event.read(32000, 2).getString();
                if (!path.startsWith("//PAWC/")) throw new IOException("Invalid workspace object path");
                DataSet ds = decode(event.read(32000, 3).getString());
                if (ds == null) throw new IOException("Unsupported workspace object");
                loaded.put(path, ds);
            }
        } finally { reader.close(); }
        List<NtupleStore.Tuple> loadedTuples=schemas(source).isEmpty()?List.of():NtupleStore.read(source,null);
        ntuples.replace(loadedTuples);objects.clear(); objects.putAll(loaded.objects); directory = "//PAWC";
    }
    public static boolean isWorkspace(Path file)throws IOException{HipoReader r=new HipoReader();try{r.setDebugMode(0);r.open(file.toString());Event e=new Event();if(!r.hasNext())return false;r.nextEvent(e);return e.scan(32000,1)>=0;}finally{r.close();}}
    private static DataSet decode(String json) {
        String type = j4np.utils.json.Json.parse(json).asObject().get("class").asString();
        // Twig's generic deserializer currently omits H2F; use its specific implementation.
        if (type.equals("twig.data.H2F")) return DataSetSerializer.deserialize_H2F(json);
        return DataSetSerializer.deserialize(json);
    }
    public static List<Schema> schemas(Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("File not found: " + source);
        HipoReader reader = new HipoReader();
        try { reader.setDebugMode(0); reader.open(source.toString()); return reader.getSchemaFactory().getSchemaList().stream().map(Schema::copy).toList(); }
        finally { reader.close(); }
    }
    /** Streams bank rows without retaining the input file in memory. First/count refer to bank rows, not events. */
    public static long rows(Path source, Schema schema, long first, long count, Consumer<double[]> consumer) {
        if (first < 0 || count < 1) throw new IllegalArgumentException("First row >= 0, row count >= 1");
        HipoReader reader = new HipoReader();
        try {
            reader.setDebugMode(0); reader.open(source.toString()); if(reader.getErrorCode()!=HipoReader.NO_ERROR)throw new IllegalArgumentException("HIPO index is invalid; use NTUPLE/RECOVER before reading rows"); Event event = new Event(); Bank bank = new Bank(schema);
            long index = 0, visited = 0;
            while (reader.hasNext() && visited < count) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Cancelled");
                reader.nextEvent(event); event.read(bank);
                for (int row = 0; row < bank.getRows() && visited < count; row++, index++) {
                    if (index < first) continue;
                    double[] values = new double[schema.getElements()];
                    for (int column = 0; column < values.length; column++) { if(schema.getType(schema.getElementName(column))==8){long exact=bank.getLong(schema.getElementName(column),row);if(exact>9007199254740991L||exact< -9007199254740991L)throw new IllegalArgumentException("Ntuple integer exceeds exact numeric expression precision");} values[column] = bank.getValue(column, row); }
                    consumer.accept(values); visited++;
                }
            }
            return visited;
        } finally { reader.close(); }
    }
    public static DataSet project(Path source, Schema schema, String id, String x, String y, String cut,
            int bins, double min, double max, int yBins, double yMin, double yMax, long first, long count) {
        checkAxis(bins, min, max);
        var columns = schema.getEntryList(); var xExpr = Expression.compile(x, columns);
        var yExpr = y.isBlank() ? null : Expression.compile(y, columns);
        var selection = Expression.compile(cut.isBlank() ? "true" : cut, columns);
        if (yExpr != null) { checkAxis(yBins, yMin, yMax); if ((long) bins * yBins > 4_000_000) throw new IllegalArgumentException("2-D histogram limited to four million bins"); }
        DataSet histogram = yExpr == null ? book1(id, x, bins, min, max) : new H2F(id, bins, min, max, yBins, yMin, yMax);
        histogram.attr().setTitleX(x); histogram.attr().setTitleY(yExpr == null ? "Counts" : y);
        rows(source, schema, first, count, row -> {
            if (!Expression.truth(selection.applyAsDouble(row))) return;
            double xv = xExpr.applyAsDouble(row), yv = yExpr == null ? 0 : yExpr.applyAsDouble(row);
            if (!Double.isFinite(xv) || !Double.isFinite(yv)) return;
            if (histogram instanceof H1F h) h.fill(xv); else ((H2F) histogram).fill(xv, yv);
        });
        return histogram;
    }
}
