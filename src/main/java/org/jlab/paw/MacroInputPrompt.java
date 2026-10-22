package org.jlab.paw;

import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** A nonmodal input window: only the macro worker waits, leaving plots usable. */
final class MacroInputPrompt extends JPanel {
    private final CompletableFuture<String> answer = new CompletableFuture<>();
    private final JTextField input;
    private final JButton proceed = new JButton("Continue"), cancel = new JButton("Cancel");
    private JDialog dialog;

    MacroInputPrompt(String prompt, String defaultValue) {
        super(new BorderLayout(8, 8)); setBorder(new EmptyBorder(12, 12, 12, 12));
        JTextArea message = new JTextArea(prompt); message.setEditable(false); message.setLineWrap(true);
        message.setWrapStyleWord(true); message.setOpaque(false); message.setFont(UIManager.getFont("Label.font"));
        message.setRows(3); message.setColumns(38); add(message, BorderLayout.NORTH);
        input = new JTextField(defaultValue, 32); input.setName("macroInput"); add(input, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(cancel); buttons.add(proceed); add(buttons, BorderLayout.SOUTH);
        Runnable submit = () -> answer.complete(input.getText().isEmpty() ? defaultValue : input.getText());
        proceed.addActionListener(event -> submit.run()); input.addActionListener(event -> submit.run());
        cancel.addActionListener(event -> answer.cancel(false));
    }
    void show(Window owner) {
        dialog = new JDialog(owner, "Macro input", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent event) { answer.cancel(false); } });
        dialog.setContentPane(this); dialog.getRootPane().setDefaultButton(proceed);
        dialog.getRootPane().registerKeyboardAction(event -> answer.cancel(false), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack(); dialog.setLocationRelativeTo(owner); dialog.setVisible(true); input.requestFocusInWindow(); input.selectAll();
    }
    CompletableFuture<String> answer() { return answer; }
    void close() { if (dialog != null) dialog.dispose(); }
}
