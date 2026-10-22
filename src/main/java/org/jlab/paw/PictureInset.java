package org.jlab.paw;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import j4np.graphics.Translation2D;
import twig.widgets.Widget;

/** Immutable captured picture merged into a Twig region at normalized coordinates. */
public final class PictureInset implements Widget {
    private final BufferedImage image;
    private final double x,y,width,height;
    private int level=2;
    public PictureInset(BufferedImage image,double x,double y,double width,double height) {this.image=image;this.x=x;this.y=y;this.width=width;this.height=height;}
    public void draw(Graphics2D g,Rectangle2D r,Translation2D tr) {int px=(int)(r.getX()+x*r.getWidth()),py=(int)(r.getMaxY()-(y+height)*r.getHeight()),w=(int)(width*r.getWidth()),h=(int)(height*r.getHeight());g.drawImage(image,px,py,w,h,null);g.setColor(Color.BLACK);g.drawRect(px,py,w,h);}
    public boolean isNDF(){return true;}public void configure(JComponent parent){}public void setLevel(int level){this.level=level;}public int getLevel(){return level;}
}
