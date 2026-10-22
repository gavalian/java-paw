package org.jlab.paw;

import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.swing.*;
import org.junit.Test;
import twig.data.*;
import static org.junit.Assert.*;

public class CommandArgumentsTest {
    private static class View implements Commands.View {
        int requests;
        public void plot(DataSet ds, boolean same) { }
        public void zones(int columns, int rows) { }
        public void zone(int index) { }
        public void export(Path file) { }
    }
    @Test public void incompleteBookPrefillsAndExecutesWithoutLosingQuotes() throws Exception {
        Workspace workspace = new Workspace();
        View view = new View() {
            public List<String> requestArguments(CommandArguments.Spec spec, List<String> supplied) {
                requests++;
                assertEquals("HISTOGRAM/CREATE/1D", spec.command());
                assertEquals(List.of("42", "Title with 'quotes' and spaces"), supplied);
                assertEquals(5, spec.required()); assertEquals("100", spec.defaults().get(2));
                return List.of(supplied.get(0), supplied.get(1), "20", "-5", "5");
            }
        };
        Commands commands = new Commands(workspace, view, text -> { });
        commands.executeInteractive("hi/book 42 \"Title with 'quotes' and spaces\"");
        assertEquals(1, view.requests); assertEquals("Title with 'quotes' and spaces", workspace.get("42").attr().getTitle());
        assertEquals(20, workspace.histogram("42").getAxis().getNBins());
        commands.executeInteractive("HI/BOOK 43 'Complete' 10 0 1"); assertEquals(1, view.requests);
    }
    @Test public void bareBookAndPartialCopyRequestAllMissingParameters() throws Exception {
        Workspace workspace = new Workspace();
        View view = new View() {
            public List<String> requestArguments(CommandArguments.Spec spec, List<String> supplied) {
                requests++;
                if (spec.command().endsWith("1D")) { assertTrue(supplied.isEmpty()); return List.of("1", "", "10", "0", "1"); }
                assertEquals("HISTOGRAM/COPY", spec.command()); assertEquals(List.of("1"), supplied); return List.of("1", "2");
            }
        };
        Commands commands = new Commands(workspace, view, text -> { });
        commands.executeInteractive("HI/BOOK"); commands.executeInteractive("H/COPY 1");
        assertEquals(2, view.requests); assertNotSame(workspace.get("1"), workspace.get("2"));
    }
    @Test public void cancelAndNoninteractiveCommandsPreserveWorkspace() throws Exception {
        Workspace workspace = new Workspace();
        View view = new View() {
            public List<String> requestArguments(CommandArguments.Spec spec, List<String> supplied) { requests++; return null; }
        };
        Commands commands = new Commands(workspace, view, text -> { });
        try { commands.executeInteractive("H/BOOK"); fail(); } catch (CancellationException expected) { }
        assertTrue(workspace.objects().isEmpty());
        try { commands.execute("H/BOOK"); fail(); } catch (IllegalArgumentException expected) { }
        assertEquals(1, view.requests);
        Commands headless = new Commands(workspace, new View(), text -> { });
        try { headless.executeInteractive("HI/BOOK"); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void twoDimensionalFillRequestsMissingY() throws Exception {
        Workspace workspace = new Workspace();
        View view = new View() {
            public List<String> requestArguments(CommandArguments.Spec spec, List<String> supplied) {
                requests++; assertEquals(3, spec.required()); assertEquals(List.of("2d", "0.25"), supplied);
                return List.of("2d", "0.25", "0.75", "1");
            }
        };
        Commands commands = new Commands(workspace, view, text -> { });
        commands.execute("2D 2d 'Two dimensions' 10 0 1 10 0 1"); commands.executeInteractive("HI/FILL 2d 0.25");
        assertEquals(1, view.requests); assertEquals(1, ((H2F) workspace.get("2d")).getEntries());
    }
    @Test public void formValidatesRequiredFieldsResetsAndCancels() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CommandArguments.Spec spec = CommandArguments.spec("HISTOGRAM/CREATE/1D", new Workspace(), List.of("HI/BOOK", "17"));
            CommandArgumentPrompt prompt = new CommandArgumentPrompt(spec, List.of("17"));
            JTextField id = (JTextField) named(prompt, "commandArgument0");
            JTextField title = (JTextField) named(prompt, "commandArgument1");
            assertEquals("17", id.getText()); id.setText(""); click(prompt, "OK"); assertFalse(prompt.answer().isDone());
            click(prompt, "Reset"); assertEquals("17", id.getText()); title.setText("a 'quoted' title"); click(prompt, "OK");
            assertEquals(List.of("17", "a 'quoted' title", "100", "0", "1"), prompt.answer().join());
            CommandArgumentPrompt cancelled = new CommandArgumentPrompt(spec, List.of("17")); click(cancelled, "Cancel"); assertTrue(cancelled.answer().isCancelled());
        });
    }
    @Test public void bareFillResolvesDimensionsBeforeAskingForCoordinates() throws Exception {
        Workspace workspace = new Workspace();
        View view = new View() {
            public List<String> requestArguments(CommandArguments.Spec spec, List<String> supplied) {
                requests++;
                if (requests == 1) { assertEquals(1, spec.required()); return List.of("two"); }
                assertEquals(3, spec.required()); assertEquals(List.of("two"), supplied); return List.of("two", "0.2", "0.8", "2");
            }
        };
        Commands commands = new Commands(workspace, view, text -> { });
        commands.execute("2D two 'Two dimensions' 10 0 1 10 0 1"); commands.executeInteractive("HI/FILL");
        assertEquals(2, view.requests); assertEquals(2, ((H2F) workspace.get("two")).getEntries());
    }
    @Test public void vectorValuesFromAFormRemainSeparateArguments() throws Exception {
        Workspace workspace = new Workspace();
        View view = new View() {
            public List<String> requestArguments(CommandArguments.Spec spec, List<String> supplied) {
                assertEquals("VECTOR/INPUT", spec.command()); assertEquals(List.of("sample"), supplied);
                return List.of("sample", "0.2 0.8 0.4");
            }
        };
        Commands commands = new Commands(workspace, view, text -> { });
        commands.execute("V/CREATE sample(3)"); commands.executeInteractive("V/INPUT sample");
        commands.execute("1D histogram 'Values' 3 0 3"); commands.execute("HI/PUT_VECTOR/CONTENT histogram sample");
        assertEquals(0.2, workspace.histogram("histogram").getBinContent(0), 1e-6);
        assertEquals(0.8, workspace.histogram("histogram").getBinContent(1), 1e-6);
    }
    private static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) { if (name.equals(child.getName())) return child; if (child instanceof Container c) { Component result = named(c, name); if (result != null) return result; } }
        return null;
    }
    private static void click(Container parent, String label) {
        for (Component child : parent.getComponents()) { if (child instanceof JButton button && label.equals(button.getText())) button.doClick(); else if (child instanceof Container c) click(c, label); }
    }
}
