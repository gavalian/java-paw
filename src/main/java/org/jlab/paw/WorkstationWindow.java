package org.jlab.paw;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.BevelBorder;

/** A top-level Swing window. Headless previews render its same content without creating a peer. */
final class WorkstationWindow extends JPanel {
    private final String title;
    private final JComponent content;
    private final JPanel heading = new JPanel(new BorderLayout());
    private final JLabel caption;
    private final JFrame frame;
    private final boolean disposable;
    private final Runnable closed;
    private Runnable closing;
    private boolean disposed;

    WorkstationWindow(String title, JComponent content, boolean disposable, boolean previewOnly, Runnable closed) {
        super(new BorderLayout());
        this.title = title; this.content = content; this.disposable = disposable; this.closed = closed;
        caption = new JLabel(title, SwingConstants.CENTER);
        heading.add(caption, BorderLayout.NORTH);
        add(heading, BorderLayout.NORTH); add(content);
        if (previewOnly) frame = null;
        else {
            frame = new JFrame(title);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setContentPane(this);
            frame.setMinimumSize(new Dimension(360, 220));
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) { close(); }
            });
        }
    }
    void onClosing(Runnable action) { closing = action; }
    void close() {
        if (closing != null) closing.run();
        else if (disposable) dispose();
        else { if (frame != null) frame.setVisible(false); else setVisible(false); }
    }
    void dispose() {
        if (disposed) return;
        disposed = true;
        if (frame != null) frame.dispose();
        else { Container parent = getParent(); if (parent != null) parent.remove(this); }
        closed.run();
    }
    void toFront() {
        if (disposed) return;
        setVisible(true);
        if (frame != null) { frame.setVisible(true); frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED); frame.toFront(); }
    }
    void setWindowBounds(Rectangle bounds) {
        if (frame != null) frame.setBounds(bounds);
        else setBounds(bounds);
    }
    void setWindowBounds(int x, int y, int width, int height) { setWindowBounds(new Rectangle(x, y, width, height)); }
    Rectangle windowBounds() { return frame == null ? getBounds() : frame.getBounds(); }
    JFrame frame() { return frame; }
    String title() { return title; }
    Container getContentPane() { return content; }
    void setJMenuBar(JMenuBar menu) {
        // Menus belong to each independent window, including on macOS.
        if (heading.getComponentCount() > 1) heading.remove(1);
        heading.add(menu, BorderLayout.CENTER); revalidate();
    }
    void applyTheme(boolean modern) {
        SwingUtilities.updateComponentTreeUI(frame == null ? this : frame);
        // Native OS title bars remain usable; the classic banner retains PAW's window colors.
        caption.setVisible(!modern || frame == null);
        caption.setOpaque(true);
        caption.setBackground(modern ? new Color(229, 234, 240) : title.contains("Main Browser") ? Theme.RED : Theme.INDIGO);
        caption.setForeground(modern ? Color.DARK_GRAY : Color.WHITE);
        caption.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 13));
        caption.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        caption.setPreferredSize(new Dimension(100, 24));
        if (!modern) classicPanels(this);
        revalidate(); repaint();
    }
    private static void classicPanels(Container container) {
        if (container instanceof JPanel) container.setBackground(new javax.swing.plaf.ColorUIResource(Theme.CREAM));
        for (Component child : container.getComponents()) if (child instanceof Container nested) classicPanels(nested);
    }
}
