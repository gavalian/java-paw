package org.jlab.paw;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.awt.image.BufferedImage;
import j4np.hipo5.data.Schema;
import twig.data.*;
import twig.graphics.*;
import twig.widgets.Widget;

public class TutorialMacrosTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static class View implements Commands.View {
        final TGDataCanvas canvas=new TGDataCanvas();int region=0,plots=0,panels=0,reads=0,delayRequests=0;
        View(){canvas.setSize(900,700);}
        public String readInput(String prompt,String value){ if(prompt.startsWith("Delay between screens")){delayRequests++;assertEquals("-1",value);return value;} assertTrue(plots>0); assertTrue(prompt.contains("Page "+(++reads)+" of 59")); return value; }
        public void plot(DataSet ds,boolean same){draw(ds,ds instanceof GraphErrors?ds.attr().getDrawOptions():"",same);}
        public void draw(DataSet ds,String options,boolean same){canvas.region(region).drawFrame(true).draw(ds,(same?"same":"")+options);plots++;}
        public void zones(int c,int r){canvas.divide(c,r);region=0;}
        public void zone(int n){region=n;canvas.cd(n);}
        public void widget(Widget w,boolean clear){if(clear){canvas.region(region).clear();canvas.region(region).drawFrame(false);}canvas.region(region).draw(w);plots++;}
        public void configure(Consumer<TGRegion> action){action.accept(canvas.region(region));}
        public void panel(String title,Map<String,String> buttons){assertFalse(buttons.isEmpty());panels++;}
        public void ntuple(Path file,List<Schema> schemas){assertFalse(schemas.isEmpty());}
        public BufferedImage capture(){return canvas.getScreenShot();}
        public void export(Path path){try{GraphicsExport.write(canvas,path);}catch(Exception ex){throw new IllegalStateException(ex);}}
    }
    private Path tutorialCopy() throws Exception {
        Path source=Path.of("macros/tutorial"),dest=temp.newFolder("tutorial").toPath();
        try(var paths=Files.walk(source)){for(Path p:paths.toList()){Path target=dest.resolve(source.relativize(p));if(Files.isDirectory(p))Files.createDirectories(target);else Files.copy(p,target);}}
        return dest;
    }
    @Test public void all59TopicsCompileRunAndRenderIndependently() throws Exception {
        Path dir=tutorialCopy();List<Path> files;
        try(var paths=Files.list(dir)){files=paths.filter(p->p.getFileName().toString().matches("[0-9]{2}_.+\\.jmacro")&&!p.getFileName().toString().startsWith("00_")).sorted().toList();}
        assertEquals(59,files.size());
        for(Path file:files){System.out.println("Tutorial: "+file.getFileName());View view=new View();Workspace workspace=new Workspace();Commands commands=new Commands(workspace,view,t->{});
            commands.execute("MACRO/EXEC "+Commands.quote(file.toString()));assertTrue(file.toString(),view.plots>0);BufferedImage image=view.capture();assertEquals(900,image.getWidth());
            int nonWhite=0;for(int y=0;y<image.getHeight();y+=10)for(int x=0;x<image.getWidth();x+=10)if((image.getRGB(x,y)&0xffffff)!=0xffffff)nonWhite++;assertTrue("No rendered content: "+file,nonWhite>20);
            if(file.getFileName().toString().startsWith("48_"))assertEquals(335400,workspace.histogram("age").integral(),0);
            if(file.getFileName().toString().startsWith("55_"))assertEquals(50000,workspace.histogram("dice").integral(),0);
        }
        assertTrue(Files.readString(dir.resolve("output/tutorial.eps")).startsWith("%!PS-Adobe-3.0 EPSF"));assertTrue(Files.readString(dir.resolve("output/tutorial.ps")).contains("colorimage"));
    }
    @Test public void tutorialMastersRunInOneSessionAndPrintEveryExample() throws Exception {
        Path dir=tutorialCopy();View view=new View();Commands commands=new Commands(new Workspace(),view,t->{});
        commands.execute("MACRO/EXEC "+Commands.quote(dir.resolve("00_run_all.kumac").toString()));assertEquals(1,view.panels);assertEquals(59,view.reads);assertEquals(1,view.delayRequests);
        commands.execute("MACRO/EXEC "+Commands.quote(dir.resolve("00_print_all.jmacro").toString()));assertEquals(2,view.panels);
        try(var paths=Files.list(dir.resolve("output"))){assertEquals(59,paths.filter(p->p.getFileName().toString().matches("[0-9]{2}_.+\\.pdf")).count());}
        Path all=dir.resolve("output/tutorial_all.pdf");assertTrue(Files.size(all)>10000);assertEquals("%PDF",Files.readString(all,java.nio.charset.StandardCharsets.ISO_8859_1).substring(0,4));
    }
    @Test public void bookHistogramColorsProduceActualBlueAndGreenPixels() throws Exception {
        View view=new View();Commands commands=new Commands(new Workspace(),view,t->{});commands.execute("MACRO/EXEC macros/04_histogram_colors.kumac");
        BufferedImage image=view.capture();int blue=0,green=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int rgb=image.getRGB(x,y),r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;if(b>200&&r<80&&g<80)blue++;if(g>200&&r<80&&b<80)green++;}assertTrue("Blue outline missing",blue>100);assertTrue("Green fill missing",green>1000);
    }
    @Test public void tableMasksProfilesTypesChainsAndCutsPreserveData() throws Exception {
        TutorialTable t=new TutorialTable("X","Y","Code");t.add(.5,2,7);t.add(.5,4,8);t.add(1.5,10,7);
        H1F profile=t.profile("p","X","Y",2,0,2);assertEquals(3,profile.getBinContent(0),0);assertEquals(1,profile.getBinError(0),0);
        assertEquals(2,t.masked(t.mask("Code==7")).size());assertEquals(1,t.select("Y>3 && Code==8").size());
        assertEquals(1,t.select("mod(int(Code),2)==0").size());
        assertEquals(-2,Expression.compile("int(-2.9)",List.of()).applyAsDouble(new double[0]),0);
        Path file=temp.newFile("typed.hipo").toPath();t.writeTyped(file,Map.of("Code","B","X","F"));TutorialTable read=TutorialTable.read(file);assertArrayEquals(t.column("Y"),read.column("Y"),0);assertEquals(6,TutorialTable.chain(List.of(file,file)).size());
        assertTrue(TutorialTable.inside(.5,.5,new double[]{0,1,1,0},new double[]{0,0,1,1}));assertFalse(TutorialTable.inside(2,.5,new double[]{0,1,1,0},new double[]{0,0,1,1}));
    }
    @Test public void nestedJavaMacrosShareArgumentsAndGlobalsAndReportCompileLines() throws Exception {
        Path dir=temp.newFolder("nested").toPath();Files.writeString(dir.resolve("child.jmacro"),"paw.globals.put(\"result\",paw.integerArgument(0,0)); paw.print(paw.globals.get(\"message\"));\n");
        Files.writeString(dir.resolve("parent.jmacro"),"paw.globals.put(\"message\",\"hello child\");paw.exec(\"child.jmacro\",\"23\");if(!paw.globals.get(\"result\").equals(23))throw new Exception(\"arguments lost\");\n");
        List<String> output=new ArrayList<>();Commands commands=new Commands(new Workspace(),new View(),output::add);commands.execute("MACRO/EXEC "+Commands.quote(dir.resolve("parent.jmacro").toString()));assertTrue(output.contains("hello child"));
        Path bad=dir.resolve("bad.jmacro");Files.writeString(bad,"// first line\nno_such_symbol();\n");try{commands.execute("MACRO/EXEC "+Commands.quote(bad.toString()));fail();}catch(IllegalArgumentException ex){assertTrue(ex.getMessage(),ex.getMessage().contains("bad.jmacro:2:"));}
    }
}
