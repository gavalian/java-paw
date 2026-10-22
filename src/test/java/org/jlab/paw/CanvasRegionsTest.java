package org.jlab.paw;

import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.Test;
import twig.data.H1F;
import twig.graphics.*;
import twig.widgets.PaveText;
import static org.junit.Assert.*;

public class CanvasRegionsTest {
    @Test public void plotsAdvanceWrapAndSameOverlaysWithoutConsumingTheNextRegion() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Workstation app = new Workstation(true, true, true);
            try {
                TGDataCanvas canvas = find(app, TGDataCanvas.class);
                app.previewCommand("ZONE 2 2"); app.previewCommand("H/PLOT 10");
                assertEquals("10", data(canvas, 0, 0)); assertSame(canvas.region(1), canvas.region()); assertEquals(2, active(app));
                app.previewCommand("HI/PLOT 20 s");
                assertEquals(2, canvas.region(0).getAxisFrame().getDataNodes().size()); assertEquals("20", data(canvas, 0, 1));
                assertTrue(canvas.region(1).getAxisFrame().getDataNodes().isEmpty()); assertEquals(2, active(app));
                app.previewCommand("H/PLOT 30"); assertEquals("30", data(canvas, 1, 0)); assertEquals(3, active(app));
                app.previewCommand("FUNCTION/PLOT 'sin(x)' 0 6.28"); assertEquals("function", data(canvas, 2, 0));
                app.previewCommand("H/PLOT 20"); assertEquals("20", data(canvas, 3, 0)); assertEquals(1, active(app));
                assertSame(canvas.region(0), canvas.region());
                app.previewCommand("H/PLOT 10"); assertEquals(1, canvas.region(0).getAxisFrame().getDataNodes().size());
                assertEquals("30", data(canvas, 1, 0)); assertEquals(2, active(app));
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { app.shutdown(); }
        });
    }
    @Test public void explicitCdAndZoneSelectorOverrideAutomaticProgression() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Workstation app = new Workstation(true, true, true);
            try {
                TGDataCanvas canvas = find(app, TGDataCanvas.class);
                app.previewCommand("ZONE 2 2"); app.previewCommand("H/PLOT 10"); app.previewCommand("CD 3");
                assertEquals(3, active(app)); app.previewCommand("H/PLOT 20 S");
                assertEquals("20", data(canvas, 2, 0)); assertEquals(3, active(app));
                app.previewCommand("H/PLOT 30"); assertEquals("30", data(canvas, 2, 0)); assertEquals(4, active(app));
                ((JSpinner) named(app, "activeZone")).setValue(2); app.previewCommand("H/PLOT 10");
                assertEquals("10", data(canvas, 1, 0)); assertEquals(3, active(app));
                app.previewCommand("ZONE 1 1"); assertEquals(1, active(app));
                assertEquals(1, ((SpinnerNumberModel) ((JSpinner) named(app, "activeZone")).getModel()).getMaximum());
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { app.shutdown(); }
        });
    }
    @Test public void customDrawsAndWidgetsAdvanceButAnnotationsStayWithTheirPlot() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Workstation app = new Workstation(true, false, true);
            try {
                TGDataCanvas canvas = find(app, TGDataCanvas.class); app.zones(2, 1);
                app.draw(new H1F("first", 10, 0, 1), "EP", false);
                PaveText label = new PaveText("Label", .5, .5); app.widget(label, false);
                assertTrue(canvas.region(0).getAxisFrame().getWidgets().contains(label)); assertEquals(2, active(app));
                AtomicReference<TGRegion> configured = new AtomicReference<>(); app.configure(configured::set);
                assertSame(canvas.region(0), configured.get());
                app.draw(new H1F("overlay", 10, 0, 1), "F", true);
                assertEquals(2, canvas.region(0).getAxisFrame().getDataNodes().size()); assertEquals(2, active(app));
                PaveText custom = new PaveText("Custom plot", .5, .5); app.widget(custom, true);
                assertTrue(canvas.region(1).getAxisFrame().getWidgets().contains(custom)); assertEquals(1, active(app));
                PaveText note = new PaveText("Note", .5, .6); app.widget(note, false);
                assertTrue(canvas.region(1).getAxisFrame().getWidgets().contains(note)); assertEquals(1, active(app));
                app.draw(new H1F("replacement", 10, 0, 1), "", false);
                assertEquals("replacement", data(canvas, 0, 0)); assertEquals(1, canvas.region(0).getAxisFrame().getDataNodes().size());
            } finally { app.shutdown(); }
        });
    }
    @Test public void singleRegionKeepsReplacementAndOverlayBehavior() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Workstation app = new Workstation(true, true, true);
            try {
                TGDataCanvas canvas = find(app, TGDataCanvas.class);
                app.previewCommand("H/PLOT 20"); app.previewCommand("H/PLOT 10 S");
                assertEquals(2, canvas.region().getAxisFrame().getDataNodes().size()); assertEquals(1, active(app));
                app.previewCommand("H/PLOT 30"); assertEquals(1, canvas.region().getAxisFrame().getDataNodes().size()); assertEquals("30", data(canvas, 0, 0));
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { app.shutdown(); }
        });
    }
    private static String data(TGDataCanvas canvas, int region, int node) { return canvas.region(region).getAxisFrame().getDataNodes().get(node).getDataSet().getName(); }
    private static int active(Workstation app) { return (Integer) ((JSpinner) named(app, "activeZone")).getValue(); }
    private static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) { if (name.equals(child.getName())) return child; if (child instanceof Container c) { Component result = named(c, name); if (result != null) return result; } }
        return null;
    }
    private static <T extends Component> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) { if (type.isInstance(child)) return type.cast(child); if (child instanceof Container c) { T result = find(c, type); if (result != null) return result; } }
        return null;
    }
}
