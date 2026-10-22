package org.jlab.paw;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.*;

/** Browse command groups and individual command descriptions without executing them. */
final class CommandHelpPanel extends JPanel {
    private final JTree tree = new JTree();
    private final JTextArea details = new JTextArea();
    private final JTextField search = new JTextField();
    private final JButton use = new JButton("Use command"), arguments = new JButton("Arguments...");
    private final Map<String, DefaultMutableTreeNode> nodes = new LinkedHashMap<>();
    private final Workspace workspace;
    private CommandCatalog.Entry selected;

    CommandHelpPanel(String path, Workspace workspace, Consumer<String> useCommand, Consumer<String> parameters, Runnable close) {
        super(new BorderLayout(8, 8)); this.workspace = workspace; setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new BorderLayout(8, 0)); top.add(new JLabel("Find command"), BorderLayout.WEST); search.setName("commandHelpSearch"); top.add(search); add(top, BorderLayout.NORTH);
        tree.setName("commandHelpTree"); details.setName("commandHelpDetails"); details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true); details.setMargin(new Insets(10, 10, 10, 10));
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), new JScrollPane(details)); split.setDividerLocation(250); split.setResizeWeight(0.28); add(split);
        tree.addTreeSelectionListener(event -> describe());
        use.addActionListener(event -> { if (selected != null) useCommand.accept(selected.command()); });
        arguments.addActionListener(event -> { if (selected != null) parameters.accept(selected.command()); });
        JButton exit = new JButton("Close"); exit.addActionListener(event -> close.run());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(use); buttons.add(arguments); buttons.add(exit); add(buttons, BorderLayout.SOUTH);
        rebuild(""); select(path);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); } public void removeUpdate(DocumentEvent e) { filter(); } public void changedUpdate(DocumentEvent e) { filter(); }
            private void filter() { rebuild(search.getText()); }
        });
        setPreferredSize(new Dimension(900, 550));
    }
    private void rebuild(String query) {
        String needle = query.strip().toUpperCase(java.util.Locale.ROOT); nodes.clear();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("All commands"); nodes.put("", root);
        for (String group : CommandCatalog.groups()) {
            DefaultMutableTreeNode branch = new DefaultMutableTreeNode(group); nodes.put(group, branch);
            for (CommandCatalog.Entry entry : CommandCatalog.entries()) {
                if (!entry.group().equals(group) || !(entry.usage() + " " + entry.description()).toUpperCase(java.util.Locale.ROOT).contains(needle)) continue;
                DefaultMutableTreeNode parent = branch;
                String[] parts = entry.command().split("/"); String prefix = parts[0];
                for (int i = 1; i < parts.length - 1; i++) {
                    prefix += "/" + parts[i]; DefaultMutableTreeNode nested = nodes.get(prefix);
                    if (nested == null) { nested = new DefaultMutableTreeNode(parts[i]); parent.add(nested); nodes.put(prefix, nested); }
                    parent = nested;
                }
                DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(entry); parent.add(leaf); nodes.put(entry.command(), leaf);
            }
            if (branch.getChildCount() > 0 || needle.isEmpty()) root.add(branch);
        }
        tree.setModel(new DefaultTreeModel(root)); tree.setSelectionPath(new TreePath(root.getPath()));
        if (!needle.isEmpty()) for (int row = 0; row < tree.getRowCount(); row++) tree.expandRow(row);
    }
    void select(String path) {
        DefaultMutableTreeNode node = nodes.get(CommandCatalog.normalize(path));
        if (node != null) { TreePath selection = new TreePath(node.getPath()); tree.setSelectionPath(selection); tree.expandPath(selection); SwingUtilities.invokeLater(() -> tree.scrollPathToVisible(selection)); }
    }
    private void describe() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        selected = node != null && node.getUserObject() instanceof CommandCatalog.Entry entry ? entry : null;
        use.setEnabled(selected != null); arguments.setEnabled(false);
        if (selected == null) {
            String path = nodes.entrySet().stream().filter(entry -> entry.getValue() == node).map(Map.Entry::getKey).findFirst().orElse("");
            details.setText(CommandCatalog.text(path, false));
        } else {
            StringBuilder text = new StringBuilder(selected.usage()).append("\n\n").append(selected.description());
            CommandArguments.Spec spec = CommandArguments.spec(selected.command(), workspace, List.of(selected.command(), "id"));
            if (spec != null) {
                arguments.setEnabled(true); text.append("\n\nParameters\n");
                for (int i = 0; i < spec.labels().size(); i++) {
                    text.append("\n").append(spec.labels().get(i)).append(i < spec.required() ? " (required argument)" : " (optional)");
                    if (!spec.defaults().get(i).isEmpty()) text.append("\n  Panel default: ").append(spec.defaults().get(i));
                }
            }
            text.append("\n\nUse command places it in the Input Pad for editing.");
            if (spec != null) text.append(" Arguments opens its parameter form.");
            details.setText(text.toString());
        }
        details.setCaretPosition(0);
    }
}
