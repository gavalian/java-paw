package org.jlab.paw;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import javax.swing.JComponent;
import j4np.graphics.Translation2D;
import twig.widgets.Widget;

/** Ntuple 3D scatter and fourth-variable colour, with gridded mean/min/max surfaces. */
final class NtupleCloud implements Widget {
    final List<double[]>points;final List<String>names;final String option,title;
    final double[]low,high;int level=1;
    NtupleCloud(List<double[]>points,List<String>names,String option,String title){this.points=points;this.names=names;this.option=option;this.title=title;low=new double[names.size()];high=new double[names.size()];java.util.Arrays.fill(low,Double.POSITIVE_INFINITY);java.util.Arrays.fill(high,Double.NEGATIVE_INFINITY);for(var p:points)for(int j=0;j<p.length;j++){low[j]=Math.min(low[j],p[j]);high[j]=Math.max(high[j],p[j]);}for(int j=0;j<low.length;j++)if(low[j]==high[j]){low[j]-=.5;high[j]+=.5;}}
    double fraction(double x,int i){return (x-low[i])/(high[i]-low[i]);}
    Point2D project(Rectangle2D r,double x,double y,double z){return new Point2D.Double(r.getCenterX()+(x-y)*r.getWidth()*.34,r.getMaxY()-r.getHeight()*.12-(x+y)*r.getHeight()*.2-z*r.getHeight()*.52);}
    @Override public void draw(Graphics2D source,Rectangle2D frame,Translation2D tr){Graphics2D g=(Graphics2D)source.create();try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);Rectangle2D r=new Rectangle2D.Double(frame.getX()+35,frame.getY()+30,frame.getWidth()-70,frame.getHeight()-80);g.setColor(Color.DARK_GRAY);g.setFont(new Font("SansSerif",Font.PLAIN,12));g.drawString(title,(float)frame.getX()+8,(float)frame.getY()+16);if(!option.contains("A")){for(int axis=0;axis<3;axis++){double[]v={0,0,0};v[axis]=1;Point2D origin=project(r,0,0,0),end=project(r,v[0],v[1],v[2]);g.draw(new Line2D.Double(origin,end));int col=axis==0?1:axis==1?0:2;g.drawString(names.get(col),(float)end.getX()-35,(float)end.getY());}}
        if(option.contains("G"))surface(g,r);else{Point2D previous=null;for(double[]p:points){Point2D xy=project(r,fraction(p[1],1),fraction(p[0],0),fraction(p[2],2));double colour=names.size()==4?fraction(p[3],3):fraction(p[2],2);g.setColor(Color.getHSBColor((float)(.67*(1-colour)),.8f,.85f));if(option.contains("L")&&previous!=null)g.draw(new Line2D.Double(previous,xy));g.fill(new Ellipse2D.Double(xy.getX()-2,xy.getY()-2,4,4));previous=xy;}}
        if(!option.contains("A")){g.setColor(Color.DARK_GRAY);g.setFont(new Font("SansSerif",Font.PLAIN,10));for(int j=0;j<3;j++)g.drawString(names.get(j)+String.format(java.util.Locale.ROOT," [%.3g, %.3g]",low[j],high[j]),(float)frame.getX()+8,(float)frame.getMaxY()-32+j*12);}
        if(names.size()==4&&!option.contains("A")){double top=frame.getY()+35,height=Math.max(30,frame.getHeight()-85);for(int i=0;i<100;i++){g.setColor(Color.getHSBColor((float)(.67*(1-i/99.)),.8f,.85f));g.fill(new Rectangle2D.Double(frame.getMaxX()-12,top+i*height/100,8,height/100+1));}g.setColor(Color.BLACK);g.drawString(names.get(3),(float)frame.getMaxX()-16,(float)(top+height+12));g.drawString(String.format(java.util.Locale.ROOT,"%.2g",low[3]),(float)frame.getMaxX()-35,(float)top-5);g.drawString(String.format(java.util.Locale.ROOT,"%.2g",high[3]),(float)frame.getMaxX()-35,(float)(top+height));}
    }finally{g.dispose();}}
    private void surface(Graphics2D g,Rectangle2D r){int n=16;double[][]sum=new double[n][n],min=new double[n][n],max=new double[n][n];int[][]count=new int[n][n];for(double[]v:min)java.util.Arrays.fill(v,Double.POSITIVE_INFINITY);for(double[]v:max)java.util.Arrays.fill(v,Double.NEGATIVE_INFINITY);for(var p:points){int x=Math.min(n-1,(int)(fraction(p[1],1)*n)),y=Math.min(n-1,(int)(fraction(p[0],0)*n));double z=fraction(p[2],2);sum[x][y]+=z;count[x][y]++;min[x][y]=Math.min(min[x][y],z);max[x][y]=Math.max(max[x][y],z);}for(int surface=0;surface<3;surface++)for(int x=n-2;x>=0;x--)for(int y=n-2;y>=0;y--){int[][]corners={{x,y},{x+1,y},{x+1,y+1},{x,y+1}};if(java.util.Arrays.stream(corners).anyMatch(c->count[c[0]][c[1]]==0))continue;Path2D path=new Path2D.Double();for(int k=0;k<4;k++){int a=corners[k][0],b=corners[k][1];double z=surface==0?min[a][b]:surface==1?sum[a][b]/count[a][b]:max[a][b];Point2D p=project(r,a/(double)(n-1),b/(double)(n-1),z);if(k==0)path.moveTo(p.getX(),p.getY());else path.lineTo(p.getX(),p.getY());}path.closePath();g.setColor(surface==1?new Color(30,180,65,180):surface==0?new Color(45,110,205,90):new Color(205,65,45,90));g.fill(path);}}
    public boolean isNDF(){return true;}public void configure(JComponent parent){}public void setLevel(int level){this.level=level;}public int getLevel(){return level;}
}
