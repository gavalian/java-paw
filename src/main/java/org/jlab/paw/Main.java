package org.jlab.paw;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class Main {
    private Main() { }
    public static void main(String[] args) throws Exception {
        boolean modern = java.util.prefs.Preferences.userNodeForPackage(Main.class).getBoolean("modern", false), demo = true;
        Path snapshot = null, macro = null;
        for (int i = 0; i < args.length; i++) switch (args[i]) {
            case "--modern" -> modern = true;
            case "--motif" -> modern = false;
            case "--empty" -> demo = false;
            case "--snapshot" -> { if (++i == args.length) throw new IllegalArgumentException("--snapshot requires a PNG path"); snapshot = Path.of(args[i]); }
            case "--macro" -> { if (++i == args.length) throw new IllegalArgumentException("--macro requires a macro path"); macro = Path.of(args[i]); }
            case "--help" -> { System.out.println("PAW++ Java: --motif | --modern | --empty | --macro file.kumac | --snapshot path.png"); return; }
            default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
        }
        final boolean mode = modern, samples = demo; final Path output = snapshot, startupMacro = macro;
        SwingUtilities.invokeAndWait(() -> {
            Theme.install(mode); Workstation workstation = new Workstation(mode, samples, output != null);
            if (output != null) {
                workstation.setSize(1440, 1000); layout(workstation); workstation.tile(); layout(workstation);
                if (startupMacro != null) try { workstation.previewCommand("MACRO/EXEC " + Commands.quote(startupMacro.toString())); } catch (Exception ex) { throw new IllegalStateException(ex); }
                BufferedImage image = new BufferedImage(1440, 1000, BufferedImage.TYPE_INT_RGB); Graphics2D g = image.createGraphics(); workstation.printAll(g); g.dispose();
                try { ImageIO.write(image, "png", output.toFile()); } catch (Exception ex) { throw new IllegalStateException(ex); }
                workstation.shutdown();
            } else {
                workstation.showWindows();
                if (startupMacro != null) workstation.execute("MACRO/EXEC " + Commands.quote(startupMacro.toString()));
            }
        });
    }
    private static void layout(Container container) { container.doLayout(); for (Component component : container.getComponents()) if (component instanceof Container child) layout(child); }
}
