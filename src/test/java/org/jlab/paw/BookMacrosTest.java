package org.jlab.paw;

import java.nio.file.*;
import java.util.*;
import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.*;
import twig.data.*;
import twig.config.TStyle;

public class BookMacrosTest {
    private static final class Plots implements Commands.View {
        final List<DataSet> datasets = new ArrayList<>();
        public void plot(DataSet ds, boolean same) { datasets.add(ds); }
        public void zones(int columns, int rows) { }
        public void zone(int index) { }
        public void export(Path file) { }
    }
    @Test public void everyBookMacroRunsIndependentlyAndMasterResolvesItsChildren() throws Exception {
        List<Path> macros;
        try (var paths = Files.list(Path.of("macros"))) { macros = paths.filter(path -> path.toString().endsWith(".kumac")).sorted().toList(); }
        assertEquals(7, macros.size());
        for (Path macro : macros) { Workspace workspace = new Workspace(); Plots plots = new Plots(); new Commands(workspace, plots, text -> { }).execute("MACRO/EXEC " + Commands.quote(macro.toAbsolutePath().toString())); assertTrue(macro.toString(), !plots.datasets.isEmpty()); }
    }
    @Test public void vectorsPreserveBookValuesAndSigmaGraphCoordinates() throws Exception {
        Workspace workspace = new Workspace(); Plots plots = new Plots(); Commands commands = new Commands(workspace, plots, text -> { });
        commands.execute("MACRO/EXEC macros/01_vectors.kumac");
        H1F v1 = workspace.histogram("//PAWC/book_vectors/1101"); assertEquals(16, v1.integral(), 0); assertEquals(0, v1.getBinContent(9), 0); assertEquals(6, v1.getBinContent(5), 0);
        assertEquals(105, workspace.histogram("//PAWC/book_vectors/1103").integral(), 0); assertEquals(110, workspace.histogram("//PAWC/book_vectors/1104").integral(), 0);
        commands.execute("MACRO/EXEC macros/02_sigma_graphs.kumac");
        GraphErrors graph = (GraphErrors) workspace.get("//PAWC/book_sigma/sine_curve"); DataPoint point = new DataPoint(); graph.getPoint(point, 50);
        assertEquals(point.x * Math.sin(point.x), point.y, 1e-5); assertEquals("L", graph.attr().getDrawOptions()); assertEquals(100, graph.getSize(0));
    }
    @Test public void histogramFillingAndColorsAreCorrectAndEarlierPlotsStayStable() throws Exception {
        Workspace workspace = new Workspace(); Plots plots = new Plots(); Commands commands = new Commands(workspace, plots, text -> { });
        commands.execute("MACRO/EXEC macros/03_histogram_filling.kumac");
        assertEquals(32, workspace.histogram("//PAWC/book_filling/1302").integral(), 0); assertEquals(36, workspace.histogram("//PAWC/book_filling/1304").integral(), 0);
        plots.datasets.clear(); commands.execute("MACRO/EXEC macros/04_histogram_colors.kumac"); assertEquals(4, plots.datasets.size());
        assertRgb(Color.BLACK, plots.datasets.get(0).attr().getLineColor()); assertEquals(-1, plots.datasets.get(0).attr().getFillColor());
        assertRgb(Color.BLUE, plots.datasets.get(1).attr().getLineColor()); assertRgb(Color.BLACK, plots.datasets.get(2).attr().getFillColor());
        assertRgb(Color.RED, plots.datasets.get(3).attr().getLineColor()); assertRgb(Color.GREEN, plots.datasets.get(3).attr().getFillColor());
        assertNotSame(plots.datasets.get(0), plots.datasets.get(3));
    }
    private static void assertRgb(Color expected, int encoded) { Color actual = TStyle.getInstance().getPalette().getColor(encoded); assertEquals(expected.getRed(), actual.getRed()); assertEquals(expected.getGreen(), actual.getGreen()); assertEquals(expected.getBlue(), actual.getBlue()); }
    @Test public void vectorValidationDoesNotInstallPartialResults() throws Exception {
        Workspace workspace = new Workspace(); Plots plots = new Plots(); Commands commands = new Commands(workspace, plots, text -> { });
        commands.execute("V/CREATE a(3) R 1 2 3"); commands.execute("V/CREATE b(3) R 1 0 3");
        commands.execute("# A comment containing the book's apostrophe");
        commands.execute("V/PRINT a | The book's inline comment");
        assertEquals(List.of("SIGMA", "bool=true||false"), Commands.tokenize("SIGMA bool=true||false"));
        try { commands.execute("VDIVID a b quotient"); fail(); } catch (IllegalArgumentException expected) { }
        try { commands.execute("V/PRINT quotient"); fail(); } catch (IllegalArgumentException expected) { }
        try { commands.execute("V/DRAW a(0)"); fail(); } catch (IllegalArgumentException expected) { }
        try { commands.execute("SET HCOL 1209"); fail(); } catch (IllegalArgumentException expected) { }
        commands.execute("SIGMA negative=-2^2"); commands.execute("V/DRAW negative result"); assertEquals(-4, workspace.histogram("result").getBinContent(0), 0);
    }
}
