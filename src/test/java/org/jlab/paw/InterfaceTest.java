package org.jlab.paw;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import twig.graphics.TGDataCanvas;
import org.junit.Test;
import static org.junit.Assert.*;

public class InterfaceTest {
    @Test public void themeSwitchAndWindowLifecyclePreserveSharedAnalysis() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Theme.install(false); Workstation workstation = new Workstation(false, true, true);
            workstation.setSize(1440, 1000); layout(workstation); workstation.tile(); layout(workstation);
            TGDataCanvas canvas = find(workstation, TGDataCanvas.class);
            assertNotNull(canvas); assertNull(find(workstation, JDesktopPane.class)); assertNull(find(workstation, JInternalFrame.class));
            assertEquals(4, workstation.windows().size());
            assertEquals(1, canvas.region().getAxisFrame().getDataNodes().size()); Object plotted = canvas.region().getAxisFrame().getDataNodes().get(0);
            for (boolean modern : new boolean[]{true, false}) {
                workstation.applyTheme(modern); layout(workstation);
                assertEquals(4, workstation.windows().size()); assertSame(plotted, canvas.region().getAxisFrame().getDataNodes().get(0));
                for (WorkstationWindow window : workstation.windows()) { assertTrue(window.isVisible()); assertTrue(window.getContentPane().getHeight() > 150); assertTrue(window.getContentPane().getWidth() > 400); }
                assertTrue(canvas.getWidth() > 600); assertTrue(canvas.getHeight() > 400);
            }
            WorkstationWindow browser=workstation.windows().get(1); browser.close();assertFalse(browser.isVisible());browser.toFront();assertTrue(browser.isVisible());
            workstation.panel("Test commands",java.util.Map.of("List","H/LIST"));assertEquals(5,workstation.windows().size());
            workstation.windows().get(4).close();assertEquals(4,workstation.windows().size());
            workstation.windows().get(0).close();assertTrue(workstation.windows().isEmpty());
            workstation.shutdown();
        });
    }
    @Test public void macroBrowserStartsAtLaunchDirectoryAndExecutesBothFormats() throws Exception {
        Path launch = Files.createTempDirectory("paw launch ");
        Path macros = Files.createDirectory(launch.resolve("macros folder"));
        Files.writeString(macros.resolve("command example.kumac"), "1D browser_test 'Browser test' 10 0 10\nH/PLOT browser_test\n");
        Files.writeString(macros.resolve("java example.jmacro"), "paw.command(\"1D java_test 'Java test' 10 0 10\"); paw.command(\"H/PLOT java_test\");");
        Files.writeString(macros.resolve("ignored.txt"), "not a macro");
        Workstation[] app = new Workstation[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                String original = System.getProperty("user.dir");
                try { System.setProperty("user.dir", launch.toString()); app[0] = new Workstation(false, false, true); }
                finally { System.setProperty("user.dir", original); }
                Workstation workstation = app[0]; workstation.setSize(1440,1000); layout(workstation); workstation.tile(); layout(workstation);
                JList<?> categories = (JList<?>) named(workstation, "browserClasses");
                categories.setSelectedValue("Macro", true);
                assertEquals("Path: " + launch, ((JLabel) named(workstation, "browserPath")).getText());
                doubleClick(workstation, "macros folder/");
                assertEquals("Path: " + macros, ((JLabel) named(workstation, "browserPath")).getText());
                JList<?> entries = (JList<?>) named(workstation, "browserObjects");
                assertEquals(3, entries.getModel().getSize()); assertEquals("../", entries.getModel().getElementAt(0));
                categories.setSelectedValue("PAWC", true); categories.setSelectedValue("Macro", true);
                assertEquals("Path: " + macros, ((JLabel) named(workstation, "browserPath")).getText());
                doubleClick(workstation, "../"); doubleClick(workstation, "macros folder/");
                doubleClick(workstation, "command example.kumac");
            });
            awaitMacro(app[0]);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(1, find(app[0], TGDataCanvas.class).region().getAxisFrame().getDataNodes().size());
                doubleClick(app[0], "java example.jmacro");
            });
            awaitMacro(app[0]);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(1, find(app[0], TGDataCanvas.class).region().getAxisFrame().getDataNodes().size());
                assertEquals("Path: " + macros, ((JLabel) named(app[0], "browserPath")).getText());
            });
        } finally {
            if (app[0] != null) app[0].shutdown();
            try (var files = Files.walk(launch)) { for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }
    private static void awaitMacro(Workstation app) throws Exception {
        AtomicBoolean complete = new AtomicBoolean(); long end = System.nanoTime() + 15_000_000_000L;
        while (!complete.get() && System.nanoTime() < end) {
            SwingUtilities.invokeAndWait(() -> {
                String status = ((JLabel) named(app, "operationStatus")).getText();
                assertFalse(status, status.startsWith("Operation ended:"));
                complete.set(status.contains("Command complete"));
            });
            if (!complete.get()) Thread.sleep(25);
        }
        assertTrue("Macro did not complete", complete.get());
    }
    private static void doubleClick(Workstation app, String entry) {
        JList<?> list = (JList<?>) named(app, "browserObjects"); list.setSelectedValue(entry, true); assertEquals(entry, list.getSelectedValue());
        layout(app); Rectangle bounds = list.getCellBounds(list.getSelectedIndex(), list.getSelectedIndex());
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, bounds.x + 8, bounds.y + 8, 2, false, MouseEvent.BUTTON1));
    }
    private static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) { Component result = named(container, name); if (result != null) return result; }
        }
        return null;
    }
    private static void layout(Container parent) { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container); }
    private static <T extends Component> T find(Container parent, Class<T> type) { for (Component child : parent.getComponents()) { if (type.isInstance(child)) return type.cast(child); if (child instanceof Container container) { T result = find(container, type); if (result != null) return result; } } return null; }
}
