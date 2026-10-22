package org.jlab.paw;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import j4np.hipo5.data.*;
import j4np.hipo5.io.*;

/** PAW logical ntuple IDs over native HIPO banks, with streamed file-backed rows. */
public final class NtupleStore {
    public record Source(Path file, Schema schema) {
        @Override public boolean equals(Object value){return value instanceof Source other&&file.equals(other.file)&&schema.getName().equals(other.schema.getName())&&schema.getGroup()==other.schema.getGroup()&&schema.getItem()==other.schema.getItem()&&schema.getEntryList().equals(other.schema.getEntryList())&&java.util.stream.IntStream.range(0,schema.getElements()).allMatch(i->schema.getType(i)==other.schema.getType(i));}
        @Override public int hashCode(){return Objects.hash(file,schema.getName(),schema.getGroup(),schema.getItem(),schema.getEntryList());}
    }
    private static final class EndRows extends RuntimeException { EndRows(){super(null,null,false,false);} }
    public static class Tuple {
        public final String id, title;
        String identity=UUID.randomUUID().toString();
        public final List<String> columns, types;
        final List<Source> sources = new ArrayList<>();
        final List<double[]> added = new ArrayList<>();
        Tuple(String id, String title, List<String> columns, List<String> types) {
            this.id=id; this.title=title; this.columns=List.copyOf(columns); this.types=List.copyOf(types);
        }
        public void validateRow(double[]values){
            if(values.length!=columns.size())throw new IllegalArgumentException("Column count differs");
            for(int i=0;i<values.length;i++)validate(values[i],types.get(i));
        }
        public void fill(double... values) {
            if(values.length!=columns.size()) throw new IllegalArgumentException("Expected "+columns.size()+" ntuple values");
            for(int i=0;i<values.length;i++) validate(values[i],types.get(i));
            double[] row=values.clone();for(int i=0;i<row.length;i++)if(types.get(i).equals("F"))row[i]=(float)row[i];added.add(row);
        }
        public long rows(long first, long count, BiConsumer<Long,double[]> consumer) {
            if(first<1||count<1) throw new IllegalArgumentException("Ntuple first row and count must be positive");
            long[] index={0}, visited={0};
            try {
            for(Source source:sources) {
                readSource(source,row->{
                    long n=++index[0]; if(n>=first && visited[0]<count){consumer.accept(n,row.clone());visited[0]++;if(visited[0]>=count)throw new EndRows();}
                });
            }
            for(double[] row:added) {
                if(Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                long n=++index[0]; if(n>=first && visited[0]<count){consumer.accept(n,row.clone());visited[0]++;}
            }
            }catch(EndRows done){}
            return visited[0];
        }
        public long size(){long[] n={0};rows(1,Long.MAX_VALUE,(i,r)->n[0]++);return n[0];}
        public Schema schema(int group) {
            Schema.SchemaBuilder b=new Schema.SchemaBuilder("PAW::Ntuple::"+id.substring(2).replace("/","_"),group,1);
            for(int i=0;i<columns.size();i++)b.addEntry(columns.get(i),types.get(i),columns.get(i));
            return b.build();
        }
    }
    private static final Map<Source,List<double[]>> cache=new LinkedHashMap<>(16,.75f,true);
    private static long cacheBytes=0, cacheBudget=10L*1024*1024;
    private static final Map<Source,Long> cacheStamps=new HashMap<>();
    public synchronized void cacheLimit(int mb){if(mb<0||mb>128)throw new IllegalArgumentException("VMEM 0..128");cacheBudget=mb*1024L*1024;clearCache();}
    private static void clearCache(){cache.clear();cacheStamps.clear();cacheBytes=0;}
    public String cacheStatus(){return "VMEM: "+cacheBudget/(1024*1024)+" MB limit; "+cacheBytes+" bytes used, "+(cacheBudget-cacheBytes)+" bytes free";}
    public List<String> cachedBanks(){return cache.keySet().stream().map(s->s.file+" "+s.schema.getName()+" "+s.schema.getEntryList()).toList();}
    private static synchronized void readSource(Source source,java.util.function.Consumer<double[]>consumer){
        long stamp;try{stamp=Files.getLastModifiedTime(source.file).toMillis()^Files.size(source.file);}catch(IOException e){throw new UncheckedIOException(e);}
        if(cache.containsKey(source)&&!Objects.equals(cacheStamps.get(source),stamp)){List<double[]>removed=cache.remove(source);cacheBytes-=removed.size()*(24L+8*source.schema.getElements());cacheStamps.remove(source);}
        List<double[]> hit=cache.get(source);if(hit!=null){for(double[]row:hit){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();consumer.accept(row);}return;}
        List<double[]>collected=new ArrayList<>();long[]size={0};boolean[]retain={cacheBudget>0};
        Workspace.rows(source.file,source.schema,0,Long.MAX_VALUE,row->{consumer.accept(row);if(retain[0]){size[0]+=24L+row.length*8L;if(size[0]>cacheBudget){collected.clear();retain[0]=false;}else collected.add(row);}});
        if(retain[0]){while(cacheBytes+size[0]>cacheBudget&&!cache.isEmpty()){Source oldest=cache.keySet().iterator().next();cacheBytes-=cache.remove(oldest).size()*(24L+8*oldest.schema.getElements());cacheStamps.remove(oldest);}cache.put(source,collected);cacheStamps.put(source,stamp);cacheBytes+=size[0];}
    }
    public void reload(Path file)throws IOException{clearCache();for(String root:units.keySet())if(units.get(root).equals(file.toAbsolutePath())){List<Tuple>loaded=read(file,root);tuples.keySet().removeIf(k->k.startsWith(root+"/"));loaded.forEach(t->tuples.put(t.id,t));}}
    public void install(Tuple tuple,int offset){String id=tuple.id;if(offset!=0)id="//PAWC/"+(Integer.parseInt(leaf(id))+offset);Tuple copy=new Tuple(id,tuple.title,tuple.columns,tuple.types);copy.identity=tuple.identity;copy.sources.addAll(tuple.sources);copy.added.addAll(tuple.added);tuples.put(id,copy);}
    void replace(Collection<Tuple> values){tuples.clear();values.forEach(t->tuples.put(t.id,t));clearCache();directory="//PAWC";}
    private final Map<String,Tuple> tuples=new LinkedHashMap<>();
    private final Map<String,Path> units=new LinkedHashMap<>();
    private final Map<String,List<String>> chains=new LinkedHashMap<>();
    private String directory="//PAWC";
    public Map<String,Tuple> tuples(){return Collections.unmodifiableMap(tuples);}
    public Map<String,List<String>> chains(){return Collections.unmodifiableMap(chains);}
    public String directory(){return directory;}
    public String path(String id){return id.startsWith("//")?id:directory+"/"+id;}
    public static String leaf(String id){return id.substring(id.lastIndexOf('/')+1);}
    public void cd(String path){directory=path.replaceAll("/+$","");}
    public Tuple get(String id){
        String p=path(id);Tuple t=tuples.get(p);if(t!=null)return t;
        String root=p.substring(2,p.indexOf('/',2));
        if(chains.containsKey(root)) return chained(root,leaf(p),new LinkedHashSet<>());
        throw new IllegalArgumentException("Ntuple not found: "+id);
    }
    public Tuple create(String id,String title,List<String> declarations){
        String p=path(id);if(tuples.containsKey(p))throw new IllegalArgumentException("Ntuple already exists: "+id);
        List<String> names=new ArrayList<>(),types=new ArrayList<>();
        for(String declaration:declarations){String[] d=declaration.split(":",2);String name=d[0];String type=d.length==1?"F":d[1].toUpperCase(Locale.ROOT);
            if(!name.matches("[A-Za-z_][A-Za-z0-9_]*")||!Set.of("B","S","I","L","F","D").contains(type))throw new IllegalArgumentException("Column declaration must be name[:B/S/I/L/F/D]");
            names.add(name);types.add(type);
        }
        if(names.isEmpty()||names.size()>512||new HashSet<>(names).size()!=names.size())throw new IllegalArgumentException("Use 1..512 distinct columns");
        Tuple t=new Tuple(p,title,names,types);tuples.put(p,t);return t;
    }
    /** Generate directly into HIPO blocks so row count does not determine memory usage. */
    public Tuple random(Path file,String id,String declarations,long rowCount,long seed)throws IOException{
        if(rowCount<1)throw new IllegalArgumentException("Random Ntuple row count must be positive");
        if(!id.matches("[1-9][0-9]*"))throw new IllegalArgumentException("Random Ntuple ID must be a positive integer");
        String fullId=path(id);
        if(tuples.containsKey(fullId))throw new IllegalArgumentException("Ntuple already exists: "+id);
        if(Files.exists(file))throw new IOException("Output file already exists: "+file);
        List<String>columns=new ArrayList<>();Set<String>names=new HashSet<>();
        for(String declaration:declarations.split(",",-1)){
            String[]parts=declaration.strip().split("/",-1);String name=parts[0].strip(),type=parts.length==1?"F":parts[1].strip().toUpperCase(Locale.ROOT);
            if(parts.length>2||!Set.of("I","F").contains(type))throw new IllegalArgumentException("Random columns use name/I or name/F; missing type defaults to F");
            if(!names.add(name.toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate column: "+name);
            columns.add(name+":"+type);
        }
        // Reuse schema validation without modifying the live registry before the file succeeds.
        Tuple definition=new NtupleStore().create(fullId,"Random Ntuple",columns);
        Tuple generator=new Tuple(fullId,definition.title,definition.columns,definition.types){
            @Override public long size(){return rowCount;}
            @Override public long rows(long first,long count,BiConsumer<Long,double[]>consumer){
                if(first<1||count<1)throw new IllegalArgumentException("Ntuple first row and count must be positive");
                Random random=new Random(seed);long visited=0;
                for(long index=0;index<rowCount&&visited<count;index++){
                    if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Random Ntuple generation cancelled");
                    double[]row=new double[types.size()];
                    for(int column=0;column<row.length;column++)row[column]=types.get(column).equals("I")?random.nextInt(100):random.nextFloat();
                    if(index>=first-1){consumer.accept(index+1,row);visited++;}
                }
                return visited;
            }
        };
        write(file,List.of(generator));
        Tuple loaded=read(file,null).getFirst();tuples.put(loaded.id,loaded);return loaded;
    }
    public Tuple duplicate(String source,String dest,String title){Tuple original=get(source);Tuple copy=create(dest,title.isBlank()?original.title:title,java.util.stream.IntStream.range(0,original.columns.size()).mapToObj(i->original.columns.get(i)+":"+original.types.get(i)).toList());return copy;}
    public void delete(String id){if(id.equals("0")||id.equals("*"))tuples.keySet().removeIf(p->p.startsWith(directory+"/"));else if(tuples.remove(path(id))==null)throw new IllegalArgumentException("Ntuple not found: "+id);}
    public void open(int unit,Path file,boolean create) throws IOException {
        String root="//LUN"+unit;if(unit<1)throw new IllegalArgumentException("Logical unit must be positive");
        if(create){if(Files.exists(file))throw new IOException("New HIPO file already exists: "+file);units.put(root,file.toAbsolutePath());directory=root;return;}
        List<Tuple> loaded=read(file,root);tuples.keySet().removeIf(p->p.startsWith(root+"/"));loaded.forEach(t->tuples.put(t.id,t));units.put(root,file.toAbsolutePath());directory=root;
    }
    public void importFile(Path file)throws IOException{List<Tuple> loaded=read(file,"//PAWC");loaded.forEach(t->tuples.put(t.id,t));}
    public Path unitFile(){Path p=units.get(directory);if(p==null)throw new IllegalArgumentException("No HIPO file is open for "+directory);return p;}
    public void close(int unit){String root="//LUN"+unit;units.remove(root);if(directory.equals(root))directory="//PAWC";}
    public static List<Tuple> read(Path file,String root)throws IOException{
        List<Schema> schemas=Workspace.schemas(file);Properties metadata=new Properties();
        HipoReader reader=new HipoReader();try{reader.setDebugMode(0);reader.open(file.toString());if(reader.hasNext()){Event header=new Event();reader.nextEvent(header);if(header.scan(32101,2)>=0)metadata.load(new StringReader(header.read(32101,2).getString()));}}finally{reader.close();}
        List<Tuple> loaded=new ArrayList<>();Set<String>ids=new HashSet<>();int ordinal=1;
        for(Schema schema:schemas){if(schema.getName().equals("PAW::Mask"))continue;
            List<String> types=new ArrayList<>();for(String col:schema.getEntryList()){String type=DataType.getTypeById(schema.getType(col)).getLetter();if(!Set.of("B","S","I","L","F","D").contains(type))throw new IOException("Unsupported nonnumeric HIPO bank column: "+col);types.add(type);}
            String key=metadata.getProperty(schema.getName()+".id",schema.getName().startsWith("PAW::Ntuple::")?schema.getName().substring(13).replaceFirst("^.*_",""):Integer.toString(ordinal));
            String relative=key.replaceFirst("^//[^/]+/","");String targetId=root==null?(key.startsWith("//")?key:"//PAWC/"+relative):root+"/"+relative;Tuple t=new Tuple(targetId,metadata.getProperty(schema.getName()+".title",schema.getName()),schema.getEntryList(),types);
            t.identity=metadata.getProperty(schema.getName()+".uuid",UUID.nameUUIDFromBytes((file.toAbsolutePath()+"|"+schema.getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());if(!ids.add(t.id))throw new IOException("Duplicate Ntuple ID in HIPO dictionary: "+t.id);t.sources.add(new Source(file.toAbsolutePath(),schema));loaded.add(t);ordinal++;
        }
        if(loaded.isEmpty())throw new IOException("No ntuple banks in HIPO file: "+file);return loaded;
    }
    public void write(Path file,Collection<Tuple> values)throws IOException{write(file,values,Map.of());}
    public void write(Path file,Collection<Tuple> values,Map<String,twig.data.DataSet> histograms)throws IOException{
        if(values.isEmpty()&&histograms.isEmpty())throw new IllegalArgumentException("No objects to write");
        Path target=file.toAbsolutePath();Path temp=Files.createTempFile(target.getParent(),".paw-ntuples-",".hipo");
        HipoWriter writer=new HipoWriter();try{
            Map<Tuple,Schema> schemas=new LinkedHashMap<>();Properties metadata=new Properties();int group=32200;
            for(Tuple t:values){if(group>=65500)throw new IllegalArgumentException("HIPO schema group range exhausted");Schema s=t.schema(group++);if(schemas.values().stream().anyMatch(existing->existing.getName().equals(s.getName())))throw new IllegalArgumentException("Duplicate ntuple IDs in export");schemas.put(t,s);writer.getSchemaFactory().addSchema(s);metadata.setProperty(s.getName()+".id",t.id);metadata.setProperty(s.getName()+".title",t.title);metadata.setProperty(s.getName()+".uuid",t.identity);}
            if(!writer.open(temp.toString()))throw new IOException("Cannot open HIPO output");
            try{
                StringWriter data=new StringWriter();metadata.store(data,"PAW Ntuple metadata");Event header=new Event();header.write(new Node(32000,1,"PAW-JAVA-WORKSPACE:1"));header.write(new Node(32101,1,"PAW-NTUPLES:1"));header.write(new Node(32101,2,data.toString()));writer.addEvent(header);
                for(var item:histograms.entrySet()){String json=twig.data.DataSetSerializer.toJson(item.getValue());Event e=new Event(Math.max(1024*1024,json.length()*4+1024));e.write(new Node(32000,2,item.getKey()));e.write(new Node(32000,3,json));writer.addEvent(e);}
                for(var entry:schemas.entrySet()){
                    Tuple t=entry.getKey();Schema s=entry.getValue();List<double[]> block=new ArrayList<>(256);
                    t.rows(1,Long.MAX_VALUE,(i,row)->{block.add(row);if(block.size()==256){writeBlock(writer,s,block,t.types);block.clear();}});
                    if(!block.isEmpty())writeBlock(writer,s,block,t.types);
                }
            }finally{writer.close();}
            try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
                    for(Tuple t:values)if(t.sources.stream().anyMatch(source->source.file.equals(target))){Schema schema=schemasForFile(target,t);t.sources.clear();t.sources.add(new Source(target,schema));t.added.clear();}
            clearCache();
        }finally{Files.deleteIfExists(temp);}
    }
    private static Schema schemasForFile(Path target,Tuple tuple)throws IOException{return Workspace.schemas(target).stream().filter(s->s.getName().equals("PAW::Ntuple::"+tuple.id.substring(2).replace("/","_"))).findFirst().orElseThrow(()->new IOException("Written bank missing"));}
    private static void writeBlock(HipoWriter writer,Schema schema,List<double[]> rows,List<String> types){
        Bank bank=new Bank(schema,rows.size());for(int r=0;r<rows.size();r++)for(int c=0;c<types.size();c++){double v=rows.get(r)[c];String name=schema.getElementName(c);validate(v,types.get(c));switch(types.get(c)){case "B"->bank.putByte(name,r,(byte)v);case "S"->bank.putShort(name,r,(short)v);case "I"->bank.putInt(name,r,(int)v);case "L"->bank.putLong(name,r,(long)v);case "F"->bank.putFloat(name,r,(float)v);default->bank.putDouble(name,r,v);}}
        Event event=new Event();event.write(bank);writer.addEvent(event);
    }
    private static void validate(double value,String type){if(!Double.isFinite(value))throw new IllegalArgumentException("Ntuple values must be finite");if(type.equals("F")&&Math.abs(value)>Float.MAX_VALUE)throw new IllegalArgumentException("Float overflow");if(Set.of("B","S","I","L").contains(type)){double max=switch(type){case "B"->127;case "S"->32767;case "I"->Integer.MAX_VALUE;default->9_007_199_254_740_991d;};double min=type.equals("L")?-max:-max-1;if(value!=Math.rint(value)||value<min||value>max)throw new IllegalArgumentException("Integer overflow or unsafe integer precision for "+type);}}
    public void chain(String name,List<String> members){if(!name.matches("[A-Za-z][A-Za-z0-9_]*")||members.isEmpty())throw new IllegalArgumentException("CHAIN name members...");Map<String,List<String>> prior=new LinkedHashMap<>(chains);chains.put(name,List.copyOf(members));try{expand(name,new LinkedHashSet<>());}catch(RuntimeException e){chains.clear();chains.putAll(prior);throw e;}}
    public void removeChain(String name){chains.remove(name);}
    public List<Path> expand(String name,Set<String> visiting){if(!visiting.add(name))throw new IllegalArgumentException("Cyclic chain: "+name);List<Path> files=new ArrayList<>();for(String member:chains.getOrDefault(name,List.of())){if(chains.containsKey(member))files.addAll(expand(member,visiting));else{Path file=member.startsWith("//LUN")?units.get(member):Path.of(member);if(file==null)throw new IllegalArgumentException("Logical unit not open: "+member);if(!Files.isRegularFile(file))throw new IllegalArgumentException("Chain file not found: "+file);files.add(file);}}visiting.remove(name);return files;}
    private Tuple chained(String name,String id,Set<String> visiting){Tuple result=null;StringBuilder identity=new StringBuilder();for(Path file:expand(name,visiting))try{Tuple t=read(file,"//"+name).stream().filter(x->leaf(x.id).equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Ntuple "+id+" missing from "+file));if(result==null)result=new Tuple("//"+name+"/"+id,t.title,t.columns,t.types);if(!result.columns.equals(t.columns)||!result.types.equals(t.types))throw new IllegalArgumentException("Chain schemas differ");identity.append(t.identity).append(";");result.sources.addAll(t.sources);}catch(IOException e){throw new IllegalArgumentException(e);}Objects.requireNonNull(result,"Empty chain");result.identity=UUID.nameUUIDFromBytes(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();return result;}
}
