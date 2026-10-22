package org.jlab.paw;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** A command parameter form; the analysis worker waits while Swing stays responsive. */
final class CommandArgumentPrompt extends JPanel {
    private final CompletableFuture<List<String>> answer = new CompletableFuture<>();
    private final List<JTextField> fields = new ArrayList<>();
    private final JButton execute = new JButton("OK"), cancel = new JButton("Cancel"), reset = new JButton("Reset"), help = new JButton("Help");
    private final JLabel message = new JLabel(" ");
    private JDialog dialog;

    CommandArgumentPrompt(CommandArguments.Spec spec, List<String> supplied) {
        this(spec, supplied, null);
    }
    CommandArgumentPrompt(CommandArguments.Spec spec, List<String> supplied, java.util.function.Consumer<String> showHelp) {
        super(new BorderLayout(10, 10)); setBorder(new EmptyBorder(12, 12, 12, 12));
        JLabel heading = new JLabel(spec.command()); heading.setFont(heading.getFont().deriveFont(Font.BOLD)); add(heading, BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout()); GridBagConstraints cell = new GridBagConstraints();
        cell.insets = new Insets(4, 4, 4, 4); cell.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < spec.labels().size(); i++) {
            cell.gridy = i; cell.gridx = 0; cell.weightx = 0; cell.fill = GridBagConstraints.NONE;
            form.add(new JLabel(spec.labels().get(i) + (i < spec.required() ? " *" : "")), cell);
            JTextField field = new JTextField(i < supplied.size() ? supplied.get(i) : spec.defaults().get(i), 28);
            field.setName("commandArgument" + i); fields.add(field);
            cell.gridx = 1; cell.weightx = 1; cell.fill = GridBagConstraints.HORIZONTAL; form.add(field, cell);
            field.addActionListener(event -> execute.doClick());
        }
        add(form, BorderLayout.CENTER);
        Runnable submit = () -> {
            List<String> values = new ArrayList<>();
            for (int i = 0; i < fields.size(); i++) {
                String value = fields.get(i).getText();
                if (i < spec.required() && value.isBlank() && !spec.labels().get(i).equals("Title") && !(spec.command().startsWith("NTUPLE/") && spec.defaults().get(i).equals(" "))) {
                    message.setText("Enter " + spec.labels().get(i).toLowerCase(java.util.Locale.ROOT) + "."); fields.get(i).requestFocusInWindow(); return;
                }
                values.add(value);
            }
            while (values.size() > spec.required() && values.get(values.size() - 1).isBlank()) values.remove(values.size() - 1);
            for (int i = spec.required(); i < values.size(); i++) if (values.get(i).isBlank()) {
                if(spec.command().startsWith("NTUPLE/") || NtupleCommands.spec(spec.command())!=null){values.set(i,"!");continue;}
                message.setText("Fill preceding optional fields or leave all following fields empty."); fields.get(i).requestFocusInWindow(); return;
            }
            answer.complete(List.copyOf(values));
        };
        execute.addActionListener(event -> submit.run()); cancel.addActionListener(event -> answer.cancel(false));
        reset.addActionListener(event -> { for (int i = 0; i < fields.size(); i++) fields.get(i).setText(i < supplied.size() ? supplied.get(i) : spec.defaults().get(i)); message.setText(" "); });
        help.addActionListener(event -> { if (showHelp != null) showHelp.accept(spec.command()); else message.setText("* Required. Enter titles and file paths directly, without quotes."); });
        JPanel footer = new JPanel(new BorderLayout()); footer.add(message, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(help); buttons.add(reset); buttons.add(cancel); buttons.add(execute); footer.add(buttons, BorderLayout.SOUTH); add(footer, BorderLayout.SOUTH);
    }
    void show(Window owner) {
        dialog = new JDialog(owner, "Command arguments", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent event) { answer.cancel(false); } });
        dialog.setContentPane(this); dialog.getRootPane().setDefaultButton(execute);
        dialog.getRootPane().registerKeyboardAction(event -> answer.cancel(false), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack(); dialog.setLocationRelativeTo(owner); dialog.setVisible(true);
        fields.stream().filter(field -> field.getText().isBlank()).findFirst().orElse(fields.get(0)).requestFocusInWindow();
    }
    CompletableFuture<List<String>> answer() { return answer; }
    void close() { if (dialog != null) dialog.dispose(); }
}
