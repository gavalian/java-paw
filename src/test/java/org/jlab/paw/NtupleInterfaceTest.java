package org.jlab.paw;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import twig.graphics.TGDataCanvas;
import static org.junit.Assert.*;

public class NtupleInterfaceTest {
    @Test public void registryBrowserAndNtuplePlotsShareTheCanvas()throws Exception{
        SwingUtilities.invokeAndWait(()->{Workstation app=new Workstation(true,false,true);try{
            app.setSize(1440,1000);app.tile();layout(app);app.previewCommand("NT/CREATE 10 'New Ntuple' 3 ! ! X Y Z");
            JList<?>classes=(JList<?>)named(app,"browserClasses"),objects=(JList<?>)named(app,"browserObjects");classes.setSelectedValue("Ntuples",true);assertEquals("Ntuple: //PAWC/10",objects.getModel().getElementAt(0));objects.setSelectedIndex(0);
            Rectangle rect=objects.getCellBounds(0,0);objects.dispatchEvent(new MouseEvent(objects,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,rect.x+10,rect.y+10,2,false,MouseEvent.BUTTON1));assertEquals(5,app.windows().size());assertTrue(text(app.windows().getLast(),"First row (1-based)"));
            var field=Workstation.class.getDeclaredField("workspace");field.setAccessible(true);Workspace workspace=(Workspace)field.get(app);workspace.ntuples().get("10").fill(1,2,3);workspace.ntuples().get("10").fill(2,4,6);
            app.previewCommand("ZONE 2 2");app.previewCommand("NT/PLOT 10.X 1 ! ! 1");assertEquals(2,((JSpinner)named(app,"activeZone")).getValue());TGDataCanvas canvas=find(app,TGDataCanvas.class);assertEquals(1,canvas.region(0).getAxisFrame().getDataNodes().size());assertTrue(canvas.region(1).getAxisFrame().getDataNodes().isEmpty());
            app.previewCommand("NT/PLOT 10.Y 1 ! ! ! S");assertEquals(2,canvas.region(0).getAxisFrame().getDataNodes().size());assertEquals(2,((JSpinner)named(app,"activeZone")).getValue());app.previewCommand("NT/PLOT 10.Y%X%Z");assertEquals(3,((JSpinner)named(app,"activeZone")).getValue());
            app.previewCommand("H/DEL 10");classes.setSelectedValue("PAWC",true);classes.setSelectedValue("Ntuples",true);assertEquals(0,objects.getModel().getSize());
        }catch(Exception e){throw new RuntimeException(e);}finally{app.shutdown();}});
    }
    @Test public void graphicalCutPromptCollectsActualMouseCoordinates()throws Exception{
        SwingUtilities.invokeAndWait(()->{GraphicalCutPrompt p=new GraphicalCutPrompt("$1",List.of("Y","X"),List.of(new double[]{0,0},new double[]{10,10}));p.setSize(680,520);layout(p);BufferedImage image=new BufferedImage(680,520,BufferedImage.TYPE_INT_RGB);p.paint(image.getGraphics());for(int[]xy:new int[][]{{80,80},{400,80},{400,300}})p.drawing.dispatchEvent(new MouseEvent(p.drawing,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,xy[0],xy[1],1,false));assertTrue(p.apply.isEnabled());p.apply.doClick();assertEquals(3,p.answer.join().size());assertTrue(p.answer.join().getFirst()[0]>0);});
    }
    @Test public void parameterFormsPermitPawBlankDefaults()throws Exception{
        SwingUtilities.invokeAndWait(()->{var spec=NtupleCommands.spec("NTUPLE/CREATE");CommandArgumentPrompt panel=new CommandArgumentPrompt(spec,List.of("10","title","2"));((JTextField)named(panel,"commandArgument5")).setText("X Y");findButton(panel,"OK").doClick();assertTrue(panel.answer().isDone());assertEquals(" ",panel.answer().join().get(3));});
    }
    private static boolean text(Container root,String text){for(Component c:root.getComponents()){if(c instanceof JLabel l&&l.getText().equals(text))return true;if(c instanceof Container inner&&text(inner,text))return true;}return false;}
    private static JButton findButton(Container root,String text){for(Component c:root.getComponents()){if(c instanceof JButton b&&b.getText().equals(text))return b;if(c instanceof Container inner){var b=findButton(inner,text);if(b!=null)return b;}}return null;}
    private static Component named(Container root,String name){for(Component c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container inner){var found=named(inner,name);if(found!=null)return found;}}return null;}
    private static <T>T find(Container root,Class<T>type){for(Component c:root.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container inner){T found=find(inner,type);if(found!=null)return found;}}return null;}
    private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container inner)layout(inner);}
}
