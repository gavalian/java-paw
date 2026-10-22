package org.jlab.paw;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;
import j4np.graphics.Translation2D;
import twig.widgets.Widget;

/** Positioned and rotated tutorial text, in plot coordinates or normalized region coordinates. */
public final class PlotText implements Widget {
    private final String text;private final double x,y,angle;private final int size;private final boolean normalized;private int level=2;
    public PlotText(String text,double x,double y,int size,double angle,boolean normalized){this.text=text;this.x=x;this.y=y;this.size=size;this.angle=angle;this.normalized=normalized;}
    public void draw(Graphics2D source,Rectangle2D r,Translation2D tr){Graphics2D g=(Graphics2D)source.create();try{double px=r.getX()+(normalized?x*r.getWidth():tr.relativeX(x,r)),py=r.getMaxY()-(normalized?y*r.getHeight():tr.relativeY(y,r));g.translate(px,py);g.rotate(-Math.toRadians(angle));g.setFont(new Font("Serif",Font.PLAIN,size));g.setColor(Color.BLACK);g.drawString(text,0,0);}finally{g.dispose();}}
    public boolean isNDF(){return normalized;}public void configure(JComponent parent){}public void setLevel(int level){this.level=level;}public int getLevel(){return level;}
}
