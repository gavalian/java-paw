package org.jlab.paw;

import j4np.hipo5.data.*;
import j4np.hipo5.io.HipoWriter;
import twig.data.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class AnalysisTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void expressionsAndCuts() {
        var columns = List.of("px", "py", "pid"); double[] row = {3, 4, 11};
        assertEquals(5, Expression.compile("sqrt(px^2+py^2)", columns).applyAsDouble(row), 1e-12);
        assertEquals(1, Expression.compile("pid==11 && (px>=3 || py<0) && !(py==0)", columns).applyAsDouble(row), 0);
        assertEquals(0, Expression.compile("pid!=11 || sqrt(px^2+py^2)>6", columns).applyAsDouble(row), 0);
        assertEquals(512, Expression.compile("2^3^2", columns).applyAsDouble(row), 0);
        assertEquals(-4, Expression.compile("-2^2", columns).applyAsDouble(row), 0);
        assertEquals(.002, Expression.compile("2e-3", columns).applyAsDouble(row), 1e-15);
        assertFalse(Expression.truth(Double.NaN));
        try { Expression.compile("unknown+1", columns); fail(); } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Unknown column")); }
        try { Expression.compile("px >", columns); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void histogramOperationsPreserveErrors() {
        Workspace ws = new Workspace(); H1F h = Workspace.book1("1", "Original", 4, 0, 4);
        for (int i = 0; i < 4; i++) { h.setBinContent(i, i + 1); h.setBinError(i, Math.sqrt(i + 1)); }
        ws.put("1", h); H1F rebinned = ws.rebin("1", "2", 2);
        assertEquals(3, rebinned.getBinContent(0), 0); assertEquals(7, rebinned.getBinContent(1), 0);
        assertEquals(Math.sqrt(3), rebinned.getBinError(0), 1e-6); assertEquals(10, rebinned.integral(), 1e-12);
        ws.copy("1", "3"); assertNotSame(h, ws.get("3"));
        H1F sum = ws.arithmetic("1", "3", "4", "ADD"); assertEquals(20, sum.integral(), 1e-12); assertEquals(Math.sqrt(2), sum.getBinError(0), 1e-6);
        try { ws.rebin("1", "bad", 3); fail(); } catch (IllegalArgumentException expected) { }
        try { ws.arithmetic("1", "2", "bad", "ADD"); fail(); } catch (IllegalArgumentException expected) { }
        assertFalse(ws.objects().containsKey(ws.path("bad")));
        H1F variable = new H1F("v", new double[]{0, 1, 3, 6, 10}); variable.fill(.5); variable.fill(2); ws.put("v", variable);
        H1F grouped = ws.rebin("v", "v2", 2); assertArrayEquals(new double[]{0, 3, 10}, grouped.getAxis().getLimits(), 0); assertEquals(variable.getEntries(), grouped.getEntries());
    }
    @Test public void hipoWorkspaceRoundTripAndReplacement() throws Exception {
        Workspace ws = new Workspace(); ws.cd("results");
        H1F h = Workspace.book1("mass", "Mass spectrum", 8, -2, 2); h.fill(.5, 3); h.attr().setTitleX("GeV"); h.attr().setLineColor(4); ws.put("mass", h);
        H2F h2 = new H2F("xy", 4, 0, 4, 5, 0, 5); h2.fill(1, 2); ws.put("xy", h2);
        Path file = temp.newFile("workspace.hipo").toPath(); ws.save(file); ws.save(file);
        Workspace restored = new Workspace(); restored.demo(); restored.load(file);
        assertEquals(2, restored.objects().size()); assertEquals("//PAWC", restored.directory());
        H1F back = (H1F) restored.get("//PAWC/results/mass"); assertEquals(3, back.integral(), 1e-12); assertEquals("Mass spectrum", back.attr().getTitle()); assertEquals("GeV", back.attr().getTitleX()); assertEquals(4, back.attr().getLineColor());
        assertTrue(restored.get("//PAWC/results/xy") instanceof H2F);
        Workspace empty = new Workspace(); Path emptyFile = temp.newFile("empty.hipo").toPath(); empty.save(emptyFile); restored.load(emptyFile); assertTrue(restored.objects().isEmpty());
    }
    private Schema schema() { return new Schema.SchemaBuilder("TEST::particles", 42, 1).addEntry("px", "F", "X").addEntry("py", "F", "Y").addEntry("pid", "I", "Particle").build(); }
    private Path dataFile() throws Exception {
        Path file = temp.newFile("events.hipo").toPath(); Schema schema = schema(); HipoWriter writer = new HipoWriter(); writer.getSchemaFactory().addSchema(schema); assertTrue(writer.open(file.toString()));
        try { for (int event = 0; event < 3; event++) { Bank bank = new Bank(schema, 2); for (int row = 0; row < 2; row++) { bank.putFloat("px", row, event * 2 + row); bank.putFloat("py", row, 4); bank.putInt("pid", row, row == 0 ? 11 : 211); } Event e = new Event(); e.write(bank); writer.addEvent(e); } } finally { writer.close(); }
        return file;
    }
    @Test public void streamedHipoProjectionUsesCutsAndRowRange() throws Exception {
        Path file = dataFile(); Schema schema = Workspace.schemas(file).get(0); assertEquals("TEST::particles", schema.getName());
        H1F histogram = (H1F) Workspace.project(file, schema, "100", "px", "", "pid==11 && px>=2", 10, 0, 10, 1, 0, 1, 0, 100);
        assertEquals(2, histogram.integral(), 0);
        H1F sliced = (H1F) Workspace.project(file, schema, "101", "px", "", "", 10, 0, 10, 1, 0, 1, 1, 3);
        assertEquals(3, sliced.integral(), 0); assertEquals(2.5, sliced.getMean(), .01); // Twig statistics use bin centers.
        assertTrue(Workspace.project(file, schema, "102", "px", "py", "pid==11", 10, 0, 10, 10, 0, 10, 0, 100) instanceof H2F);
        Workspace ws = new Workspace(); ws.demo(); try { ws.load(file); fail(); } catch (java.io.IOException expected) { assertEquals(3, ws.objects().size()); }
    }
    @Test public void macroAndCommandValidation() throws Exception {
        Workspace ws = new Workspace(); List<String> output = new ArrayList<>(); List<DataSet> drawn = new ArrayList<>();
        Commands commands = new Commands(ws, new Commands.View() { public void plot(DataSet ds, boolean same) { drawn.add(ds); } public void zones(int c, int r) { } public void zone(int i) { } public void export(Path p) { } }, output::add);
        commands.execute("H/BOOK 1 'A quoted title' 10 0 10"); commands.execute("HISTOGRAM/FILL 1 2 3"); commands.execute("H/PLOT 1");
        assertEquals("A quoted title", ws.get("1").attr().getTitle()); assertEquals(3, ws.histogram("1").integral(), 0); assertEquals(1, drawn.size());
        Path macro = temp.newFile("demo.kumac").toPath(); Files.writeString(macro, "# comment\nH/COPY 1 2\nHISTOGRAM/ADD 1 2 3\n"); commands.execute("MACRO/EXEC " + Commands.quote(macro.toString())); assertEquals(6, ws.histogram("3").integral(), 0);
        String path = "a path/with \"quotes\" and \\slash"; assertEquals(path, Commands.tokenize(Commands.quote(path)).get(0));
        try { commands.execute("H/BOOK bad title 0 1 2"); fail(); } catch (IllegalArgumentException expected) { }
        try { commands.execute("H/PLOT 1 INVALID"); fail(); } catch (IllegalArgumentException expected) { }
        try { Commands.tokenize("'unclosed"); fail(); } catch (IllegalArgumentException expected) { }
        try { ws.cd("//OTHER"); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void gaussianFitUsesTwigMinuit() throws Exception {
        Workspace ws = new Workspace(); ws.demo(); List<DataSet> plots = new ArrayList<>();
        Commands commands = new Commands(ws, new Commands.View() { public void plot(DataSet ds, boolean same) { plots.add(ds); } public void zones(int c, int r) { } public void zone(int i) { } public void export(Path p) { } }, text -> { });
        commands.execute("H/FIT 10 G"); assertEquals(2, plots.size());
        twig.math.F1D fit = (twig.math.F1D) plots.get(1);
        assertEquals(0, fit.getParameter(1), .05); assertEquals(1, fit.getParameter(2), .05); assertTrue(Double.isFinite(fit.getChiSquare()));
    }
    @Test public void graphicsExportProducesReadablePngAndPdf() throws Exception {
        Path png = temp.newFile("plot.png").toPath(), pdf = temp.newFile("plot.pdf").toPath();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            twig.graphics.TGDataCanvas canvas = new twig.graphics.TGDataCanvas(); canvas.setSize(640, 480); canvas.region().draw(Workspace.book1("1", "Test", 10, 0, 10));
            try { GraphicsExport.write(canvas, png); GraphicsExport.write(canvas, pdf); } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
        });
        var image = javax.imageio.ImageIO.read(png.toFile()); assertEquals(640, image.getWidth()); assertEquals(480, image.getHeight());
        assertTrue(Files.size(pdf) > 1000); assertEquals("%PDF", new String(Files.readAllBytes(pdf), 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }
}
