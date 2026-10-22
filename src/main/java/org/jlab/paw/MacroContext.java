package org.jlab.paw;

import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import twig.data.*;
import twig.math.*;
import twig.widgets.*;

/** Java replacements for tutorial KUIP, SIGMA and COMIS calls. Twig remains the data/plot backend. */
public final class MacroContext {
    public final Workspace workspace;
    public final Path directory;
    public final List<String> arguments;
    public final Map<String,Object> globals;
    public final Random random = new Random(1994);
    private final Commands commands;
    private final Commands.View view;
    private final Consumer<String> output;
    private final int depth;
    private Map<String,Double> ntupleRow = Map.of();
    public long eventNumber;
    private double rowWeight = 1;
    private double[] callbackValues=new double[0];
    void callbackArguments(double[] values){callbackValues=values;}
    public double argumentValue(int index){return callbackValues[index];}
    private double[] rowResult=new double[0];
    public void result(double... values) { rowResult=values.clone(); }
    double resultValue(int index) { if(index<1||index>rowResult.length)throw new IllegalArgumentException("Callback result index out of range: "+index); return rowResult[index-1]; }
    void setNtupleRow(NtupleStore.Tuple tuple, long index, double[] values) {
        Map<String,Double> row = new LinkedHashMap<>();
        for (int i=0;i<values.length;i++) row.put(tuple.columns.get(i), values[i]);
        ntupleRow=Collections.unmodifiableMap(row); eventNumber=index; rowWeight=1; rowResult=new double[0];
    }
    public Map<String,Double> row() { return ntupleRow; }
    public double value(String name) { return ntupleRow.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown ntuple column: "+name)); }
    public void weight(double weight) { if(!Double.isFinite(weight))throw new IllegalArgumentException("Finite callback weight required"); rowWeight=weight; }
    double selectionWeight() { return rowWeight; }
    public void fillNtuple(String id, double... values) { check(); workspace.ntuples().get(id).fill(values); }
    public NtupleStore.Tuple createNtuple(String id, String title, String... columns) { return workspace.ntuples().create(id,title,List.of(columns)); }
    MacroContext(Workspace w, Commands c, Commands.View v, Consumer<String> out, Path directory, List<String> arguments, Map<String,Object> globals, int depth) {
        workspace=w; commands=c; view=v; output=out; this.directory=directory; this.arguments=List.copyOf(arguments); this.globals=globals; this.depth=depth;
    }
    public void check() { if(Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Macro cancelled"); }
    public void command(String text) throws Exception { check(); commands.executeNested(text,depth,directory); }
    public void exec(String file, String... args) throws Exception { command("MACRO/EXEC " + Commands.quote(file) + Arrays.stream(args).map(a->" "+Commands.quote(a)).reduce("",String::concat)); }
    public void print(Object text) { output.accept(String.valueOf(text)); }
    public int integerArgument(int index, int fallback) { return index<arguments.size()?Integer.parseInt(arguments.get(index)):fallback; }
    public Path file(String name) { return directory.resolve(name).normalize(); }
    public Path output(String name) throws java.io.IOException { Path folder=directory.resolve("output"); Files.createDirectories(folder); return folder.resolve(name); }
    public void cd(String name) { workspace.cd("tutorial/"+name); workspace.ntuples().cd(workspace.directory()); }
    public void zones(int columns,int rows) { view.zones(columns,rows); }
    public void zone(int number) { view.zone(number-1); }
    public void plot(DataSet ds) { plot(ds,"",false); }
    public void plot(DataSet ds,String options,boolean same) { check(); view.draw(ds,options,same); }
    public void widget(Widget widget,boolean clear) { view.widget(widget,clear); }
    public void configure(Consumer<twig.graphics.TGRegion> action) { view.configure(action); }
    public void panel(String title,Map<String,String> buttons) { view.panel(title,buttons); }
    public void export(Path path) { view.export(path); }
    public java.awt.image.BufferedImage capture() { return view.capture(); }
    public void printPictures(List<java.awt.image.BufferedImage> pictures,Path file,boolean landscape) throws java.io.IOException { GraphicsExport.pictures(pictures,file,landscape); }
    public void registerNtuple(Path path) throws java.io.IOException { workspace.ntuples().importFile(path); view.ntuple(path,Workspace.schemas(path)); }
    public void pause(int milliseconds) throws InterruptedException { check(); if(!java.awt.GraphicsEnvironment.isHeadless()&&!javax.swing.SwingUtilities.isEventDispatchThread())Thread.sleep(Math.min(1000,Math.max(0,milliseconds))); }

    public H1F h1(String id,String title,int bins,double low,double high) { H1F h=Workspace.book1(id,title,bins,low,high);workspace.put(id,h);return h; }
    public H2F h2(String id,int nx,double xmin,double xmax,int ny,double ymin,double ymax) { Workspace.checkAxis(nx,xmin,xmax); Workspace.checkAxis(ny,ymin,ymax); if((long)nx*ny>4_000_000)throw new IllegalArgumentException("Too many bins");H2F h=new H2F(id,nx,xmin,xmax,ny,ymin,ymax);workspace.put(id,h);return h; }
    public <T extends DataSet> T keep(String id,T ds) {workspace.put(id,ds);return ds;}
    public double[] array(int n,double low,double high) { if(n<1||n>1_000_000)throw new IllegalArgumentException("Array length 1..1000000");return java.util.stream.IntStream.range(0,n).mapToDouble(i->n==1?low:low+(high-low)*i/(n-1)).toArray(); }
    public double[] map(double[] x,DoubleUnaryOperator f) {return Arrays.stream(x).map(f).toArray();}
    public void vector(String name,double[] x) throws Exception { StringBuilder text=new StringBuilder("V/CREATE "+name+"("+x.length+") R");for(double value:x)text.append(" ").append(value);command(text.toString()); }
    public GraphErrors graph(String id,double[] x,double[] y) { if(x.length!=y.length)throw new IllegalArgumentException("Graph lengths differ");GraphErrors g=new GraphErrors();for(int i=0;i<x.length;i++)g.addPoint(x[i],y[i],0,0);return keep(id,g); }
    public H1F function1(String id,int bins,double low,double high,DoubleUnaryOperator f) {H1F h=h1(id,id,bins,low,high);for(int i=0;i<bins;i++){double v=f.applyAsDouble(h.getAxis().getBinCenter(i));if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite function");h.setBinContent(i,v);h.setBinError(i,Math.sqrt(Math.abs(v)));}return h;}
    public H2F function2(String id,int nx,double xmin,double xmax,int ny,double ymin,double ymax,DoubleBinaryOperator f) {H2F h=h2(id,nx,xmin,xmax,ny,ymin,ymax);for(int i=0;i<nx;i++){check();for(int j=0;j<ny;j++){double v=f.applyAsDouble(h.getAxisX().getBinCenter(i),h.getAxisY().getBinCenter(j));if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite function");h.setBinContent(i,j,v);}}return h;}
    public F1D fit(DataSet data,String expression,double low,double high,double... initial) {F1D f=new F1D("fit",expression,low,high);f.setParameters(initial);DataFitter.fit(f,data,"");for(int i=0;i<f.getNPars();i++)if(!Double.isFinite(f.getParameter(i)))throw new IllegalStateException("Nonfinite fit");print("Fit chi-square="+f.getChiSquare());return f;}
    public H1F crop(H1F source,int first,int last,String id) {if(first<1||last<first||last>source.getAxis().getNBins())throw new IllegalArgumentException("Invalid 1-based bins");double[] edges=Arrays.copyOfRange(source.getAxis().getLimits(),first-1,last+1);H1F h=new H1F(id,edges);for(int i=first-1;i<last;i++){h.setBinContent(i-first+1,source.getBinContent(i));h.setBinError(i-first+1,source.getBinError(i));}return keep(id,h);}
    public H2F crop(H2F s,int x1,int x2,int y1,int y2,String id) {if(x1<1||y1<1||x2<x1||y2<y1||x2>s.getAxisX().getNBins()||y2>s.getAxisY().getNBins())throw new IllegalArgumentException("Invalid 1-based bins");H2F h=h2(id,x2-x1+1,s.getAxisX().getBinLow(x1-1),s.getAxisX().getLimits()[x2],y2-y1+1,s.getAxisY().getBinLow(y1-1),s.getAxisY().getLimits()[y2]);for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)h.setBinContent(x-x1,y-y1,s.getBinContent(x-1,y-1));return h;}
    /** Exact tutorial double-peak function, replacing HTFUN1.F. */
    public double htfun(double x) {return Math.exp(-.5*Math.pow((x-.3)/.07,2))+.5*Math.exp(-.5*Math.pow((x-.7)/.12,2));}
    public H1F peaks(String id,int samples) {H1F h=h1(id,"Test 1-dim Histo",100,0,1);for(int i=0;i<samples;){check();double x=random.nextDouble();if(random.nextDouble()*1.5<htfun(x)){h.fill(x);i++;}}return h;}
    public H2F peaks2(String id) {return function2(id,25,0,1,25,0,1,(x,y)->100*htfun(x)*htfun(y));}
    public TutorialTable staff() {return TutorialTable.staff();}
    public void note(String text) { PaveText note=new PaveText(text,.04,.95);note.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,12));widget(note.setNDF(true).setDrawBox(false),false); }
}
