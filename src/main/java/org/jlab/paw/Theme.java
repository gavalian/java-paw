package org.jlab.paw;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.ColorUIResource;

public final class Theme {
    public static final Color CREAM = new Color(224, 225, 187);
    public static final Color GOLD = new Color(241, 184, 73);
    public static final Color INDIGO = new Color(82, 82, 151);
    public static final Color RED = new Color(156, 0, 42);
    private Theme() { }
    public static void install(boolean modern) {
        try {
            if (modern) FlatLightLaf.setup();
            else {
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
                ColorUIResource cream = new ColorUIResource(CREAM), gold = new ColorUIResource(GOLD);
                for (String key : new String[]{"control", "Panel.background", "Button.background", "ToggleButton.background", "CheckBox.background", "RadioButton.background", "Label.background", "ScrollPane.background", "SplitPane.background", "Viewport.background", "TextArea.background", "TextField.background", "List.background", "Tree.background", "Table.background", "ComboBox.background", "InternalFrame.background", "Desktop.background"}) UIManager.put(key, cream);
                for (String key : new String[]{"MenuBar.background", "Menu.background", "MenuItem.background", "PopupMenu.background", "Menu.selectionBackground", "MenuItem.selectionBackground"}) UIManager.put(key, gold);
                FontUIResource font = new FontUIResource("Dialog", Font.BOLD, 12);
                for (String key : new String[]{"Label.font", "Button.font", "Menu.font", "MenuItem.font", "CheckBox.font", "RadioButton.font", "ComboBox.font", "InternalFrame.titleFont"}) UIManager.put(key, font);
                UIManager.put("TextArea.font", new FontUIResource("Monospaced", Font.PLAIN, 12));
                UIManager.put("TextField.font", new FontUIResource("Monospaced", Font.PLAIN, 12));
                UIManager.put("List.font", new FontUIResource("Monospaced", Font.BOLD, 12));
            }
        } catch (Exception ex) { throw new IllegalStateException("Could not install interface theme", ex); }
    }
}
