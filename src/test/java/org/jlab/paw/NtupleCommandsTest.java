package org.jlab.paw;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import twig.data.*;
import twig.widgets.Widget;
import static org.junit.Assert.*;

public class NtupleCommandsTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    final Workspace w=new Workspace();final List<String>out=new ArrayList<>();final List<DataSet>plots=new ArrayList<>();final List<Boolean>overlay=new ArrayList<>();final List<Widget>widgets=new ArrayList<>();
    final Commands c=new Commands(w,new Commands.View(){
        public void plot(DataSet ds,boolean same){plots.add(ds);overlay.add(same);}
        public void zones(int x,int y){}public void zone(int i){}public void export(Path p){}
        public void widget(Widget widget,boolean clear){widgets.add(widget);}
        public List<double[]>graphicalCut(String id,List<String>names,List<double[]>points){return names.size()==1?List.of(new double[]{1,0},new double[]{2,0}):List.of(new double[]{0,0},new double[]{4,0},new double[]{4,5},new double[]{0,5});}
    },out::add);
    Path path(String name){return temp.getRoot().toPath().resolve(name);}
    String q(Path p){return Commands.quote(p.toString());}
    void tuple()throws Exception{c.execute("NT/CREATE 10 'test rows' 3 ! 100 X Y Z");var t=w.ntuples().get("10");t.fill(1,2,3);t.fill(2,4,6);t.fill(3,6,9);}
    @Test public void weightedProjectionRangesCutsAndOverlays()throws Exception{
        tuple();c.execute("HI/BOOK 20 'fixed bins' 5 0 5");c.execute("NT/CUTS $1 '1<=x<=2'");c.execute("NT/PROJ 20 10.X '$1*Y'");assertEquals(6,w.histogram("20").integral(),1e-6);c.execute("NT/PROJ 20 10.X '$1*Y'");assertEquals(12,w.histogram("20").integral(),1e-6);assertTrue(plots.isEmpty());
        c.execute("NT/PLOT 10.X 1 1 2 ! ! 20");assertEquals(1,w.histogram("20").integral(),1e-6);assertEquals(1,w.histogram("20").getBinContent(2),1e-6);
        c.execute("NT/PLOT 10.Y%X 0");assertTrue(w.get("1000000") instanceof H2F);assertEquals(3,((H2F)w.get("1000000")).integral(),1e-6);
        c.execute("NT/PLOT 10.X 1 ! ! ! S");assertTrue(overlay.getLast());assertTrue(plots.get(plots.size()-2) instanceof H2F);
        c.execute("NT/PLOT 10.X 1 ! ! ! +");assertEquals(6,w.histogram("1000000").integral(),1e-6);
        int drawn=plots.size();c.execute("NT/PLOT 10.X 1 ! ! ! N");assertEquals(drawn,plots.size());
        try{c.execute("NT/PLOT 10.X 1 ! ! ! PROF");fail("Profile must require two expressions");}catch(IllegalArgumentException expected){}
    }
    @Test public void hipoRoundTripIDsTypesDuplicatesAndLogicalUnits()throws Exception{
        tuple();c.execute("NT/DUPLICATE 10 11");assertEquals(0,w.ntuples().get("11").size());c.execute("NT/PRINT 10");assertTrue(out.stream().anyMatch(s->s.contains("3 rows")));
        c.execute("HI/FILE 7 "+q(path("data.hipo"))+" ! N");c.execute("CD //PAWC");w.ntuples().write(path("data.hipo"),w.ntuples().tuples().values());c.execute("CD //LUN7");c.execute("HRIN");assertEquals(3,w.ntuples().get("//PAWC/10").size());c.execute("CLOSE 7");assertEquals("//PAWC",w.ntuples().directory());
        Workspace loaded=new Workspace();loaded.ntuples().open(8,path("data.hipo"),false);assertEquals(List.of("X","Y","Z"),loaded.ntuples().get("10").columns);assertEquals(3,loaded.ntuples().get("10").size());
        c.execute("HI/BOOK 1 'saved histogram' 10 0 10");w.save(path("workspace.hipo"));Workspace all=new Workspace();all.load(path("workspace.hipo"));assertEquals(3,all.ntuples().get("10").size());assertNotNull(all.histogram("1"));
    }
    @Test public void masksArePersistentAndRespectBit32AndReadOnly()throws Exception{
        tuple();c.execute("MASK/FILE "+q(path("selected.hipo"))+" N");c.execute("NT/LOOP 10 'X<=2>>selected(32)'");c.execute("MASK/CLOSE selected");c.execute("MASK/FILE "+q(path("selected.hipo"))+" R");c.execute("NT/PLOT 10.X 'selected(32).AND.Y>2'");assertEquals(1,w.histogram("1000000").integral(),1e-6);
        try{c.execute("MASK/RESET selected 32");fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("Read-only"));}
        c.execute("MASK/CLOSE selected");c.execute("MASK/FILE "+q(path("selected.hipo"))+" U");c.execute("MASK/RESET selected 32");c.execute("NT/PLOT 10.X 'selected(32)'");assertEquals(0,w.histogram("1000000").integral(),1e-6);c.execute("MASK/LIST");
    }
    @Test public void callbacksLoopsEmbeddedExpressionsAndPca()throws Exception{
        tuple();Path callback=path("select.jmacro");c.execute("NT/UWFUNC 10 "+q(callback)+" P");Files.writeString(callback,"paw.weight(paw.value(\"X\")<=2?2:0);\n");c.execute("NT/PLOT 10.X "+q(callback));assertEquals(4,w.histogram("1000000").integral(),1e-6);
        Path spaced=path("callback with spaces.jmacro");Files.writeString(spaced,"paw.weight(3);\n");c.execute("NT/PLOT 10.X "+q(spaced));assertEquals(9,w.histogram("1000000").integral(),1e-6);
        Files.writeString(callback,"paw.weight(paw.value(\"X\")*2);\n");c.execute("NT/PLOT 10.X/Y '"+callback+">3'");assertEquals(2,w.histogram("1000000").integral(),1e-6);
        Files.writeString(callback,"paw.globals.put(\"visits\", ((Integer)paw.globals.getOrDefault(\"visits\",0))+1); paw.weight(1);\n");c.execute("NT/LOOP 10 "+q(callback)+" 2 2");Files.writeString(callback,"paw.print(paw.globals.get(\"visits\"));");c.execute("MACRO/EXEC "+q(callback));assertEquals("2",out.getLast());
        Path macro=path("pca.kumac");Files.writeString(macro,"NT/LINTRA 10 N ! ! 2 X Y\nNT/PLOT 10.xtoxsi.jmacro(1)\n");c.execute("MACRO/EXEC "+q(macro));assertEquals(3,w.histogram("1000000").integral(),1e-6);assertTrue(out.stream().anyMatch(s->s.contains("eigenvalue=2.0")));assertTrue(Files.exists(path("xtoxsi.jmacro")));
    }
    @Test public void asciiReadScanDumpCutsAndInvalidReadIsAtomic()throws Exception{
        tuple();Path ascii=path("rows.txt");Files.writeString(ascii,"4 8 12\n5 10 15\n");c.execute("NT/READ 10 "+q(ascii));assertEquals(5,w.ntuples().get("10").size());Files.writeString(ascii,"6 12 18\nwrong row\n");try{c.execute("NT/READ 10 "+q(ascii));fail();}catch(java.io.IOException expected){}assertEquals(5,w.ntuples().get("10").size());
        c.execute("NT/SCAN 10 'X>2' 2 2 ! X:Y");assertTrue(out.stream().anyMatch(s->s.contains("3  3.00000000000  6.00000000000")));
        c.execute("NT/DUMP 10.Y%X 'X>2' ! ! "+q(path("dump.txt"))+" ! ,");assertEquals(List.of("6.00000000000,3.00000000000","8.00000000000,4.00000000000","10.0000000000,5.00000000000"),Files.readAllLines(path("dump.txt")));
        c.execute("CUTS 1 'X>2'");c.execute("CUTS 0 W "+q(path("cuts.txt")));c.execute("CUTS 0 -");c.execute("CUTS 0 R "+q(path("cuts.txt")));c.execute("NT/PLOT 10.X $1");assertEquals(3,w.histogram("1000000").integral(),1e-6);
    }
    @Test public void chainsMergeRecoverAndCache()throws Exception{
        tuple();w.ntuples().write(path("first.hipo"),w.ntuples().tuples().values());w.ntuples().write(path("second.hipo"),w.ntuples().tuples().values());c.execute("CHAIN week "+q(path("first.hipo")));c.execute("CHAIN week "+q(path("second.hipo")));c.execute("CHAIN year week");c.execute("CD //year");c.execute("NT/PLOT 10.X");assertEquals(6,w.histogram("1000000").integral(),1e-6);c.execute("CHAIN year>");c.execute("NT/VMEM -2");assertFalse(w.ntuples().cachedBanks().isEmpty());c.execute("NT/VMEM 0");assertTrue(w.ntuples().cachedBanks().isEmpty());
        c.execute("NT/HMERGE "+q(path("merged.hipo"))+" "+q(path("first.hipo"))+" "+q(path("second.hipo")));c.execute("HI/FILE 9 "+q(path("merged.hipo")));assertEquals(6,w.ntuples().get("10").size());c.execute("NT/RECOVER 10");assertEquals(6,w.ntuples().get("10").size());
        c.execute("CHAIN -year");c.execute("CHAIN -");assertTrue(w.ntuples().chains().isEmpty());
    }
    @Test public void profilesGraphicalCutsAndMultidimensionalPlots()throws Exception{
        tuple();c.execute("NT/PLOT 10.Y%X 1 ! ! ! PROF");H1F h=w.histogram("1000000");assertEquals(4,h.getBinContent(h.getAxis().getBin(2)),1e-6);assertTrue(plots.getLast() instanceof GraphErrors);
        c.execute("NT/GCUT 1 10.X");c.execute("NT/PLOT 10.X $1");assertEquals(2,w.histogram("1000000").integral(),1e-6);
        c.execute("NT/GCUT 2 10.Y%X");c.execute("NT/PLOT 10.X $2");assertEquals(2,w.histogram("1000000").integral(),1e-6);c.execute("CUTS 2 D");
        c.execute("NT/PLOT 10.Y%X%Z");assertTrue(widgets.getLast() instanceof NtupleCloud);c.execute("NT/PLOT 10.Y%X%Z%X");assertTrue(widgets.getLast() instanceof NtupleCloud);
        c.execute("NT/SCAN 10 1 1 1 SA X:Y Z");assertTrue(plots.getLast() instanceof GraphErrors);c.execute("NT/CSELECT N");c.execute("NT/PLOT 10.X 'X>1'");assertTrue(widgets.getLast() instanceof twig.widgets.PaveText);
    }
    @Test public void commandReferenceAndInteractiveMissingArguments()throws Exception{
        final List<String>forms=new ArrayList<>();Commands interactive=new Commands(w,new Commands.View(){public void plot(DataSet d,boolean s){}public void zones(int x,int y){}public void zone(int i){}public void export(Path p){}public List<String>requestArguments(CommandArguments.Spec spec,List<String>provided){forms.add(spec.command());assertEquals(List.of("13"),provided);return List.of("13","from panel","2","!","100","X Y");}},out::add);
        interactive.executeInteractive("NT/CREATE 13");assertEquals(List.of("NTUPLE/CREATE"),forms);assertEquals(2,w.ntuples().get("13").columns.size());c.execute("NT/HELP");assertTrue(out.getLast().contains("NTUPLE/LINTRA"));c.execute("MASK/HELP");assertTrue(out.getLast().contains("NTUPLE/MASK/RESET"));c.execute("NT/FLAGS_QP TIMING 1");c.execute("NT/LIST");assertTrue(out.getLast().contains("Query completed"));
        for(var d:NtupleCommands.DEFINITIONS){assertEquals(d.labels().split(";",-1).length,d.defaults().split(";",-1).length);assertNotNull(CommandArguments.spec(d.command(),w,List.of(d.command())));}
    }
    @Test public void fixedFormatsMatchPatternsAndKeywordArguments()throws Exception{
        c.execute("NT/CREATE 9 'fixed rows' 2 ! ! X Y");Path ascii=path("fixed.txt");Files.writeString(ascii," 0010000200 C\n 0030000400\n");c.execute("NT/READ 9 "+q(ascii)+" '(1X,2F5.2)' ! ! '-/C/(*)'");assertEquals(1,w.ntuples().get("9").size());List<double[]>rows=new ArrayList<>();w.ntuples().get("9").rows(1,100,(i,row)->rows.add(row));assertArrayEquals(new double[]{3,4},rows.getFirst(),1e-6);
        Files.writeString(ascii,"5 6\n7 8\n");c.execute("NT/READ 9 "+q(ascii)+" MATCH=/5/");assertEquals(2,w.ntuples().get("9").size());
        c.execute("NT/PLOT 9.X UWFUNC='X.GE.4' OPTION=N");assertEquals(1,w.histogram("1000000").integral(),1e-6);assertTrue(plots.isEmpty());c.execute("NT/SCAN 9 NEVENT=1 IFIRST=2 VARLIS=X:Y");assertTrue(out.stream().anyMatch(line->line.startsWith("2  5.000")));
        assertEquals(List.of("1.0"),new FortranRowFormat("(0P,F5.2)").read("00100"));assertEquals(List.of("10.0"),new FortranRowFormat("(-1P,F5.2)").read("00100"));
        assertEquals(1.5,NtupleExpressions.compile("DSQRT(1D0)+ALOG(EXP(.5))",List.of()).applyAsDouble(new double[0]),1e-9);
    }
    @Test public void nativeTypesDirectoriesAndOverwriteRemainConsistent()throws Exception{
        var t=w.ntuples().create("1","typed",List.of("b:B","s:S","i:I","l:L","f:F","d:D"));t.fill(127,32767,2147483647,9007199254740991d,1.25,Math.PI);Path file=path("typed.hipo");w.ntuples().write(file,List.of(t));w.ntuples().open(4,file,false);var disk=w.ntuples().get("1");assertEquals(t.types,disk.types);disk.fill(0,0,0,0,0,0);w.ntuples().write(file,List.of(disk));assertEquals(2,disk.size());
        w.ntuples().cd("//PAWC/sub");w.ntuples().create("1","another directory",List.of("X"));w.save(path("directories.hipo"));Workspace restored=new Workspace();restored.load(path("directories.hipo"));assertNotNull(restored.ntuples().get("//PAWC/sub/1"));assertEquals(1,restored.ntuples().get("//PAWC/1").size());
        try{t.fill(128,0,0,0,0,0);fail();}catch(IllegalArgumentException expected){}assertEquals(1,t.size());
    }
    @Test public void physicalRecoveryRebuildsAMissingTrailerIndex()throws Exception{
        tuple();Path file=path("broken.hipo");w.ntuples().write(file,w.ntuples().tuples().values());c.execute("HI/FILE 3 "+q(file));try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.WRITE)){channel.position(40);channel.write(java.nio.ByteBuffer.allocate(8));}
        try{w.ntuples().get("10").size();fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("RECOVER"));}
        c.execute("NT/RECOVER 10");assertEquals(3,w.ntuples().get("10").size());assertEquals("test rows",w.ntuples().get("10").title);
    }
    @Test public void mergedHistogramsAndProfileAccumulation()throws Exception{
        tuple();c.execute("HI/BOOK 1 title 5 0 5");c.execute("HI/FILL 1 1 2");w.save(path("a.hipo"));w.save(path("b.hipo"));c.execute("NT/HMERGE "+q(path("sum.hipo"))+" "+q(path("a.hipo"))+" "+q(path("b.hipo")));Workspace sum=new Workspace();sum.load(path("sum.hipo"));assertEquals(4,sum.histogram("1").integral(),1e-6);assertEquals(6,sum.ntuples().get("10").size());
        c.execute("HI/BOOK 20 profile 1 0 10");c.execute("NT/PLOT 10.Y%X 1 ! ! ! PROF 20");assertEquals(4,w.histogram("20").getBinContent(0),1e-6);w.ntuples().get("10").fill(4,8,12);c.execute("NT/PLOT 10.Y%X 1 1 4 ! 'PROF+' 20");assertEquals(5,w.histogram("20").getBinContent(0),1e-6);
    }

    @Test public void randomCommandCreatesTypedStreamedHipoData()throws Exception{
        Path file=path("random numbers.hipo");c.execute("NT/RANDOM 'a/I,b/F,c' 5000 "+q(file)+" 42 12345");
        var tuple=w.ntuples().get("42");assertEquals(List.of("a","b","c"),tuple.columns);assertEquals(List.of("I","F","F"),tuple.types);assertEquals(5000,tuple.size());assertTrue(tuple.added.isEmpty());assertEquals(1,tuple.sources.size());
        List<double[]>rows=new ArrayList<>();tuple.rows(1,5000,(i,row)->{assertEquals(i.longValue(),rows.size()+1L);assertEquals(Math.rint(row[0]),row[0],0);assertTrue(row[0]>=0&&row[0]<100);assertTrue(row[1]>=0&&row[1]<1);assertTrue(row[2]>=0&&row[2]<1);rows.add(row);});
        c.execute("NTUPLES/RANDOM 'a/i,b/f,c' 5000 "+q(path("repeat.hipo"))+" 43 12345");List<double[]>repeat=new ArrayList<>();w.ntuples().get("43").rows(1,5000,(i,row)->repeat.add(row));for(int i=0;i<rows.size();i++)assertArrayEquals(rows.get(i),repeat.get(i),0);
        Workspace reopened=new Workspace();reopened.ntuples().open(4,file,false);assertEquals(5000,reopened.ntuples().get("42").size());assertEquals(tuple.types,reopened.ntuples().get("42").types);c.execute("NT/PLOT 42.b");assertEquals(5000,w.histogram("1000000").integral(),1e-6);
        c.execute("NT/HELP");assertTrue(out.getLast().contains("NTUPLE/RANDOM"));
    }
    @Test public void randomCommandDefaultsAndFailuresPreserveExistingData()throws Exception{
        Path file=path("default.hipo");c.execute("NT/RANDOM x 3 "+q(file));assertEquals(List.of("F"),w.ntuples().get("1").types);assertEquals(3,w.ntuples().get("1").size());
        for(String declaration:List.of("a/D","a/","a/I,a/F","a/F,","bad-name/F")){
            Path invalid=path("invalid.hipo");try{c.execute("NT/RANDOM "+Commands.quote(declaration)+" 3 "+q(invalid)+" 2");fail(declaration);}catch(IllegalArgumentException expected){}assertFalse(Files.exists(invalid));assertEquals(1,w.ntuples().tuples().size());
        }
        try{c.execute("NT/RANDOM a 0 "+q(path("zero.hipo"))+" 2");fail();}catch(IllegalArgumentException expected){}assertFalse(Files.exists(path("zero.hipo")));
        byte[]original=Files.readAllBytes(file);try{c.execute("NT/RANDOM a 2 "+q(file)+" 2");fail();}catch(java.io.IOException expected){}assertArrayEquals(original,Files.readAllBytes(file));
        try{c.execute("NT/RANDOM a 2 "+q(path("duplicate.hipo")));fail();}catch(IllegalArgumentException expected){}assertFalse(Files.exists(path("duplicate.hipo")));
        try{Thread.currentThread().interrupt();c.execute("NT/RANDOM a 5000 "+q(path("cancelled.hipo"))+" 2");fail();}catch(java.util.concurrent.CancellationException expected){}finally{Thread.interrupted();}assertFalse(Files.exists(path("cancelled.hipo")));assertEquals(1,w.ntuples().tuples().size());
    }
    @Test public void randomCommandPromptsForMissingColumnsRowsAndFilename()throws Exception{
        Commands interactive=new Commands(w,new Commands.View(){public void plot(DataSet d,boolean same){}public void zones(int x,int y){}public void zone(int n){}public void export(Path file){}public List<String>requestArguments(CommandArguments.Spec spec,List<String>provided){assertEquals("NTUPLE/RANDOM",spec.command());assertEquals(List.of("a/I,b/F"),provided);return List.of("a/I,b/F","10",path("panel.hipo").toString());}},out::add);
        interactive.executeInteractive("NT/RANDOM 'a/I,b/F'");assertEquals(10,w.ntuples().get("1").size());assertTrue(Files.exists(path("panel.hipo")));
    }

}
