package org.jlab.paw;

import twig.graphics.TGDataCanvas;
import java.awt.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.jfree.pdf.PDFDocument;

/** Checked output errors and atomic replacement, using Twig's own rendering. Call on the EDT. */
public final class GraphicsExport {
    private GraphicsExport() { }
    /** Multipage print output uses captured pictures, while ordinary PDF export remains vector. */
    public static void pictures(java.util.List<java.awt.image.BufferedImage> pictures, Path file, boolean landscape) throws IOException {
        if(pictures.isEmpty())throw new IllegalArgumentException("No pictures to print");
        Path destination=file.toAbsolutePath(),temp=Files.createTempFile(destination.getParent(),".paw-print-",".pdf");
        try { PDFDocument document=new PDFDocument();
            int width=landscape?842:595,height=landscape?595:842;
            for(var image:pictures) { var page=document.createPage(new Rectangle(width,height));Graphics2D g=page.getGraphics2D();double scale=Math.min((width-40.0)/image.getWidth(),(height-40.0)/image.getHeight());int w=(int)(image.getWidth()*scale),h=(int)(image.getHeight()*scale);g.drawImage(image,(width-w)/2,(height-h)/2,w,h,null);g.dispose(); }
            document.writeToFile(temp.toFile());
            Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temp);}
    }
    public static void write(TGDataCanvas canvas, Path file) throws IOException {
        if (canvas.getWidth() < 1 || canvas.getHeight() < 1) throw new IOException("Graphics window is not visible");
        Path destination = file.toAbsolutePath(); String name = file.toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".png") && !name.endsWith(".pdf") && !name.endsWith(".ps") && !name.endsWith(".eps")) throw new IOException("Use .png, .pdf, .ps or .eps");
        Path temp = Files.createTempFile(destination.getParent(), ".paw-plot-", name.endsWith(".png") ? ".png" : ".pdf");
        try {
            if (name.endsWith(".png")) {
                if (!ImageIO.write(canvas.getScreenShot(), "png", temp.toFile())) throw new IOException("PNG encoder unavailable");
            } else if(name.endsWith(".ps") || name.endsWith(".eps")) {
                var image=canvas.getScreenShot(); int w=image.getWidth(),h=image.getHeight();
                try(var writer=Files.newBufferedWriter(temp,java.nio.charset.StandardCharsets.US_ASCII)) {
                    writer.write(name.endsWith(".eps")?"%!PS-Adobe-3.0 EPSF-3.0\n":"%!PS-Adobe-3.0\n");
                    writer.write("%%BoundingBox: 0 0 "+w+" "+h+"\n%%Pages: 1\n%%EndComments\n/picstr "+(w*3)+" string def\ngsave\n"+w+" "+h+" scale\n"+w+" "+h+" 8 ["+w+" 0 0 -"+h+" 0 "+h+"]\n{currentfile picstr readhexstring pop} false 3 colorimage\n");
                    char[] hex="0123456789abcdef".toCharArray(); StringBuilder row=new StringBuilder(w*6+1);
                    for(int y=0;y<h;y++){row.setLength(0);for(int x=0;x<w;x++){int rgb=image.getRGB(x,y);for(int shift:new int[]{16,8,0}){int b=(rgb>>shift)&255;row.append(hex[b>>4]).append(hex[b&15]);}}writer.write(row.toString());writer.newLine();}
                    writer.write("grestore\n"+(name.endsWith(".eps")?"":"showpage\n")+"%%EOF\n");
                }
            } else {
                PDFDocument document = new PDFDocument(); var page = document.createPage(new Rectangle(canvas.getSize()));
                Graphics2D graphics = page.getGraphics2D(); canvas.paint(graphics); graphics.dispose(); document.writeToFile(temp.toFile());
            }
            if (Files.size(temp) == 0) throw new IOException("Export produced an empty file");
            try { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
