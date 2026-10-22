package org.jlab.paw;

import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import j4np.hipo5.data.*;
import j4np.hipo5.io.*;
import twig.data.*;

/** Small in-memory tutorial ntuples. HIPO bank rows replace both legacy RW and CW storage. */
public final class TutorialTable {
    public final List<String> columns;
    private final List<double[]> rows = new ArrayList<>();
    public TutorialTable(String... columns) {this.columns=List.of(columns);if(new HashSet<>(this.columns).size()!=columns.length)throw new IllegalArgumentException("Duplicate columns");}
    public int size() {return rows.size();}
    public void add(double... row) {if(row.length!=columns.size())throw new IllegalArgumentException("Column count");for(double x:row)if(!Double.isFinite(x))throw new IllegalArgumentException("Finite values required");rows.add(row.clone());}
    public List<double[]> rows() {return rows.stream().map(double[]::clone).toList();}
    public double[] column(String expression) {var f=Expression.compile(expression,columns);return rows.stream().mapToDouble(f).toArray();}
    public TutorialTable select(String cut) {var f=Expression.compile(cut,columns);return select(row->Expression.truth(f.applyAsDouble(row)));}
    public TutorialTable select(Predicate<double[]> cut) {TutorialTable t=new TutorialTable(columns.toArray(String[]::new));for(double[] row:rows)if(cut.test(row.clone()))t.add(row);return t;}
    public BitSet mask(String cut) {var f=Expression.compile(cut,columns);BitSet bits=new BitSet();for(int i=0;i<size();i++)if(Expression.truth(f.applyAsDouble(rows.get(i))))bits.set(i);return bits;}
    public TutorialTable masked(BitSet bits) {TutorialTable t=new TutorialTable(columns.toArray(String[]::new));for(int i=bits.nextSetBit(0);i>=0;i=bits.nextSetBit(i+1)){if(i>=size())throw new IllegalArgumentException("Mask exceeds ntuple");t.add(rows.get(i));}return t;}
    public void append(TutorialTable other) {if(!columns.equals(other.columns))throw new IllegalArgumentException("Ntuple schemas differ");other.rows.forEach(this::add);}
    public H1F project(String id,String expression,int bins,double min,double max) {H1F h=Workspace.book1(id,expression,bins,min,max);fill(h,expression);return h;}
    public H1F auto(String id,String expression) {double[] v=column(expression);double min=Arrays.stream(v).min().orElse(0),max=Arrays.stream(v).max().orElse(1);double pad=max==min?1:(max-min)*.01;return project(id,expression,Math.max(10,(int)Math.sqrt(size())),min-pad,max+pad);}
    public void fill(H1F h,String expression) {for(double x:column(expression))if(Double.isFinite(x))h.fill(x);}
    public H2F project2(String id,String x,String y,int nx,double xmin,double xmax,int ny,double ymin,double ymax) {Workspace.checkAxis(nx,xmin,xmax);Workspace.checkAxis(ny,ymin,ymax);H2F h=new H2F(id,nx,xmin,xmax,ny,ymin,ymax);double[] a=column(x),b=column(y);for(int i=0;i<a.length;i++)if(Double.isFinite(a[i])&&Double.isFinite(b[i]))h.fill(a[i],b[i]);return h;}
    /** Tutorial profile convention: mean Y and RMS spread in each X bin. */
    public H1F profile(String id,String x,String y,int bins,double min,double max) {H1F h=Workspace.book1(id,"Mean "+y+" vs "+x,bins,min,max);double[] sum=new double[bins],sq=new double[bins];int[] n=new int[bins];double[] a=column(x),b=column(y);for(int i=0;i<a.length;i++){int k=h.getAxis().getBin(a[i]);if(k>=0&&k<bins){sum[k]+=b[i];sq[k]+=b[i]*b[i];n[k]++;}}for(int i=0;i<bins;i++)if(n[i]>0){double mean=sum[i]/n[i];h.setBinContent(i,mean);h.setBinError(i,Math.sqrt(Math.max(0,sq[i]/n[i]-mean*mean)));}return h;}
    public void scan(Consumer<String> out,int count) {out.accept(String.join("\t",columns));rows.stream().limit(count).forEach(row->out.accept(Arrays.toString(row)));}
    public void write(Path path) throws java.io.IOException { writeTyped(path,Map.of()); }
    public void writeTyped(Path path,Map<String,String> types) throws java.io.IOException {
        Path target=path.toAbsolutePath(),temp=Files.createTempFile(target.getParent(),".paw-ntuple-",".hipo");
        Schema.SchemaBuilder builder=new Schema.SchemaBuilder("PAW::tuple",32100,1);for(String c:columns){String type=types.getOrDefault(c,"D");if(!Set.of("B","S","I","F","D").contains(type))throw new IllegalArgumentException("Type B/S/I/F/D");builder.addEntry(c,type,c);}Schema schema=builder.build();HipoWriter writer=new HipoWriter();writer.getSchemaFactory().addSchema(schema);
        try {if(!writer.open(temp.toString()))throw new java.io.IOException("Cannot open HIPO");try {for(int first=0;first<size();first+=256){int n=Math.min(256,size()-first);Bank bank=new Bank(schema,n);for(int r=0;r<n;r++)for(int c=0;c<columns.size();c++){String col=columns.get(c),type=types.getOrDefault(col,"D");double v=rows.get(first+r)[c];switch(type){case "B"->{if(v!=Math.rint(v)||v<-128||v>127)throw new IllegalArgumentException("Byte overflow");bank.putByte(col,r,(byte)v);}case "S"->{if(v!=Math.rint(v)||v<-32768||v>32767)throw new IllegalArgumentException("Short overflow");bank.putShort(col,r,(short)v);}case "I"->{if(v!=Math.rint(v)||v<Integer.MIN_VALUE||v>Integer.MAX_VALUE)throw new IllegalArgumentException("Integer overflow");bank.putInt(col,r,(int)v);}case "F"->{if(Math.abs(v)>Float.MAX_VALUE)throw new IllegalArgumentException("Float overflow");bank.putFloat(col,r,(float)v);}default->bank.putDouble(col,r,v);}}Event event=new Event();event.write(bank);writer.addEvent(event);}}finally{writer.close();}Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    public static TutorialTable read(Path file) throws java.io.IOException {List<Schema> schemas=Workspace.schemas(file);Schema schema=schemas.stream().filter(s->s.getName().equals("PAW::tuple")).findFirst().orElseThrow(()->new java.io.IOException("No PAW::tuple bank"));TutorialTable t=new TutorialTable(schema.getEntryList().toArray(String[]::new));Workspace.rows(file,schema,0,Long.MAX_VALUE,t::add);return t;}
    public static TutorialTable chain(List<Path> files) throws java.io.IOException {TutorialTable result=null;for(Path path:files){TutorialTable t=read(path);if(result==null)result=t;else result.append(t);}if(result==null)throw new IllegalArgumentException("Empty chain");return result;}
    public static TutorialTable ascii(Path file,String... columns) throws java.io.IOException {TutorialTable t=new TutorialTable(columns);int line=0;for(String text:Files.readAllLines(file)){line++;text=text.strip();if(text.isEmpty()||text.startsWith("#"))continue;try {t.add(Arrays.stream(text.split("\\s+")).mapToDouble(Double::parseDouble).toArray());}catch(RuntimeException ex){throw new java.io.IOException(file+":"+line+": "+ex.getMessage(),ex);}}return t;}
    /** Synthetic replacement for the tutorial's unavailable APTUPLE.DAT; never represents real CERN staff. */
    public static TutorialTable staff() {TutorialTable t=new TutorialTable("Category","Division","Flag","Age","Service","Children","Grade","Step","Nation","Hrweek","Cost");Random r=new Random(3354);for(int i=0;i<3354;i++){int age=20+r.nextInt(45),service=r.nextInt(Math.min(35,age-18)),grade=3+r.nextInt(12);t.add(1+r.nextInt(4),1+r.nextInt(13),r.nextInt(4),age,service,r.nextInt(6),grade,1+r.nextInt(15),1+r.nextInt(12),35+r.nextInt(11),3000+grade*600+r.nextGaussian()*800);}return t;}
    public static boolean inside(double x,double y,double[] px,double[] py) {if(px.length!=py.length||px.length<3)throw new IllegalArgumentException("Polygon needs three points");boolean inside=false;for(int i=0,j=px.length-1;i<px.length;j=i++)if((py[i]>y)!=(py[j]>y)&&x<(px[j]-px[i])*(y-py[i])/(py[j]-py[i])+px[i])inside=!inside;return inside;}
}
