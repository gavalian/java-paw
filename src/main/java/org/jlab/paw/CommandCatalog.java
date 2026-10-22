package org.jlab.paw;

import java.util.*;

/** Help for the commands implemented by this workstation, grouped for browsing. */
public final class CommandCatalog {
    private CommandCatalog() { }
    public record Entry(String group, String command, String arguments, String description) {
        public String usage() { return command + (arguments.isEmpty() ? "" : " " + arguments); }
        @Override public String toString() { return command.substring(command.lastIndexOf('/') + 1); }
    }
    private static final List<Entry> ENTRIES = """
        KUIP|HELP|[command or group]|Browse all commands, a command group, or an individual command. Group/HELP is also accepted.
        KUIP|CHELP|[command or group]|Display help for a valid command or group path.
        KUIP|USAGE|[command or group]|Show command syntax without the detailed descriptions.
        KUIP|DEMO||Create reproducible sample histograms 10, 20, and 30 and plot the first.
        KUIP|CDIR|[directory]|Change the current memory directory under //PAWC, or display it when no directory is supplied.
        HISTOGRAM|HISTOGRAM/LIST||List objects in memory with their paths, types, and titles.
        HISTOGRAM|HISTOGRAM/CREATE/1D|id 'title' bins min max|Create an empty one-dimensional histogram. H/BOOK, HI/BOOK, HISTO/BOOK, HISTOGRAM/BOOK, and 1D are aliases. Bins must be positive and min must be less than max.
        HISTOGRAM|HISTOGRAM/CREATE/2D|id 'title' xbins xmin xmax ybins ymin ymax|Create an empty two-dimensional histogram. 2D is an alias. Both axes require positive bin counts and increasing limits; at most four million bins are supported.
        HISTOGRAM|HISTOGRAM/FILL|id x [y] [weight]|Fill a histogram. A 1D histogram takes x and an optional weight; a 2D histogram requires x and y, followed by an optional weight. Default weight is 1.
        HISTOGRAM|HISTOGRAM/PLOT|id [S]|Plot a histogram, then advance to the next graphics region, wrapping after the last. S overlays the most recent plot without advancing. CD selects a region explicitly.
        HISTOGRAM|HISTOGRAM/COPY|source dest|Copy a histogram to a new identifier.
        HISTOGRAM|HISTOGRAM/RESET|id|Clear histogram contents and redraw it.
        HISTOGRAM|HISTOGRAM/DELETE|id|Remove the selected histogram from memory.
        HISTOGRAM|HISTOGRAM/REBIN|source dest factor|Merge adjacent bins into a new histogram and plot it. The factor must divide the original bin count.
        HISTOGRAM|HISTOGRAM/ADD|first second dest|Add two histograms with compatible binning into a new histogram, propagating errors.
        HISTOGRAM|HISTOGRAM/SUB|first second dest|Subtract the second histogram from the first into a new histogram, propagating errors.
        HISTOGRAM|HISTOGRAM/MUL|first second dest|Multiply compatible histograms into a new histogram, propagating errors.
        HISTOGRAM|HISTOGRAM/DIV|first second dest|Divide the first histogram by the second. Zero denominators are rejected before installing the result.
        HISTOGRAM|HISTOGRAM/FIT|id G/E/P0/P1|Fit a histogram. Models: G (Gaussian), E (exponential), P0 (constant), P1 (linear). Parameters and chi-square are written to the transcript.
        HISTOGRAM|HISTOGRAM/SMOOTH|source dest|Create and plot a histogram smoothed with a three-bin weighted average.
        HISTOGRAM|HISTOGRAM/SHOW|id|Print entries, integral, mean, RMS, underflow, and overflow for a 1D histogram.
        HISTOGRAM|HISTOGRAM/PUT_VECTOR/CONTENT|id vector|Replace histogram bin contents with vector values. The vector length must equal the bin count.
        HISTOGRAM|HISTOGRAM/GET_VECTOR/CONTENT|id vector|Copy histogram bin contents into a vector.
        VECTOR|VECTOR/CREATE|name(size) [R or I] [values...]|Create a one-dimensional vector. R stores real values and I requires integer initial values. Unspecified elements are zero; size defaults to 1.
        VECTOR|VECTOR/INPUT|name values...|Replace all vector values. Supply exactly one value for every element.
        VECTOR|VECTOR/LIST||List vector names and lengths.
        VECTOR|VECTOR/PRINT|name|Print vector values. One-based indices and slices such as sample(2:5) are accepted.
        VECTOR|VECTOR/COPY|source dest|Copy a vector or slice to another vector.
        VECTOR|VECTOR/DELETE|name|Delete a vector. Use * to delete all vectors.
        VECTOR|VECTOR/DRAW|vector [id] [S]|Draw vector values as histogram bin contents. S overlays the plot.
        VECTOR|VECTOR/PLOT|vector [id] [S]|Histogram the distribution of vector values; x%y creates a scatter plot. S overlays the plot.
        VECTOR|VECTOR/HFILL|vector id|Fill an existing 1D histogram with vector samples.
        VECTOR|VBIAS|vector scalar dest|Add a scalar to each source vector element and store the result in dest.
        VECTOR|VSCALE|vector scalar dest|Multiply each source vector element by a scalar and store the result in dest.
        VECTOR|VADD|first second dest|Add corresponding vector elements into dest.
        VECTOR|VMULTI|first second dest|Multiply corresponding vector elements into dest.
        VECTOR|VSUBTR|first second dest|Subtract corresponding elements of the second vector from the first into dest.
        VECTOR|VDIVID|first second dest|Divide corresponding vector elements into dest; zero denominators are rejected.
        VECTOR|SIGMA|dest=expression|Evaluate the supported scalar/one-dimensional SIGMA subset. Example: sample=array(10,0#1).
        VECTOR|GRAPH|count xvector yvector [L, P, or LP]|Create a graph from vector samples. Drawing options: L (lines), P (points), LP (both). Default: L.
        FUNCTION|FUNCTION/PLOT|'expression' min max|Plot a function of x over increasing limits. Example: FUNCTION/PLOT 'sin(x)' 0 6.28.
        GRAPHICS|SET|attribute value|Set HCOL/PCOL (histogram/graph color) or HWID/PWID (line width). Basic colors use indices 0..7; widths use 1..10. HCOL also supports the documented fill/border encodings.
        GRAPHICS|ZONE|columns rows|Divide the canvas into up to 16 graphics regions. Fresh plots automatically advance through the regions and wrap; overlays stay on their plot.
        GRAPHICS|CD|region|Select a graphics region, numbered from 1.
        GRAPHICS|GRAPHICS/EXPORT|'file.png'|Export active graphics to PNG, vector PDF, or raster PostScript/EPS according to the file extension.
        WORKSPACE|WORKSPACE/SAVE|'file.hipo'|Save histograms and graphs with their memory paths to an atomic HIPO workspace snapshot.
        WORKSPACE|WORKSPACE/OPEN|'file.hipo'|Replace memory with a validated workspace snapshot. Invalid files preserve existing objects.
        MACRO|MACRO/EXEC|'file.kumac' [Java macro arguments...]|Run a .kumac command macro or a .jmacro Java method body. Java macro arguments follow the filename; command macros take only a filename. Nested paths resolve relative to the parent macro; nesting is limited to eight levels. Java macros require JDK 21+.
        MACRO|MACRO/DELAY|name|Ask for the screen delay in seconds and store it in name. Positive values advance automatically, negative values ask for Continue, and zero advances immediately.
        MACRO|MACRO/PAGE|seconds 'prompt'|Wait between tutorial screens: positive seconds delay, negative seconds display Continue, and zero continues immediately.
        MACRO|READ|name ['prompt']|Request macro input and store it in name. Enter retains the previous value; reference values as [name].
        """.lines().filter(line -> !line.isBlank()).map(line -> {
            String[] parts = line.strip().split("\\|", 4);
            return new Entry(parts[0], parts[1], parts[2], parts[3]);
        }).collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toCollection(ArrayList::new), list -> { NtupleCommands.DEFINITIONS.forEach(d->list.add(d.entry())); return List.copyOf(list); }));
    public static List<Entry> entries() { return ENTRIES; }
    public static List<String> groups() { return List.of("KUIP", "HISTOGRAM", "VECTOR", "FUNCTION", "GRAPHICS", "WORKSPACE", "MACRO", "NTUPLE", "MASK"); }
    static String normalize(String path) {
        String p = path.strip().toUpperCase(Locale.ROOT).replaceAll("^/+|/+$", "");
        p = switch (p) { case "H", "HI", "HISTO" -> "HISTOGRAM"; case "V" -> "VECTOR"; case "N", "NT", "NTUP", "NTUPLES" -> "NTUPLE"; case "GENERAL" -> "KUIP"; default -> p; };
        return Commands.canonical(p);
    }
    public static List<Entry> matching(String path) {
        String p = normalize(path);
        List<Entry> result = ENTRIES.stream().filter(e -> p.isEmpty() || e.group().equals(p) || e.command().equals(p) || e.command().startsWith(p + "/")).toList();
        if(p.equals("MASK")) result=ENTRIES.stream().filter(e->e.group().equals("MASK")).toList();
        if (result.isEmpty()) throw new IllegalArgumentException("Unknown command or group: " + path + ". Type HELP for available groups.");
        return result;
    }
    public static String text(String path, boolean usageOnly) {
        String p = normalize(path); List<Entry> entries = matching(p);
        StringBuilder text = new StringBuilder(p.isEmpty() ? "PAW++ Command Reference\n" : p + "\n");
        if (p.isEmpty()) text.append("Groups: ").append(String.join(", ", groups())).append("\n\n");
        for (Entry entry : entries) { text.append(entry.usage()).append('\n'); if (!usageOnly) text.append("  ").append(entry.description()).append("\n\n"); }
        return text.append("\nHELP group or group/HELP opens a group. HELP command opens command details.\nH/, HI/, HISTO/ and V/ aliases are accepted. Identifiers resolve in the current memory or Ntuple directory.\nQuote titles and file paths containing spaces. Required arguments open a parameter panel when omitted in interactive commands.").toString();
    }
}
