package org.jlab.paw;

import java.awt.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import javax.swing.*;
import org.junit.Test;
import twig.data.DataSet;
import static org.junit.Assert.*;

public class CommandHelpTest {
    private static class View implements Commands.View {
        public void plot(DataSet ds, boolean same) { }
        public void zones(int columns, int rows) { }
        public void zone(int index) { }
        public void export(Path file) { }
    }
    @Test public void groupHelpAndAliasesResolveToTheSameBrowser() throws Exception {
        List<String> paths = new ArrayList<>();
        Commands commands = new Commands(new Workspace(), new View() {
            public boolean showCommandHelp(String path) { paths.add(path); return true; }
        }, text -> { });
        for (String command : List.of("HISTOGRAM/HELP", "HI/HELP", "H/HELP", "HISTO/HELP", "HELP HISTOGRAM")) commands.executeInteractive(command);
        assertEquals(List.of("HISTOGRAM", "HISTOGRAM", "HISTOGRAM", "HISTOGRAM", "HISTOGRAM"), paths);
        for (String group : CommandCatalog.groups()) commands.execute(group + "/HELP");
        commands.execute("V/HELP"); commands.execute("HELP V"); commands.execute("VECTOR"); commands.execute("HELP HI/BOOK"); commands.execute("CHELP /GRAPHICS"); commands.execute("HELP");
        assertEquals(List.of("VECTOR", "VECTOR", "VECTOR", "HISTOGRAM/CREATE/1D", "GRAPHICS", ""), paths.subList(paths.size() - 6, paths.size()));
    }
    @Test public void fallbackHelpIsScopedAndUnknownPathsAreRejected() throws Exception {
        List<String> output = new ArrayList<>(); Commands commands = new Commands(new Workspace(), new View(), output::add);
        commands.execute("VECTOR/HELP"); String help = output.get(output.size() - 1);
        assertTrue(help.contains("VECTOR/CREATE")); assertTrue(help.contains("VBIAS")); assertFalse(help.contains("HISTOGRAM/FIT"));
        commands.execute("USAGE HI/BOOK"); String usage = output.get(output.size() - 1);
        assertTrue(usage.contains("HISTOGRAM/CREATE/1D id 'title' bins min max")); assertFalse(usage.contains("Create an empty"));
        commands.execute("NTUPLE/HELP"); assertTrue(output.get(output.size() - 1).contains("NTUPLE/CREATE"));
        for (String bad : List.of("UNKNOWN/HELP", "HELP UNKNOWN", "VECTOR/HELP extra")) {
            try { commands.execute(bad); fail(bad); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void catalogCoversEveryExecutableCommand() throws Exception {
        Set<String> documented = CommandCatalog.entries().stream().map(CommandCatalog.Entry::command).collect(Collectors.toSet());
        assertEquals(CommandCatalog.entries().size(), documented.size());
        String commandSource = Files.readString(Path.of("src/main/java/org/jlab/paw/Commands.java"));
        String vectorSource = Files.readString(Path.of("src/main/java/org/jlab/paw/Vectors.java"));
        commandSource = commandSource.substring(commandSource.indexOf("switch (op) {", commandSource.indexOf("private void execute(")), commandSource.indexOf("private static int pawColor"));
        vectorSource = vectorSource.substring(vectorSource.indexOf("switch (op) {"), vectorSource.indexOf("private void sigma"));
        for (String source : List.of(commandSource, vectorSource)) {
            var cases = Pattern.compile("case (.*?) ->", Pattern.DOTALL).matcher(source);
            while (cases.find()) {
                var names = Pattern.compile("\"([^\"]+)\"").matcher(cases.group(1));
                while (names.find()) {
                    String command = names.group(1);
                    if (Set.of("HCOL", "PCOL", "HWID", "PWID").contains(command)) continue;
                    assertTrue("Missing help for " + command, documented.contains(command));
                }
            }
        }
    }
    @Test public void browserSelectsSearchesAndUsesCommandsWithoutExecutingThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> used = new ArrayList<>(), parameters = new ArrayList<>();
            CommandHelpPanel panel = new CommandHelpPanel("VECTOR", new Workspace(), used::add, parameters::add, () -> { });
            JTextArea details = (JTextArea) named(panel, "commandHelpDetails");
            assertTrue(details.getText().contains("VECTOR/CREATE"));
            panel.select("HI/BOOK"); assertTrue(details.getText().contains("Panel default: 100"));
            click(panel, "Use command"); assertEquals(List.of("HISTOGRAM/CREATE/1D"), used); assertTrue(parameters.isEmpty());
            click(panel, "Arguments..."); assertEquals(used, parameters);
            ((JTextField) named(panel, "commandHelpSearch")).setText("SMOOTH");
            JTree tree = (JTree) named(panel, "commandHelpTree");
            assertTrue(tree.getRowCount() >= 3); panel.select("HISTOGRAM/SMOOTH"); assertTrue(details.getText().contains("three-bin"));
            ((JTextField) named(panel, "commandHelpSearch")).setText("no_such_command"); assertEquals(1, tree.getRowCount());
        });
    }
    @Test public void workstationCommandsOpenGroupAndNestedHelpWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Workstation app = new Workstation(true, false, true);
            try {
                app.previewCommand("VECTOR/HELP"); assertEquals(5, app.windows().size());
                JTextArea details = (JTextArea) named(app.windows().get(4), "commandHelpDetails");
                assertTrue(details.getText().contains("VECTOR/INPUT"));
                app.windows().get(4).close(); assertEquals(4, app.windows().size());
                app.previewCommand("HELP HI/BOOK"); assertEquals(5, app.windows().size());
                details = (JTextArea) named(app.windows().get(4), "commandHelpDetails");
                assertTrue(details.getText().contains("Panel default: 100"));
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { app.shutdown(); }
        });
    }
    private static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) { if (name.equals(child.getName())) return child; if (child instanceof Container c) { Component result = named(c, name); if (result != null) return result; } }
        return null;
    }
    private static void click(Container parent, String label) {
        for (Component child : parent.getComponents()) { if (child instanceof JButton button && label.equals(button.getText())) button.doClick(); else if (child instanceof Container c) click(c, label); }
    }
}
