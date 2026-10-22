package org.jlab.paw;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import java.awt.*;
import twig.data.DataSet;

public class MacroInputTest {
    private static class View implements Commands.View {
        int plots;
        public void plot(DataSet data, boolean same) { plots++; }
        public void zones(int c, int r) { }
        public void zone(int n) { }
        public void export(Path file) { }
    }
    @Test public void readBlocksBetweenPagesAndPreservesDefaultAndQuotedInput() throws Exception {
        CountDownLatch waiting = new CountDownLatch(1), reply = new CountDownLatch(1);
        List<String> defaults = new ArrayList<>();
        View view = new View() {
            public String readInput(String prompt, String value) throws Exception {
                defaults.add(value); assertEquals("Review page", prompt);
                if (defaults.size() == 1) { assertEquals(1, plots); waiting.countDown(); reply.await(); return "arbitrary 'quoted' input [not_a_variable]"; }
                assertEquals(2, plots); return "";
            }
        };
        Path file = Files.createTempFile("paw read", ".kumac");
        Files.writeString(file, "1D one 'First' 10 0 10\nH/PLOT one\nREAD answer 'Review page'\n1D two '[answer]' 10 0 10\nH/PLOT two\nREAD answer 'Review page'\n1D three '[answer]' 10 0 10\n");
        Workspace workspace = new Workspace(); Commands commands = new Commands(workspace, view, text -> { });
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> run = worker.submit(() -> { try { commands.execute("MACRO/EXEC " + Commands.quote(file.toString())); } catch (Exception ex) { throw new RuntimeException(ex); } });
            assertTrue(waiting.await(5, TimeUnit.SECONDS)); assertEquals(1, view.plots); assertFalse(run.isDone());
            reply.countDown(); run.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("", "arbitrary 'quoted' input [not_a_variable]"), defaults);
            assertEquals(defaults.get(1), workspace.get("two").attr().getTitle());
            assertEquals(defaults.get(1), workspace.get("three").attr().getTitle());
        } finally { reply.countDown(); worker.shutdownNow(); Files.delete(file); }
    }
    @Test public void cancellationStopsTheSequenceAndUnwindsVariables() throws Exception {
        View view = new View() { public String readInput(String prompt, String value) { throw new CancellationException("Cancelled"); } };
        Path file = Files.createTempFile("paw cancel", ".kumac");
        Files.writeString(file, "1D one 'First' 10 0 10\nH/PLOT one\nREAD continue 'Next?'\n1D two 'Second' 10 0 10\nH/PLOT two\n");
        Workspace workspace = new Workspace(); Commands commands = new Commands(workspace, view, text -> { });
        try {
            try { commands.execute("MACRO/EXEC " + Commands.quote(file.toString())); fail("Expected cancellation"); }
            catch (CancellationException expected) { assertEquals("Cancelled", expected.getMessage()); }
            assertEquals(1, view.plots); assertEquals(1, workspace.objects().size());
            commands.execute("H/PLOT one"); assertEquals(2, view.plots);
        } finally { Files.delete(file); }
    }
    @Test public void runAllChoosesAutomaticManualOrImmediateProgression() throws Exception {
        Path file = Files.createTempFile("paw screen delay", ".kumac");
        Files.writeString(file, "MACRO/DELAY screen_delay\n1D one 'First' 10 0 10\nH/PLOT one\nMACRO/PAGE [screen_delay] 'Page 1'\n1D two 'Second' 10 0 10\nH/PLOT two\nMACRO/PAGE [screen_delay] 'Page 2'\n");
        try {
            for (String choice : List.of("0.25", "-2", "0")) {
                List<Double> waits = new ArrayList<>(); List<String> prompts = new ArrayList<>();
                View view = new View() {
                    public String readInput(String prompt, String value) {
                        prompts.add(prompt);
                        if (prompts.size() == 1) { assertEquals(0, plots); assertEquals("-1", value); return choice; }
                        assertEquals("Page " + plots, prompt); return "continue";
                    }
                    public void waitBetweenPages(double seconds) { waits.add(seconds); assertEquals(waits.size(), plots); }
                };
                new Commands(new Workspace(), view, text -> { }).execute("MACRO/EXEC " + Commands.quote(file.toString()));
                assertEquals(2, view.plots);
                assertEquals(choice.equals("-2") ? 3 : 1, prompts.size());
                assertEquals(choice.equals("0.25") ? List.of(0.25, 0.25) : List.of(), waits);
            }
        } finally { Files.delete(file); }
    }
    @Test public void invalidDelaysAreRequestedAgainAndAutomaticWaitCanBeCancelled() throws Exception {
        List<String> replies = List.of("bad", "NaN", "Infinity", "0.1"); int[] prompts = {0};
        View view = new View() {
            public String readInput(String prompt, String value) { return replies.get(prompts[0]++); }
            public void waitBetweenPages(double seconds) { assertEquals(0.1, seconds, 0); throw new CancellationException("Delay cancelled"); }
        };
        Commands commands = new Commands(new Workspace(), view, text -> { });
        commands.execute("MACRO/DELAY delay"); assertEquals(4, prompts[0]);
        try { commands.execute("MACRO/PAGE [delay] 'Next screen'"); fail("Expected cancellation"); }
        catch (CancellationException expected) { assertEquals("Delay cancelled", expected.getMessage()); }
    }
    @Test public void inputControlsAcceptEnterKeepDefaultsAndCancel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MacroInputPrompt prompt = new MacroInputPrompt("Next page?", "previous");
            JTextField input = (JTextField) find(prompt, JTextField.class, null); input.setText("anything");
            ((JButton) find(prompt, JButton.class, "Continue")).doClick(); assertEquals("anything", prompt.answer().join());
            MacroInputPrompt defaults = new MacroInputPrompt("Next page?", "previous");
            JTextField blank = (JTextField) find(defaults, JTextField.class, null); blank.setText(""); blank.postActionEvent(); assertEquals("previous", defaults.answer().join());
            MacroInputPrompt cancel = new MacroInputPrompt("Next page?", "");
            ((JButton) find(cancel, JButton.class, "Cancel")).doClick(); assertTrue(cancel.answer().isCancelled());
        });
    }
    private static Component find(Container parent, Class<?> type, String text) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child) && (text == null || child instanceof JButton button && text.equals(button.getText()))) return child;
            if (child instanceof Container container) { Component found = find(container, type, text); if (found != null) return found; }
        }
        return null;
    }
}
