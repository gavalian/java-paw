package org.jlab.paw;

import twig.data.*;
import twig.widgets.Widget;
import java.awt.*;
import java.awt.geom.*;
import javax.swing.JComponent;
import j4np.graphics.Translation2D;

/** Additional tutorial representations, rendered as Twig widgets from Twig datasets. */
public final class TutorialGraphics implements Widget {
    private final H2F histogram;
    private final Graph3D points;
    private final String mode;
    private final double[] levels;
    private final double[][] errors;
    private final String coordinates;
    private final boolean logX,logY,logZ;
    private int level=1;
    public TutorialGraphics(H2F h,String mode) {this(h,mode,null,null,"CARTESIAN",false,false,false);}
    public TutorialGraphics(H2F h,String mode,double[] levels,double[][] errors,String coordinates,boolean logX,boolean logY,boolean logZ) {
        this.histogram=h;this.points=null;this.mode=mode.toUpperCase();this.levels=levels==null?null:levels.clone();this.errors=errors;this.coordinates=coordinates;this.logX=logX;this.logY=logY;this.logZ=logZ;
        if(!java.util.Set.of("BOX","SCATTER","CONTOUR","SURFACE","LEGO","ERROR","ERROR_COLOR","TABLE","ARROW").contains(this.mode))throw new IllegalArgumentException("Unknown representation: "+mode);
        if(!java.util.Set.of("CARTESIAN","POL","CYL","SPH","PSD").contains(coordinates))throw new IllegalArgumentException("Coordinate system");
    }
    public TutorialGraphics(Graph3D graph) {points=graph;histogram=null;mode="POINTS";levels=null;errors=null;coordinates="CARTESIAN";logX=logY=logZ=false;}
    private static Color color(double t) {return Color.getHSBColor((float)(.67*(1-Math.max(0,Math.min(1,t)))),.8f,.9f);}
    @Override public void draw(Graphics2D original,Rectangle2D r,Translation2D tr) {
        Graphics2D g=(Graphics2D)original.create();
        try {g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setFont(new Font("SansSerif",Font.PLAIN,11));g.setColor(Color.WHITE);g.fill(r);
            Rectangle2D plot=new Rectangle2D.Double(r.getX()+35,r.getY()+28,Math.max(10,r.getWidth()-55),Math.max(10,r.getHeight()-60));
            String title=histogram==null?points.attr().getTitle():histogram.attr().getTitle();
            g.setColor(Color.BLACK);g.drawString((title.isBlank()?mode:title)+ (coordinates.equals("CARTESIAN")?"":" / "+coordinates)+(logX?" logX":"")+(logY?" logY":"")+(logZ?" logZ":""),(int)r.getX()+5,(int)r.getY()+15);
            if(points!=null){cloud(g,plot);return;}
            if(java.util.Set.of("SURFACE","LEGO","ERROR").contains(mode)) {surface(g,plot);return;}
            flat(g,plot);
        } finally {g.dispose();}
    }
    private void flat(Graphics2D g,Rectangle2D r) {
        int nx=histogram.getAxisX().getNBins(),ny=histogram.getAxisY().getNBins();double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(int i=0;i<nx;i++)for(int j=0;j<ny;j++){double v=value(i,j);min=Math.min(min,v);max=Math.max(max,v);}double span=max-min;if(span<=0)span=1;
        double dx=r.getWidth()/nx,dy=r.getHeight()/ny;
        if(!mode.equals("CONTOUR"))for(int i=0;i<nx;i++)for(int j=0;j<ny;j++){
            double v=value(i,j),t=(v-min)/span,x=r.getX()+i*dx,y=r.getMaxY()-(j+1)*dy;g.setColor(color(t));
            switch(mode) {
                case "BOX" -> {double size=Math.sqrt(Math.max(0,t));g.fill(new Rectangle2D.Double(x+dx*(1-size)/2,y+dy*(1-size)/2,dx*size,dy*size));}
                case "SCATTER" -> {if(v!=0){double radius=Math.max(1,Math.sqrt(Math.max(0,t))*Math.min(dx,dy)/2);g.fill(new Ellipse2D.Double(x+dx/2-radius,y+dy/2-radius,2*radius,2*radius));}}
                case "TABLE" -> {g.setColor(Color.BLACK);if(nx<=15&&ny<=15)g.drawString(String.format(java.util.Locale.ROOT,"%.1f",v),(float)x,(float)(y+dy/2));}
                case "ARROW" -> {double gx=histogram.getBinContent(Math.min(nx-1,i+1),j)-histogram.getBinContent(Math.max(0,i-1),j),gy=histogram.getBinContent(i,Math.min(ny-1,j+1))-histogram.getBinContent(i,Math.max(0,j-1));double a=Math.atan2(gy,gx),length=Math.min(dx,dy)*.4;double cx=x+dx/2,cy=y+dy/2,ex=cx+Math.cos(a)*length,ey=cy-Math.sin(a)*length;g.draw(new Line2D.Double(cx,cy,ex,ey));for(double sign:new double[]{-.5,.5})g.draw(new Line2D.Double(ex,ey,ex-Math.cos(a+sign)*length*.35,ey+Math.sin(a+sign)*length*.35));}
                default -> g.fill(new Rectangle2D.Double(x,y,dx+.2,dy+.2));
            }
        }
        if(mode.equals("CONTOUR")) {
            double[] cuts=levels;if(cuts==null){cuts=new double[12];for(int i=0;i<cuts.length;i++)cuts[i]=min+(max-min)*(i+1)/(cuts.length+1);}
            for(double cut:cuts){g.setColor(color((cut-min)/span));for(int i=0;i<nx-1;i++)for(int j=0;j<ny-1;j++){
                double[] z={value(i,j),value(i+1,j),value(i+1,j+1),value(i,j+1)};
                double[][] xy={{i+.5,j+.5},{i+1.5,j+.5},{i+1.5,j+1.5},{i+.5,j+1.5}};java.util.List<Point2D> crossings=new java.util.ArrayList<>();
                for(int edge=0;edge<4;edge++){int next=(edge+1)%4;if((z[edge]<cut)!=(z[next]<cut)){double f=(cut-z[edge])/(z[next]-z[edge]);crossings.add(new Point2D.Double(r.getX()+(xy[edge][0]+f*(xy[next][0]-xy[edge][0]))*dx,r.getMaxY()-(xy[edge][1]+f*(xy[next][1]-xy[edge][1]))*dy));}}
                // For ambiguous saddle cells, choose pairs consistently around the cell boundary.
                for(int k=0;k+1<crossings.size();k+=2)g.draw(new Line2D.Double(crossings.get(k),crossings.get(k+1)));
            }}
        }
        g.setColor(Color.BLACK);g.draw(r);axisText(g,r,histogram.getAxisX().min(),histogram.getAxisX().max(),histogram.getAxisY().min(),histogram.getAxisY().max());
    }
    private double value(int i,int j) {double v=mode.equals("ERROR_COLOR")?error(i,j):histogram.getBinContent(i,j);return logZ?Math.log10(Math.max(1e-9,v)):v;}
    private double error(int i,int j) {return errors==null?Math.sqrt(Math.abs(histogram.getBinContent(i,j))):Math.abs(errors[i][j]);}
    private Point2D project(Rectangle2D r,double x,double y,double z) {return new Point2D.Double(r.getCenterX()+(x-y)*r.getWidth()*.32,r.getMaxY()-r.getHeight()*.2-(x+y)*r.getHeight()*.18-z*r.getHeight()*.5);}
    private double fraction(double v,double min,double max,boolean log) {if(log){if(min<=0||max<=0)throw new IllegalArgumentException("Log axes require positive bounds");return (Math.log(v)-Math.log(min))/(Math.log(max)-Math.log(min));}return (v-min)/(max-min);}
    private double[] transform(double x,double y,double z) {double theta=2*Math.PI*x;return switch(coordinates){case "POL","CYL"->new double[]{.5+.5*y*Math.cos(theta),.5+.5*y*Math.sin(theta),z};case "SPH"->new double[]{.5+.5*z*Math.sin(Math.PI*y)*Math.cos(theta),.5+.5*z*Math.sin(Math.PI*y)*Math.sin(theta),.5+.5*z*Math.cos(Math.PI*y)};case "PSD"->{double a=2*y-1,phi=2*Math.atan(Math.exp(-2*a));yield new double[]{.5+.5*z*Math.sin(phi)*Math.cos(theta),.5+.5*z*Math.sin(phi)*Math.sin(theta),.5+.5*z*Math.cos(phi)};}default->new double[]{x,y,z};};}
    private Point2D vertex(Rectangle2D r,double x,double y,double z) {double[] p=transform(x,y,z);return project(r,p[0],p[1],p[2]);}
    private void surface(Graphics2D g,Rectangle2D r) {
        int nx=histogram.getAxisX().getNBins(),ny=histogram.getAxisY().getNBins();double min=0,max=0;
        for(int i=0;i<nx;i++)for(int j=0;j<ny;j++){double z=value(i,j);min=Math.min(min,z-(mode.equals("ERROR")?error(i,j):0));max=Math.max(max,z+(mode.equals("ERROR")?error(i,j):0));}double span=Math.max(1e-9,max-min);
        for(int i=nx-1;i>=0;i--)for(int j=ny-1;j>=0;j--){double z=value(i,j),height=(z-min)/span;
            double x0=fraction(histogram.getAxisX().getBinLow(i),histogram.getAxisX().min(),histogram.getAxisX().max(),logX),x1=fraction(histogram.getAxisX().getLimits()[i+1],histogram.getAxisX().min(),histogram.getAxisX().max(),logX),y0=fraction(histogram.getAxisY().getBinLow(j),histogram.getAxisY().min(),histogram.getAxisY().max(),logY),y1=fraction(histogram.getAxisY().getLimits()[j+1],histogram.getAxisY().min(),histogram.getAxisY().max(),logY);
            if(mode.equals("ERROR")){Point2D a=vertex(r,(x0+x1)/2,(y0+y1)/2,(z-error(i,j)-min)/span),b=vertex(r,(x0+x1)/2,(y0+y1)/2,(z+error(i,j)-min)/span);g.setColor(color(height));g.draw(new Line2D.Double(a,b));g.fill(new Ellipse2D.Double((a.getX()+b.getX())/2-2,(a.getY()+b.getY())/2-2,4,4));continue;}
            if(mode.equals("LEGO")) {face(g,color(height).darker(),vertex(r,x0,y0,0),vertex(r,x1,y0,0),vertex(r,x1,y0,height),vertex(r,x0,y0,height));face(g,color(height).darker(),vertex(r,x0,y0,0),vertex(r,x0,y1,0),vertex(r,x0,y1,height),vertex(r,x0,y0,height));}
            double h10=mode.equals("SURFACE")?(value(Math.min(nx-1,i+1),j)-min)/span:height,h01=mode.equals("SURFACE")?(value(i,Math.min(ny-1,j+1))-min)/span:height,h11=mode.equals("SURFACE")?(value(Math.min(nx-1,i+1),Math.min(ny-1,j+1))-min)/span:height;
            face(g,color(height),vertex(r,x0,y0,height),vertex(r,x1,y0,h10),vertex(r,x1,y1,h11),vertex(r,x0,y1,h01));
        }
        g.setColor(Color.BLACK);g.drawString(String.format(java.util.Locale.ROOT,"X %.3g..%.3g   Y %.3g..%.3g   %s %.3g..%.3g",histogram.getAxisX().min(),histogram.getAxisX().max(),histogram.getAxisY().min(),histogram.getAxisY().max(),logZ?"log10 Z":"Z",min,max),(float)r.getX(),(float)r.getMaxY()+17);
    }
    private void face(Graphics2D g,Color color,Point2D... vertices) {Path2D path=new Path2D.Double();path.moveTo(vertices[0].getX(),vertices[0].getY());for(int i=1;i<vertices.length;i++)path.lineTo(vertices[i].getX(),vertices[i].getY());path.closePath();g.setColor(color);g.fill(path);g.setColor(new Color(0,0,0,90));g.draw(path);}
    private void cloud(Graphics2D g,Rectangle2D r) {int n=points.getVextorX().size();for(int i=0;i<n;i++){double x=points.getVextorX().getValue(i),y=points.getVextorY().getValue(i),z=points.getVextorZ().getValue(i);Point2D p=project(r,(x+1)/2,(y+1)/2,(z+1)/2);g.setColor(color((z+1)/2));g.fill(new Ellipse2D.Double(p.getX()-1,p.getY()-1,2,2));}g.setColor(Color.BLACK);g.drawString("X,Y,Z in [-1,1]",(float)r.getX(),(float)r.getMaxY()+15);}
    private void axisText(Graphics2D g,Rectangle2D r,double xmin,double xmax,double ymin,double ymax) {g.drawString(String.format(java.util.Locale.ROOT,"%.3g",xmin),(float)r.getX(),(float)r.getMaxY()+15);g.drawString(String.format(java.util.Locale.ROOT,"%.3g",xmax),(float)r.getMaxX()-30,(float)r.getMaxY()+15);g.drawString(String.format(java.util.Locale.ROOT,"%.3g",ymin),(float)r.getX()-35,(float)r.getMaxY());g.drawString(String.format(java.util.Locale.ROOT,"%.3g",ymax),(float)r.getX()-35,(float)r.getY()+10);}
    public boolean isNDF(){return true;}public void configure(JComponent parent){}public void setLevel(int level){this.level=level;}public int getLevel(){return level;}
}
