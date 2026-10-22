package org.jlab.paw;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;

/** Mouse range/polygon selection on the actual projected data, without blocking Swing. */
final class GraphicalCutPrompt extends JPanel {
    final CompletableFuture<List<double[]>> answer=new CompletableFuture<>();
    final List<double[]>points,vertices=new ArrayList<>();final boolean range;
    final double xmin,xmax,ymin,ymax;final JButton apply=new JButton("Use cut");final JLabel hint=new JLabel();
    JDialog dialog;
    final JPanel drawing;
    GraphicalCutPrompt(String id,List<String>names,List<double[]>points){
        super(new BorderLayout(8,8));this.points=points;range=names.size()==1;
        double lo=points.stream().mapToDouble(p->p[0]).min().orElse(0),hi=points.stream().mapToDouble(p->p[0]).max().orElse(1);double pad=lo==hi?1:(hi-lo)*.05;xmin=lo-pad;xmax=hi+pad;
        lo=points.stream().mapToDouble(p->p[1]).min().orElse(0);hi=points.stream().mapToDouble(p->p[1]).max().orElse(1);pad=lo==hi?1:(hi-lo)*.05;ymin=lo-pad;ymax=hi+pad;
        setBorder(BorderFactory.createEmptyBorder(12,12,12,12));hint.setText(range?"Click the lower and upper range endpoints.":"Click polygon vertices, then choose Use cut. Double-click to finish.");add(hint,BorderLayout.NORTH);
        drawing=new JPanel(){@Override protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g2.setColor(new Color(35,93,158,85));for(double[]p:points)g2.fill(new Ellipse2D.Double(px(p[0])-1,py(p[1])-1,3,3));g2.setColor(new Color(0,95,210));Path2D path=new Path2D.Double();for(int i=0;i<vertices.size();i++){double[]v=vertices.get(i);if(i==0)path.moveTo(px(v[0]),py(v[1]));else path.lineTo(px(v[0]),py(v[1]));g2.fill(new Ellipse2D.Double(px(v[0])-4,py(v[1])-4,8,8));if(range)g2.draw(new Line2D.Double(px(v[0]),20,px(v[0]),getHeight()-35));}if(!range&&vertices.size()>=3)path.closePath();g2.setStroke(new BasicStroke(2));g2.draw(path);g2.setColor(Color.DARK_GRAY);g2.drawRect(40,20,getWidth()-60,getHeight()-55);g2.drawString(names.getLast()+String.format(java.util.Locale.ROOT," [%.4g, %.4g]",xmin,xmax),45,getHeight()-10);if(!range)g2.drawString(names.getFirst()+String.format(java.util.Locale.ROOT," [%.4g, %.4g]",ymin,ymax),45,15);g2.dispose();}};
        drawing.setBackground(Color.WHITE);drawing.setPreferredSize(new Dimension(640,440));drawing.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(e.getX()<40||e.getX()>drawing.getWidth()-20||e.getY()<20||e.getY()>drawing.getHeight()-35)return;if(e.getClickCount()>1){if(apply.isEnabled())submit();return;}if(range&&vertices.size()==2)vertices.clear();vertices.add(new double[]{xmin+(e.getX()-40.)/(drawing.getWidth()-60)*(xmax-xmin),range?0:ymax-(e.getY()-20.)/(drawing.getHeight()-55)*(ymax-ymin)});apply.setEnabled(range?vertices.size()==2:vertices.size()>=3);drawing.repaint();}});add(drawing,BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));JButton clear=new JButton("Clear"),cancel=new JButton("Cancel");clear.addActionListener(e->{vertices.clear();apply.setEnabled(false);drawing.repaint();});cancel.addActionListener(e->answer.cancel(false));apply.setEnabled(false);apply.addActionListener(e->submit());buttons.add(clear);buttons.add(cancel);buttons.add(apply);add(buttons,BorderLayout.SOUTH);
    }
    double px(double x){return 40+(x-xmin)/(xmax-xmin)*(drawing.getWidth()-60);}
    double py(double y){return 20+(ymax-y)/(ymax-ymin)*(drawing.getHeight()-55);}
    private void submit(){answer.complete(vertices.stream().map(double[]::clone).toList());}
    void show(Window owner,String id){dialog=new JDialog(owner,"Graphical cut "+id,Dialog.ModalityType.MODELESS);dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);dialog.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){answer.cancel(false);}});dialog.setContentPane(this);dialog.pack();dialog.setLocationRelativeTo(owner);dialog.setVisible(true);}
    void close(){if(dialog!=null)dialog.dispose();}
}
