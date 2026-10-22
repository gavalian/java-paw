package org.jlab.paw;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.regex.*;
import twig.data.*;

/** PAW query commands over the shared, native HIPO ntuple registry. */
final class NtupleCommands {
    record Definition(String command,int required,String labels,String defaults,String description) {
        CommandArguments.Spec spec(){return new CommandArguments.Spec(command,List.of(labels.split(";",-1)),List.of(defaults.split(";",-1)),required);}
        CommandCatalog.Entry entry(){return new CommandCatalog.Entry(command.startsWith("NTUPLE/MASK/")?"MASK":(command.contains("/")?command.substring(0,command.indexOf('/')):"HISTOGRAM"),command,labels.replace(';',' '),description);}
    }
    static final List<Definition> DEFINITIONS=List.of(
        def("RANDOM",3,"Columns;Rows;File;ID;Seed","a/I,b/F,c/F;1000;;1; ","Generate a HIPO Ntuple: comma-separated name/I or name/F columns (default F), positive row count and new filename. Optional PAW ID defaults to 1; optional seed makes generation reproducible. Integers are uniform 0..99; floats are uniform [0,1). Rows are streamed and the result is registered in the browser."),
        def("CREATE",6,"ID;Title;NVAR;CHRZPA;NPRIME;VARLIST",";;1; ;1000;","Create a numeric Ntuple with PAW ID. Supply NVAR column names, optionally name:B/S/I/L/F/D; default F. Java macros fill rows with paw.fillNtuple."),
        def("LIST",0,"","","List all registered Ntuple IDs, titles and schemas."),
        def("PRINT",1,"ID","","Print schema, row count and column limits."),
        def("HMERGE",2,"OUTFILE;INFILES",";","Merge compatible Ntuple rows and add matching histograms from HIPO files."),
        def("DUPLICATE",2,"ID1;ID2;NEWBUF;Title;Option",";;-1; ;A","Duplicate the definition into an empty Ntuple; rows are not copied."),
        def("RECOVER",1,"ID","","Rebuild a readable HIPO Ntuple by scanning its rows into an atomic new file."),
        def("SCAN",1,"ID;Selection;NEVENT;IFIRST;Option;VARLIST",";1.;99999999;1; ; ","Scan selected rows; expressions and column ranges are accepted. S spider, S2 segments, A average. Row numbers begin at 1."),
        def("LOOP",2,"ID;Selection;NEVENT;IFIRST",";1.;99999999;1","Run a selection expression or .jmacro callback for each row, optionally writing mask bits."),
        def("GCUT",2,"Cut ID;ID.expression;Selection;NEVENT;IFIRST;NUPD;Option;Histogram ID;WKID",";;1.;99999999;1;100000000; ;1000000;1","Draw a one or two dimensional projection and select a range or polygon with the mouse."),
        def("PROJECT",2,"Histogram ID;ID.expression;Selection;NEVENT;IFIRST",";;1.;99999999;1","Accumulate weighted rows into an existing histogram, without resetting or drawing it. ID.Y%X projects Y versus X."),
        def("READ",2,"ID;File;FORMAT;Option;NEVENT;MATCH",";;*; ;1000000; ","Append ASCII rows; free or fixed Fortran numeric formats, MATCH and comment lines are supported."),
        def("PLOT",1,"ID.expression;Selection;NEVENT;IFIRST;NUPD;Option;Histogram ID",";1.;99999999;1;100000000; ;1000000","Plot weighted projections. S overlays, + accumulates, two expressions use Y%X. C/B/L/P/*/E and profile options control rendering."),
        def("CHAIN",0,"Name;Members",";","Append files, logical units or other chains; -name removes a chain, - removes all, name> prints its tree, -member removes a member, -Ppath rebases file paths."),
        def("CUTS",1,"Cut ID;Option or expression;File;WKID",";P; ;1","Define $1..$99 with an expression; P prints, - deletes, R/W reads/writes, D draws a graphical cut. $0 selects all cuts for management."),
        def("CSELECT",0,"Option;Text size","N;0.28","Enable N selection labels for future plots; R/C/B position labels. Without N, draw the previous selection."),
        def("UWFUNC",2,"ID;File;Option",";; ","Generate a Java .jmacro row callback. paw.value reads columns; paw.weight supplies selection weights; paw.result supplies projection results. P prints, E opens editor, T emits column tags."),
        def("LINTRA",1,"ID;Option;NEVENT;IFIRST;NVARS;VARLIST","; ;99999999;1;20; ","Principal component analysis, descending eigenvalues, optional N standardization and P detailed output. Writes xtoxsi.jmacro and permits id.xtoxsi.jmacro(component)."),
        def("VMEM",0,"Maximum MB","-1","Set a bounded file row cache (0..128 MB). -1 reports allocation, -2 lists cached banks. Default 10 MB."),
        def("DUMP",1,"ID.expression;Selection;NEVENT;IFIRST;File;Value separator;Expression separator",";1.;99999999;1; ;,;%","Write selected expression values to a file or transcript; default expression separator is %."),
        def("FLAGS_QP",0,"Option;Value",";","Inspect or set Java query diagnostic flags: TRACE, TIMING, STRICT (0 or 1)."),
        mask("FILE",1,"File;Option",";R","Open a HIPO mask: R read, N new, U update. Mask name is the file stem."),
        mask("CLOSE",1,"Mask name","","Flush and close a mask."),
        mask("LIST",0,"Mask name","*","List mask files, row counts and bit selection definitions."),
        mask("RESET",1,"Mask name;Bit",";1","Clear a mask bit 1..32 and persist it."),
        new Definition("HISTOGRAM/FILE",2,"Logical unit;File;Record length;Option",";;1024; ","Open a HIPO file under //LUNn; option N creates a new output destination."),
        new Definition("HROUT",0,"ID;ICYCLE;Option","0;0; ","Write the selected Ntuple or all current-directory Ntuples to the current HIPO logical unit."),
        new Definition("HRIN",0,"ID;ICYCLE;Offset","0;999;0","Import selected Ntuples from the current HIPO unit into //PAWC, with an optional numeric ID offset."),
        new Definition("CLOSE",1,"Logical unit","","Close a HIPO logical unit.")
    );
    private static Definition def(String name,int n,String labels,String defaults,String help){return new Definition("NTUPLE/"+name,n,labels,defaults,help);}
    private static Definition mask(String name,int n,String labels,String defaults,String help){return def("MASK/"+name,n,labels,defaults,help);}
    static CommandArguments.Spec spec(String command){return DEFINITIONS.stream().filter(d->d.command.equals(command)).map(Definition::spec).findFirst().orElse(null);}
    static String canonical(String op){
        String p=op.replaceFirst("^(N|NT|NTUP|NTUPLES)/","NTUPLE/").replaceFirst("^MASK/","NTUPLE/MASK/");
        if(Set.of("CUTS","CHAIN","UWFUNC","SCAN","LOOP","PROJECT").contains(p))p="NTUPLE/"+p;
        if(p.startsWith("NTUPLE/")&&!p.endsWith("/HELP")){String prefix=p;List<String> matches=DEFINITIONS.stream().map(Definition::command).filter(c->c.startsWith(prefix)).toList();if(matches.size()==1)p=matches.getFirst();}
        return p;
    }
    static List<String> parameters(Definition d,List<String> raw){
        List<String>result=new ArrayList<>();result.add(raw.getFirst());List<String>labels=List.of(d.labels.split(";",-1));
        Map<String,Integer>indices=new HashMap<>();for(int i=0;i<labels.size();i++)indices.put(labels.get(i).replace(" ","").replace(".expression","").toUpperCase(Locale.ROOT),i+1);
        Map<String,String>aliases=Map.ofEntries(Map.entry("IDN","ID"),Map.entry("UWFUNC","SELECTION"),Map.entry("CHOPT","OPTION"),Map.entry("OPT","OPTION"),Map.entry("IDH","HISTOGRAMID"),Map.entry("FNAME","FILE"),Map.entry("FILENAME","FILE"),Map.entry("CNAME","NAME"),Map.entry("ENTRY","MEMBERS"),Map.entry("VARLIS","VARLIST"),Map.entry("CUTID","CUTID"),Map.entry("NVARS","NVARS"),Map.entry("MNAME","MASKNAME"),Map.entry("IBIT","BIT"),Map.entry("MXSIZE","MAXIMUMMB"),Map.entry("SEP1","VALUESEPARATOR"),Map.entry("SEP2","EXPRESSIONSEPARATOR"));
        int positional=1;for(int i=1;i<raw.size();i++){String token=raw.get(i);int equal=token.indexOf('=');String key=equal>0?token.substring(0,equal).toUpperCase(Locale.ROOT):"";key=aliases.getOrDefault(key,key);Integer index=indices.get(key);if(index==null){while(result.size()<=positional)result.add("!");result.set(positional++,token);}else{while(result.size()<=index)result.add("!");result.set(index,token.substring(equal+1));if(key.equals("VARLIST")||key.equals("MEMBERS")||key.equals("INFILES"))positional=index+1;}}
        return result;
    }
    final Workspace workspace;final NtupleStore store;final Commands commands;final Commands.View view;final Consumer<String> output;
    final Map<Integer,String> cuts=new TreeMap<>();final Map<String,NtupleMask> masks=new LinkedHashMap<>();
    final Map<String,Integer> flags=new LinkedHashMap<>(Map.of("TRACE",0,"TIMING",0,"STRICT",1));
    String lastPlotId="1000000";
    record ProfileState(DataSet histogram,double[][] sums){}
    final Map<String,ProfileState> profiles=new HashMap<>();
    boolean comments;String commentOption="",lastSelection="";double commentSize=.28;
    NtupleCommands(Workspace w,Commands c,Commands.View v,Consumer<String> o){workspace=w;store=w.ntuples();commands=c;view=v;output=o;}
    private static String arg(List<String>a,int i,String fallback){return i<a.size()&&!a.get(i).equals("!")?a.get(i):fallback;}
    private static long integer(List<String>a,int i,long fallback){return Long.parseLong(arg(a,i,""+fallback));}
    private static List<String> words(List<String>a,int from){return a.subList(Math.min(from,a.size()),a.size()).stream().flatMap(s->Arrays.stream(s.strip().split("[ ,]+"))).filter(s->!s.isBlank()&&!s.equals("!")).toList();}
    private static Path file(String s,Path base){Path p=Path.of(s);return (p.isAbsolute()||base==null?p:base.resolve(p)).toAbsolutePath().normalize();}
    boolean execute(String op,List<String>a,int depth,Path base)throws Exception{
        Definition d=DEFINITIONS.stream().filter(x->x.command.equals(op)).findFirst().orElse(null);if(d==null)return false;
        a=parameters(d,a);
        if(a.size()-1<d.required)throw new IllegalArgumentException(d.command+" "+d.labels.replace(';',' '));
        long began=System.nanoTime();if(flags.get("TRACE")==1)output.accept("Query: "+op+" "+a.subList(1,a.size()));
        switch(op){
            case "NTUPLE/RANDOM"->{if(a.size()>6)throw new IllegalArgumentException("NTUPLE/RANDOM columns rows filename [id] [seed]");long seed=arg(a,5," ").isBlank()?new Random().nextLong():Long.parseLong(a.get(5));Path target=file(a.get(3),base);NtupleStore.Tuple generated=store.random(target,arg(a,4,"1"),a.get(1),Long.parseLong(a.get(2)),seed);output.accept("Generated "+generated.id+": "+a.get(2)+" rows, "+generated.columns.size()+" columns in "+target+" (seed "+seed+")");}
            case "NTUPLE/CREATE"->{int n=(int)integer(a,3,1);List<String>cols=words(a,6);if(cols.size()!=n)throw new IllegalArgumentException("NVAR must equal the number of columns");String prior=store.directory(),dest=arg(a,4," ");if(!dest.isBlank())store.cd(dest);try{store.create(a.get(1),a.get(2),cols);}finally{store.cd(prior);}}
            case "NTUPLE/LIST"->store.tuples().forEach((id,t)->output.accept(id+"  "+t.title+"  "+t.columns.size()+" columns"));
            case "NTUPLE/PRINT"->{var t=store.get(a.get(1));double[][]range=limits(t,1,Long.MAX_VALUE);output.accept(t.id+"  "+t.title+"  "+t.size()+" rows");for(int i=0;i<t.columns.size();i++)output.accept(t.columns.get(i)+" "+t.types.get(i)+"  "+range[0][i]+" .. "+range[1][i]);}
            case "NTUPLE/DUPLICATE"->{String option=arg(a,5,"A").toUpperCase(Locale.ROOT);if(!option.matches("[AM]*"))throw new IllegalArgumentException("DUPLICATE option A or M");store.duplicate(a.get(1),a.get(2),arg(a,4," "));}
            case "NTUPLE/LOOP"->{var s=selector(a.get(1),"%",false);try(var q=new Query(s,arg(a,2,"1."),base,depth)){long[]selected={0};s.tuple.rows(integer(a,4,1),integer(a,3,99999999),(i,row)->{if(Expression.truth(q.weight(i,row)))selected[0]++;});output.accept("LOOP: "+selected[0]+" selected rows");}}
            case "NTUPLE/PLOT","NTUPLE/PROJECT"->projection(op,a,base,depth);
            case "NTUPLE/SCAN","NTUPLE/DUMP"->scan(op,a,base,depth);
            case "NTUPLE/READ"->read(a,base);
            case "NTUPLE/CUTS"->cuts(a,base);
            case "NTUPLE/CHAIN"->chain(a,base);
            case "NTUPLE/MASK/FILE","NTUPLE/MASK/CLOSE","NTUPLE/MASK/LIST","NTUPLE/MASK/RESET"->mask(op,a,base);
            case "NTUPLE/UWFUNC"->uwfunc(a,base);
            case "NTUPLE/LINTRA"->lintra(a,base);
            case "NTUPLE/VMEM"->{int mb=(int)integer(a,1,-1);if(mb>=0)store.cacheLimit(mb);else if(mb==-2)store.cachedBanks().forEach(output);else if(mb!=-1)throw new IllegalArgumentException("VMEM -2..128");output.accept(store.cacheStatus());}
            case "NTUPLE/FLAGS_QP"->{if(a.size()==1)output.accept(flags.toString());else {String key=a.get(1).toUpperCase(Locale.ROOT);if(!flags.containsKey(key))throw new IllegalArgumentException("Query flags: "+flags.keySet());if(a.size()>2){int v=Integer.parseInt(a.get(2));if(v<0||v>1)throw new IllegalArgumentException("Flag value is 0 or 1");flags.put(key,v);}output.accept(key+"="+flags.get(key));}}
            case "NTUPLE/CSELECT"->{commentOption=arg(a,1,"N").toUpperCase(Locale.ROOT);commentSize=Double.parseDouble(arg(a,2,".28"));if(commentSize<=0||!Double.isFinite(commentSize)||!commentOption.matches("[NRCB]*"))throw new IllegalArgumentException("CSELECT [NRCB] positive size");comments=commentOption.contains("N");if(!comments&&!lastSelection.isBlank())selectionLabel(lastSelection);}
            case "NTUPLE/GCUT"->gcut(a,base,depth);
            case "NTUPLE/HMERGE"->merge(a,base);
            case "NTUPLE/RECOVER"->{var t=store.get(a.get(1));if(t.sources.isEmpty())throw new IllegalArgumentException("RECOVER requires a file-backed Ntuple");Path source=t.sources.getFirst().file();long recovered=HipoRecovery.recover(source);store.reload(source);output.accept("Recovered "+recovered+" HIPO events and rebuilt index: "+source);}
            case "HISTOGRAM/FILE"->{store.open(Integer.parseInt(a.get(1)),file(a.get(2),base),arg(a,4," ").toUpperCase(Locale.ROOT).contains("N"));output.accept(store.directory());}
            case "HROUT"->{String id=arg(a,1,"0");Collection<NtupleStore.Tuple>tuples=id.equals("0")?store.tuples().values().stream().filter(t->t.id.startsWith(store.directory()+"/")).toList():List.of(store.get(id));store.write(store.unitFile(),tuples);output.accept("Wrote "+tuples.size()+" Ntuples to "+store.unitFile());}
            case "HRIN"->{String id=arg(a,1,"0");int offset=(int)integer(a,3,0);List<NtupleStore.Tuple>loaded=NtupleStore.read(store.unitFile(),"//PAWC");for(var t:loaded)if(id.equals("0")||NtupleStore.leaf(t.id).equals(id))store.install(t,offset);}
            case "CLOSE"->store.close(Integer.parseInt(a.get(1)));
            default->throw new IllegalStateException(op);
        }
        for(var m:masks.values())if(m.writable)m.write();
        if(flags.get("TIMING")==1)output.accept(String.format(Locale.ROOT,"Query completed in %.3f s",(System.nanoTime()-began)/1e9));return true;
    }
    record Selector(NtupleStore.Tuple tuple,List<String>names,List<ToDoubleFunction<double[]>>expressions){}
    Selector selector(String text,String separator,boolean all){
        int dot=text.indexOf('.');String id=dot<0?text:text.substring(0,dot);var t=store.get(id);
        List<String>names=dot<0?(all?t.columns:List.of(t.columns.getFirst())):Arrays.asList(text.substring(dot+1).split(Pattern.quote(separator),-1));
        names=names.stream().map(s->{if(s.matches("\\d+")){int n=Integer.parseInt(s);if(n<1||n>t.columns.size())throw new IllegalArgumentException("Column index out of range");return t.columns.get(n-1);}return s;}).toList();
        return new Selector(t,names,List.of());
    }
    private String expandCuts(String expression,Set<Integer>seen){Matcher m=Pattern.compile("\\$(\\d+)").matcher(expression);StringBuffer b=new StringBuffer();while(m.find()){int id=Integer.parseInt(m.group(1));if(id<1||id>99||!cuts.containsKey(id))throw new IllegalArgumentException("Undefined cut $"+id);if(!seen.add(id))throw new IllegalArgumentException("Cyclic cut $"+id);String e=expandCuts(cuts.get(id),seen);seen.remove(id);m.appendReplacement(b,Matcher.quoteReplacement("("+e+")"));}m.appendTail(b);return b.toString();}
    final class Query implements AutoCloseable {
        final Selector selector;final List<ToDoubleFunction<double[]>>expressions=new ArrayList<>();final List<NtupleCallback>callbacks=new ArrayList<>();final ToDoubleFunction<double[]>predicate;final NtupleMask destination;final int bit;final List<NtupleMask>inputs=new ArrayList<>();final List<Integer>inputBits=new ArrayList<>();
        Query(Selector selector,String selection,Path base,int depth)throws Exception{
            this.selector=selector;NtupleMask dest=null;int dbit=0;
            try{
                String[] parts=selection.split(">>",-1);if(parts.length>2)throw new IllegalArgumentException("Selection >> mask(bit)");String s=parts[0].isBlank()||parts[0].equals("0")?"1.":parts[0];
                if(parts.length==2){Matcher m=Pattern.compile("\\s*([A-Za-z][A-Za-z0-9_]*)\\((\\d+)\\)\\s*").matcher(parts[1]);if(!m.matches())throw new IllegalArgumentException("Mask destination name(bit)");dest=getMask(m.group(1));dbit=Integer.parseInt(m.group(2));NtupleMask.flag(dbit);bindMask(dest,selector.tuple);if(!dest.writable)throw new IllegalArgumentException("Read-only mask");dest.definitions.setProperty(""+dbit,s);}
                String expanded=expandCuts(s,new HashSet<>());List<String>columns=new ArrayList<>(selector.tuple.columns);
                Matcher m=Pattern.compile("([A-Za-z][A-Za-z0-9_]*)\\((\\d+)\\)").matcher(expanded);StringBuffer b=new StringBuffer();while(m.find()){NtupleMask mask=masks.get(m.group(1).toUpperCase(Locale.ROOT));if(mask==null)continue;int ib=Integer.parseInt(m.group(2));NtupleMask.flag(ib);bindMask(mask,selector.tuple);columns.add("_mask"+inputs.size());inputs.add(mask);inputBits.add(ib);m.appendReplacement(b,"_mask"+(inputs.size()-1));}m.appendTail(b);expanded=b.toString();
                predicate=compile(expanded,columns,base,depth);
                for(String expression:selector.names)expressions.add(compile(expandCuts(expression,new HashSet<>()),selector.tuple.columns,base,depth));
            }catch(Exception ex){for(var callback:callbacks)callback.close();throw ex;}
            destination=dest;bit=dbit;
        }
        ToDoubleFunction<double[]>compile(String text,List<String>columns,Path base,int depth)throws Exception{
            Matcher m=Pattern.compile("([A-Za-z0-9_./]+\\.jmacro)(?:\\(([^()]*)\\))?",Pattern.CASE_INSENSITIVE).matcher(text);
            Matcher whole=Pattern.compile("(.+\\.jmacro)(?:\\(([^()]*)\\))?",Pattern.CASE_INSENSITIVE).matcher(text.strip());if(whole.matches()&&Files.isRegularFile(file(whole.group(1),base)))m=whole.reset();
            List<String>extended=new ArrayList<>(columns);List<ToDoubleFunction<double[]>>functions=new ArrayList<>();StringBuffer replaced=new StringBuffer();
            while(m.find()){
                Path path=file(m.group(1),base);var context=commands.callbackContext(path,depth);NtupleCallback callback=new NtupleCallback(path,context);callbacks.add(callback);String parameter=m.group(2);boolean component=parameter!=null&&parameter.matches("\\d+");int index=component?Integer.parseInt(parameter):0;
                List<ToDoubleFunction<double[]>>arguments=parameter==null||component?List.of():Arrays.stream(parameter.split(",")).map(arg->NtupleExpressions.compile(arg,columns)).toList();
                functions.add(row->{context.callbackArguments(arguments.stream().mapToDouble(f->f.applyAsDouble(row)).toArray());double w=callback.apply(selector.tuple,currentRow,Arrays.copyOf(row,selector.tuple.columns.size()));return component?context.resultValue(index):w;});
                String synthetic="_callback"+functions.size();extended.add(synthetic);m.appendReplacement(replaced,synthetic);
            }m.appendTail(replaced);var expression=NtupleExpressions.compile(replaced.toString(),extended);if(functions.isEmpty())return expression;
            return row->{double[]v=Arrays.copyOf(row,extended.size());for(int j=0;j<functions.size();j++)v[columns.size()+j]=functions.get(j).applyAsDouble(row);return expression.applyAsDouble(v);};
        }
        long currentRow;
        double weight(long index,double[]row){currentRow=index;double[]r=row;if(!inputs.isEmpty()){r=Arrays.copyOf(row,row.length+inputs.size());for(int i=0;i<inputs.size();i++)r[row.length+i]=inputs.get(i).get(index,inputBits.get(i))?1:0;}double w=predicate.applyAsDouble(r);if(!Double.isFinite(w)){if(flags.get("STRICT")==1)throw new IllegalArgumentException("Nonfinite selection at row "+index);w=0;}if(destination!=null)destination.set(index,bit,w!=0);return w;}
        double[]values(double[]row){double[]v=new double[expressions.size()];for(int i=0;i<v.length;i++)v[i]=expressions.get(i).applyAsDouble(row);return v;}
        @Override public void close()throws Exception{Exception error=null;for(var c:callbacks)try{c.close();}catch(Exception e){error=e;}if(error!=null)throw error;}
    }
    private NtupleMask getMask(String name){var mask=masks.get(name.toUpperCase(Locale.ROOT));if(mask==null)throw new IllegalArgumentException("Mask not open: "+name);return mask;}
    private static void bindMask(NtupleMask mask,NtupleStore.Tuple tuple){String key=tuple.identity+":"+String.join(",",tuple.columns);if(mask.tuple.isBlank())mask.tuple=key;else if(!mask.tuple.equals(key))throw new IllegalArgumentException("Mask belongs to a different Ntuple");}
    private static double[][]limits(NtupleStore.Tuple t,long first,long count){double[][]v={new double[t.columns.size()],new double[t.columns.size()]};Arrays.fill(v[0],Double.POSITIVE_INFINITY);Arrays.fill(v[1],Double.NEGATIVE_INFINITY);t.rows(first,count,(i,r)->{for(int j=0;j<r.length;j++){v[0][j]=Math.min(v[0][j],r[j]);v[1][j]=Math.max(v[1][j],r[j]);}});return v;}
    private static H2F j4npClone(H2F h){return twig.data.DataSetSerializer.deserialize_H2F(twig.data.DataSetSerializer.toJson(h));}
    private void projection(String op,List<String>a,Path base,int depth)throws Exception{
        boolean project=op.endsWith("PROJECT");Selector s=selector(a.get(project?2:1),"%",false);int offset=project?1:0;String selection=arg(a,2+offset,"1."),option=project?"":arg(a,6," ").strip().toUpperCase(Locale.ROOT),id=project?a.get(1):arg(a,7,"1000000");long count=integer(a,3+offset,99999999),first=integer(a,4+offset,1);
        if(s.names.size()>4||project&&s.names.size()>2)throw new IllegalArgumentException("PLOT supports 1..4 expressions; PROJECT supports 1..2");
        if(!option.replace("PROFS","").replace("PROFI","").replace("PROF","").matches("[NCS+BLP*UEAG ]*"))throw new IllegalArgumentException("Plot options: N C S + B L P * U E A G PROF PROFS PROFI");
        if(!project){long update=integer(a,5,100000000);if(count<0){id=""+(-count);count=99999999;}if(first<0){id=""+(-first);first=1;}if(update<0){id=""+(-update);update=100000000;}if(update==0)throw new IllegalArgumentException("NUPD must be nonzero");}
        if(!project&&option.contains("+")&&arg(a,7,"1000000").equals("1000000"))id=lastPlotId;
        if(s.names.size()>=3){cloud(s,selection,first,count,option,base,depth);return;}
        DataSet original=workspace.objects().get(workspace.path(id));if(project&&original==null)throw new IllegalArgumentException("PROJECT requires a booked histogram");
        boolean profile=option.contains("PROF");if(profile&&s.names.size()!=2)throw new IllegalArgumentException("Profile needs Y%X");
        DataSet histogram=original==null||(!project&&id.equals("1000000")&&!option.contains("+"))?null:original instanceof H1F h?h.copy():original instanceof H2F h?j4npClone(h):null;
        if(original!=null&&!(original instanceof H1F||original instanceof H2F))throw new IllegalArgumentException("Projection target must be a histogram");
        try(var q=new Query(s,selection,base,depth)){
            if(histogram==null){double[][]range={new double[s.names.size()],new double[s.names.size()]};Arrays.fill(range[0],Double.POSITIVE_INFINITY);Arrays.fill(range[1],Double.NEGATIVE_INFINITY);s.tuple.rows(first,count,(i,row)->{if(q.weight(i,row)==0)return;double[]v=q.values(row);for(int j=0;j<v.length;j++)if(Double.isFinite(v[j])){range[0][j]=Math.min(range[0][j],v[j]);range[1][j]=Math.max(range[1][j],v[j]);}});for(int j=0;j<s.names.size();j++){double lo=range[0][j],hi=range[1][j];if(!Double.isFinite(lo)){lo=0;hi=1;}double pad=lo==hi?Math.max(1,Math.abs(lo)*.05):(hi-lo)*.025;range[0][j]=lo-pad;range[1][j]=hi+pad;}
                histogram=s.names.size()==1||profile?Workspace.book1(id,s.tuple.title,100,range[0][s.names.size()-1],range[1][s.names.size()-1]):new H2F(id,60,range[0][1],range[1][1],60,range[0][0],range[1][0]);
            }
            if((s.names.size()==1||profile)!=(histogram instanceof H1F))throw new IllegalArgumentException("Projection dimension differs from booked histogram");
            if(!project&&!option.contains("+")){if(histogram instanceof H1F h)h.reset();else((H2F)histogram).reset();}
            final DataSet target=histogram;ProfileState state=profiles.get(workspace.path(id));double[][]p=profile?(option.contains("+")&&state!=null&&state.histogram==original?Arrays.stream(state.sums).map(double[]::clone).toArray(double[][]::new):new double[4][((H1F)target).getAxis().getNBins()]):null;
            long interval=project?Long.MAX_VALUE:integer(a,5,100000000);if(interval<0)interval=100000000;final long frequency=interval;final long start=first;boolean[]preview={false};final String render=drawOption(target,option);
            s.tuple.rows(first,count,(i,row)->{double w=q.weight(i,row);if(w==0)return;double[]v=q.values(row);if(Arrays.stream(v).anyMatch(x->!Double.isFinite(x)))return;if(profile){int bin=((H1F)target).getAxis().getBin(v[1]);if(bin>=0&&bin<p[0].length){p[0][bin]+=w;p[1][bin]+=w*v[0];p[2][bin]+=w*v[0]*v[0];p[3][bin]+=w*w;}}else if(target instanceof H1F h)h.fill(v[0],w);else((H2F)target).fill(v[1],v[0],w);
                if(!project&&!profile&&!option.contains("N")&&!option.contains("S")&&(i-start+1)%frequency==0){DataSet copy=target instanceof H1F h?h.copy():j4npClone((H2F)target);copy=renderData(copy,option);if(preview[0])view.updatePlot(copy,render);else{view.draw(copy,render,false);preview[0]=true;}}
            });
            if(profile){H1F h=(H1F)target;for(int i=0;i<p[0].length;i++)if(p[0][i]!=0){double mean=p[1][i]/p[0][i],spread=Math.sqrt(Math.max(0,p[2][i]/p[0][i]-mean*mean)),neff=p[0][i]*p[0][i]/p[3][i];h.setBinContent(i,mean);h.setBinError(i,option.contains("PROFS")?spread:spread==0&&option.contains("PROFI")?1/Math.sqrt(12*neff):spread/Math.sqrt(neff));}}
            target.attr().setTitleX(s.names.getLast());target.attr().setTitleY(s.names.size()==1?"Counts":s.names.getFirst());workspace.put(id,target);if(profile)profiles.put(workspace.path(id),new ProfileState(target,p));else profiles.remove(workspace.path(id));
            if(!project&&!option.contains("N")){if(option.contains("*"))target.attr().setMarkerStyle(4);DataSet display=profile?renderProfile((H1F)target,p):renderData(target,option);if(preview[0]||option.contains("U"))view.updatePlot(display,render);else view.draw(display,render,option.contains("S"));
                view.axisLabels(!option.contains("A"));
                lastPlotId=id;lastSelection=selection;if(comments)selectionLabel(selection);}
        }
    }
    private static GraphErrors renderProfile(H1F histogram,double[][]sums){GraphErrors graph=new GraphErrors(histogram.getName());for(int i=0;i<sums[0].length;i++)if(sums[0][i]!=0)graph.addPoint(histogram.getAxis().getBinCenter(i),histogram.getBinContent(i),0,histogram.getBinError(i));graph.attr().setTitle(histogram.attr().getTitle());graph.attr().setTitleX(histogram.attr().getTitleX());graph.attr().setTitleY(histogram.attr().getTitleY());return graph;}
    private static DataSet renderData(DataSet target,String option){
        if(!(target instanceof H1F h)||!(option.contains("L")||option.contains("C")))return target;
        GraphErrors graph=new GraphErrors(h.getName());int n=h.getAxis().getNBins();boolean smooth=option.contains("C");
        for(int i=0;i<n;i++){double x=h.getAxis().getBinCenter(i),y=h.getBinContent(i);graph.addPoint(x,y,0,h.getBinError(i));if(smooth&&i+1<n){double a=h.getBinContent(Math.max(0,i-1)),b=y,c=h.getBinContent(i+1),d=h.getBinContent(Math.min(n-1,i+2));for(int j=1;j<12;j++){double t=j/12.,value=.5*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t);graph.addPoint(x+(h.getAxis().getBinCenter(i+1)-x)*t,value,0,0);}}}
        graph.attr().setTitle(h.attr().getTitle());graph.attr().setTitleX(h.attr().getTitleX());graph.attr().setTitleY(h.attr().getTitleY());graph.attr().setLineColor(h.attr().getLineColor());graph.attr().setLineWidth(h.attr().getLineWidth());return graph;
    }
    private static String drawOption(DataSet target,String option){if(target instanceof H2F)return "F";if(option.contains("PROF"))return "PE";String draw="";if(option.contains("B"))draw+="B";if(option.contains("P")&&!option.contains("PROF")||option.contains("*"))draw+="P";if(option.contains("E"))draw+="E";if(option.contains("L")||option.contains("C"))draw+="L";return draw;}
    private void cloud(Selector s,String selection,long first,long count,String option,Path base,int depth)throws Exception{
        List<double[]>points=new ArrayList<>();try(Query q=new Query(s,selection,base,depth)){s.tuple.rows(first,count,(i,row)->{if(q.weight(i,row)!=0){double[]v=q.values(row);if(Arrays.stream(v).allMatch(Double::isFinite)){if(points.size()>=1_000_000)throw new IllegalArgumentException("3D display limited to one million points; restrict NEVENT or selection");points.add(v);}}});}if(!option.contains("N")&&!points.isEmpty()){view.widget(new NtupleCloud(points,s.names,option,s.tuple.title),!option.contains("S"));lastSelection=selection;if(comments)selectionLabel(selection);}
    }
    private void selectionLabel(String selection){twig.widgets.PaveText text=new twig.widgets.PaveText(selection,commentOption.contains("R")?.9:commentOption.contains("C")?.5:.1,commentOption.contains("B")?.03:.95);text.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,Math.max(8,(int)(commentSize*40))));view.widget(text,false);}
    private List<String>variables(NtupleStore.Tuple tuple,List<String>list,int limit){if(list.isEmpty()||list.equals(List.of("")))return tuple.columns.subList(0,Math.min(limit,tuple.columns.size()));List<String>result=new ArrayList<>();for(String item:list){if(item.contains(":")){String[] ends=item.split(":",-1);int lo=ends[0].isBlank()?0:index(tuple,ends[0]),hi=ends[1].isBlank()?tuple.columns.size()-1:index(tuple,ends[1]);if(lo>hi)throw new IllegalArgumentException("Reversed column range");result.addAll(tuple.columns.subList(lo,hi+1));}else result.add(item);}return result;}
    private int index(NtupleStore.Tuple t,String name){for(int i=0;i<t.columns.size();i++)if(t.columns.get(i).equalsIgnoreCase(name))return i;throw new IllegalArgumentException("Unknown column: "+name);}
    private void scan(String op,List<String>a,Path base,int depth)throws Exception{
        boolean dump=op.endsWith("DUMP");Selector raw=selector(a.get(1),"%",true);String option=dump?"":arg(a,5," ").strip().toUpperCase(Locale.ROOT);List<String>names=dump?raw.names:variables(raw.tuple,words(a,6),option.contains("S")?512:10);if(!option.matches("S2?A?|AS2?|"))throw new IllegalArgumentException("SCAN option S, S2, SA or S2A");Selector s=new Selector(raw.tuple,names,List.of());long count=integer(a,3,99999999),first=integer(a,4,1);String selection=arg(a,2,"1."),dest=dump?arg(a,5," "):" ";
        try(Query q=new Query(s,selection,base,depth);PrintWriter writer=dest.isBlank()?null:new PrintWriter(Files.newBufferedWriter(file(dest,base)))){
            Consumer<String>out=writer==null?output:writer::println;
            if(option.contains("S")){spider(s,q,first,count,option);return;}
            if(!dump)out.accept("Row  "+String.join("  ",names));String separator=dump?arg(a,7,"%"):"  ";long[]accepted={0};s.tuple.rows(first,count,(i,row)->{if(q.weight(i,row)==0)return;accepted[0]++;String line=Arrays.stream(q.values(row)).mapToObj(v->String.format(Locale.ROOT,"%.12g",v)).collect(java.util.stream.Collectors.joining(separator));out.accept(dump?line:i+"  "+line);});if(!dump)output.accept("SCAN: "+accepted[0]+" selected rows");if(writer!=null&&writer.checkError())throw new IOException("Failed writing dump "+dest);
        }
    }
    private void spider(Selector s,Query q,long first,long count,String option)throws Exception{
        int n=s.names.size();if(n<3)throw new IllegalArgumentException("Graphical scan needs at least three variables");double[][]range={new double[n],new double[n]};double[]mean=new double[n];Arrays.fill(range[0],Double.POSITIVE_INFINITY);Arrays.fill(range[1],Double.NEGATIVE_INFINITY);long[]accepted={0};s.tuple.rows(first,count,(i,row)->{if(q.weight(i,row)==0)return;double[]v=q.values(row);if(Arrays.stream(v).anyMatch(x->!Double.isFinite(x)))return;accepted[0]++;for(int j=0;j<n;j++){range[0][j]=Math.min(range[0][j],v[j]);range[1][j]=Math.max(range[1][j],v[j]);mean[j]+=v[j];}});if(accepted[0]==0)return;for(int j=0;j<n;j++)mean[j]/=accepted[0];
        boolean[]auto={false};s.tuple.rows(first,count,(i,row)->{if(q.weight(i,row)==0)return;double[]v=q.values(row);if(Arrays.stream(v).anyMatch(x->!Double.isFinite(x)))return;GraphErrors graph=spiderGraph(v,range,option.contains("2"));graph.attr().setTitle("Row "+i+"  "+String.join(" / ",s.names));view.draw(graph,"L",false);if(option.contains("A")){GraphErrors avg=spiderGraph(mean,range,false);avg.attr().setLineColor(4);view.draw(avg,"L",true);}if(!auto[0])try{String response=view.readInput("Next event: Enter = next, G = continuous, N = stop","G");if(response==null||response.equalsIgnoreCase("N"))throw new java.util.concurrent.CancellationException("Graphical scan stopped");auto[0]=response.equalsIgnoreCase("G");}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalArgumentException(e);}});
    }
    private static GraphErrors spiderGraph(double[]v,double[][]range,boolean segments){GraphErrors graph=new GraphErrors();for(int j=0;j<=v.length;j++){int k=j%v.length;double r=range[1][k]==range[0][k]?.5:(v[k]-range[0][k])/(range[1][k]-range[0][k]),angle=k*2*Math.PI/v.length;if(segments)graph.addPoint(0,0,0,0);graph.addPoint(r*Math.cos(angle),r*Math.sin(angle),0,0);if(segments)graph.addPoint(0,0,0,0);}return graph;}
    private void read(List<String>a,Path base)throws IOException{
        var t=store.get(a.get(1));String format=arg(a,3,"*"),option=arg(a,4," ").toUpperCase(Locale.ROOT),match=arg(a,6," ");long max=integer(a,5,1000000);if(max<1)throw new IllegalArgumentException("NEVENT must be positive");if(!option.matches("[A ]*"))throw new IllegalArgumentException("READ option A allows trailing fields");
        FortranRowFormat fixed=format.equals("*")?null:new FortranRowFormat(format);
        boolean invert=match.startsWith("-");Matcher filter=Pattern.compile("-?/([^/]*)/(?:\\((\\d+|\\*)\\))?").matcher(match);if(!match.isBlank()&&!filter.matches())throw new IllegalArgumentException("MATCH /text/, -/text/, /text/(column), /text/(*)");String needle=match.isBlank()?null:filter.group(1);int column=needle==null||"*".equals(filter.group(2))?-1:filter.group(2)==null?0:Integer.parseInt(filter.group(2))-1;
        List<double[]>staged=new ArrayList<>();try(var reader=Files.newBufferedReader(file(a.get(2),base))){String line;int lineNo=0;while((line=reader.readLine())!=null&&staged.size()<max){lineNo++;if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();if(line.isBlank()||line.stripLeading().startsWith("#")||line.stripLeading().startsWith("*"))continue;
                if(needle!=null){boolean found=column<0?line.contains(needle):column>=0&&column+needle.length()<=line.length()&&line.startsWith(needle,column);if(found==invert)continue;}
                List<String>values;try{values=fixed==null?Arrays.asList(line.strip().split("[ ,\\t]+")):fixed.read(line);}catch(RuntimeException e){throw new IOException("Invalid fixed-format row at line "+lineNo+": "+e.getMessage(),e);}
                if(values.size()<t.columns.size())throw new IOException("Wrong column count at line "+lineNo);
                double[]row=new double[t.columns.size()];try{for(int i=0;i<row.length;i++)row[i]=Double.parseDouble(values.get(i).replace('D','E').replace('d','e'));t.validateRow(row);}catch(RuntimeException e){throw new IOException("Invalid numeric row at line "+lineNo+": "+e.getMessage(),e);}staged.add(row);
            }}staged.forEach(t::fill);output.accept("READ: "+staged.size()+" rows appended");
    }
    private void cuts(List<String>a,Path base)throws Exception{
        int id=Integer.parseInt(a.get(1).replace("$",""));if(id<0||id>99)throw new IllegalArgumentException("Cut ID 0..99");String option=arg(a,2,"P");
        switch(option.toUpperCase(Locale.ROOT)){
            case "P"->{cuts.forEach((key,value)->{if(id==0||id==key)output.accept("$"+key+" = "+value);});}
            case "-"->{if(id==0){cuts.clear();graphicalCuts.clear();}else{cuts.remove(id);graphicalCuts.remove(id);}}
            case "W"->{Properties p=new Properties();cuts.forEach((key,value)->{if(id==0||id==key){p.setProperty(""+key,value);if(graphicalCuts.containsKey(key))p.setProperty(key+".vertices",graphicalCuts.get(key).stream().map(v->v[0]+","+v[1]).collect(java.util.stream.Collectors.joining(";")));}});try(var writer=Files.newBufferedWriter(file(arg(a,3,"cuts.properties"),base))){p.store(writer,"PAW cuts");}}
            case "R"->{Properties p=new Properties();try(var reader=Files.newBufferedReader(file(arg(a,3,"cuts.properties"),base))){p.load(reader);}Map<Integer,String>incoming=new TreeMap<>();for(String key:p.stringPropertyNames()){if(key.endsWith(".vertices"))continue;int n=Integer.parseInt(key);if(n<1||n>99)throw new IllegalArgumentException("Invalid saved cut ID");if(id==0||id==n)incoming.put(n,p.getProperty(key));}cuts.putAll(incoming);for(int n:incoming.keySet()){String vertices=p.getProperty(n+".vertices");if(vertices!=null)graphicalCuts.put(n,Arrays.stream(vertices.split(";")).map(v->Arrays.stream(v.split(",")).mapToDouble(Double::parseDouble).toArray()).toList());}}
            case "D"->{String expression=cuts.get(id);if(expression==null)throw new IllegalArgumentException("Undefined cut $"+id);List<double[]>vertices=graphicalCuts.get(id);if(vertices==null)throw new IllegalArgumentException("Cut $"+id+" is an expression; GCUT creates drawable contours");GraphErrors g=new GraphErrors();for(double[]v:vertices)g.addPoint(v[0],v[1],0,0);double[]first=vertices.getFirst();g.addPoint(first[0],first[1],0,0);view.draw(g,"L",true);}
            default->{if(id==0)throw new IllegalArgumentException("Expression cuts use IDs 1..99");String prior=cuts.put(id,option);try{expandCuts("$"+id,new HashSet<>());}catch(Exception e){if(prior==null)cuts.remove(id);else cuts.put(id,prior);throw e;}graphicalCuts.remove(id);}
        }
    }
    private void mask(String op,List<String>a,Path base)throws IOException{
        switch(op){
            case "NTUPLE/MASK/FILE"->{Path path=file(a.get(1),base);String stem=path.getFileName().toString().replaceFirst("\\.[^.]+$","").toUpperCase(Locale.ROOT);if(!stem.matches("[A-Z][A-Z0-9_]*"))throw new IllegalArgumentException("Mask filename must have an identifier stem");if(masks.containsKey(stem))throw new IllegalArgumentException("Mask already open: "+stem);String option=arg(a,2,"R").toUpperCase(Locale.ROOT);NtupleMask mask;if(option.equals("N")){if(Files.exists(path))throw new IOException("Mask file exists: "+path);mask=new NtupleMask(stem,path,true);mask.write();}else if(option.equals("R")||option.equals("U")){if(!Files.isRegularFile(path))throw new IOException("Mask file not found: "+path);mask=NtupleMask.read(stem,path,option.equals("U"));}else throw new IllegalArgumentException("MASK/FILE option R/N/U");masks.put(stem,mask);}
            case "NTUPLE/MASK/CLOSE"->{NtupleMask m=getMask(a.get(1));if(m.writable)m.write();masks.remove(m.name);}
            case "NTUPLE/MASK/LIST"->{String name=arg(a,1,"*");for(var m:masks.values())if(name.equals("*")||m.name.equalsIgnoreCase(name))output.accept(m.name+" "+m.file+" "+(m.writable?"update":"read")+" "+m.rows.size()+" rows "+m.definitions);}
            case "NTUPLE/MASK/RESET"->{NtupleMask m=getMask(a.get(1));int bit=(int)integer(a,2,1);NtupleMask.flag(bit);if(!m.writable)throw new IllegalArgumentException("Read-only mask");m.rows.replaceAll((row,bits)->bits&~NtupleMask.flag(bit));m.definitions.remove(""+bit);}
        }
    }
    private void chain(List<String>a,Path base){
        if(a.size()==1){store.chains().forEach((n,m)->output.accept(n+" = "+m));return;}String name=a.get(1);if(name.equals("-")){new ArrayList<>(store.chains().keySet()).forEach(store::removeChain);return;}if(name.startsWith("-")){store.removeChain(name.substring(1));return;}if(name.endsWith(">")){String key=name.substring(0,name.length()-1);store.expand(key,new HashSet<>()).forEach(p->output.accept(key+" -> "+p));return;}
        List<String>members=new ArrayList<>(store.chains().getOrDefault(name,List.of()));for(String member:words(a,2)){if(member.startsWith("-P")){Path parent=file(member.substring(2),base);members.replaceAll(m->store.chains().containsKey(m)||m.startsWith("//")?m:parent.resolve(Path.of(m).getFileName()).toString());}else if(member.startsWith("-")){String target=member.substring(1);members.removeIf(m->m.equals(target)||m.equals(file(target,base).toString()));}else members.add(store.chains().containsKey(member)||member.startsWith("//")?member:file(member,base).toString());}if(members.isEmpty())store.removeChain(name);else store.chain(name,members);
    }
    private void uwfunc(List<String>a,Path base)throws IOException{
        var t=store.get(a.get(1));Path path=file(a.get(2),base);if(!path.toString().endsWith(".jmacro"))throw new IllegalArgumentException("Java callback filename must end in .jmacro");String option=arg(a,3," ").toUpperCase(Locale.ROOT);if(!option.matches("[EPT ]*"))throw new IllegalArgumentException("UWFUNC options E/P/T");StringBuilder body=new StringBuilder("// Ntuple "+t.id+" — "+t.title.replace('\n',' ') +"\n// eventNumber is the one-based row. Set paw.weight(0) to reject a row.\n");for(String c:t.columns)body.append("double ").append(c).append(" = paw.value(\"").append(c).append("\");\n");body.append("paw.weight(1.0);\n// For a projection callback: paw.result(X, Y);\n");if(option.contains("T"))body.append("// Columns: ").append(String.join(", ",t.columns)).append('\n');Files.writeString(path,body,StandardOpenOption.CREATE_NEW);if(option.contains("P"))output.accept(body.toString());if(option.contains("E"))view.editFile(path);output.accept("Generated callback "+path);
    }
    private void lintra(List<String>a,Path base)throws IOException{
        var t=store.get(a.get(1));String option=arg(a,2," ").toUpperCase(Locale.ROOT);if(!option.matches("[NP ]*"))throw new IllegalArgumentException("LINTRA options N/P");int requested=(int)integer(a,5,20);if(requested<0||requested>20)throw new IllegalArgumentException("NVARS 0..20");List<String>names=variables(t,words(a,6),Math.min(requested==0?20:requested,t.columns.size()));if(names.isEmpty()||names.size()>20)throw new IllegalArgumentException("LINTRA uses 1..20 variables");int n=names.size();List<ToDoubleFunction<double[]>>f=names.stream().map(s->NtupleExpressions.compile(s,t.columns)).toList();long[]count={0};double[]mean=new double[n];double[][]cov=new double[n][n];
        t.rows(integer(a,4,1),integer(a,3,99999999),(i,row)->{double[]v=f.stream().mapToDouble(x->x.applyAsDouble(row)).toArray();if(Arrays.stream(v).anyMatch(x->!Double.isFinite(x)))return;count[0]++;double[]delta=new double[n];for(int j=0;j<n;j++){delta[j]=v[j]-mean[j];mean[j]+=delta[j]/count[0];}for(int j=0;j<n;j++)for(int k=0;k<n;k++)cov[j][k]+=delta[j]*(v[k]-mean[k]);});if(count[0]<2)throw new IllegalArgumentException("PCA needs at least two finite rows");double[]scale=new double[n];for(int j=0;j<n;j++)scale[j]=option.contains("N")?Math.sqrt(cov[j][j]/(count[0]-1)):1;for(int j=0;j<n;j++)if(scale[j]==0)scale[j]=1;for(int j=0;j<n;j++)for(int k=0;k<n;k++)cov[j][k]/=(count[0]-1)*scale[j]*scale[k];
        double[][]vectors=new double[n][n];for(int j=0;j<n;j++)vectors[j][j]=1;for(int iter=0;iter<100*n*n;iter++){int p=0,q=0;double max=0;for(int j=0;j<n;j++)for(int k=j+1;k<n;k++)if(Math.abs(cov[j][k])>max){max=Math.abs(cov[j][k]);p=j;q=k;}if(max<1e-12)break;double theta=.5*Math.atan2(2*cov[p][q],cov[q][q]-cov[p][p]),c=Math.cos(theta),s=Math.sin(theta);double pp=cov[p][p],qq=cov[q][q],pq=cov[p][q];for(int j=0;j<n;j++)if(j!=p&&j!=q){double jp=cov[j][p],jq=cov[j][q];cov[j][p]=cov[p][j]=c*jp-s*jq;cov[j][q]=cov[q][j]=s*jp+c*jq;}cov[p][p]=c*c*pp-2*s*c*pq+s*s*qq;cov[q][q]=s*s*pp+2*s*c*pq+c*c*qq;cov[p][q]=cov[q][p]=0;for(int j=0;j<n;j++){double jp=vectors[j][p],jq=vectors[j][q];vectors[j][p]=c*jp-s*jq;vectors[j][q]=s*jp+c*jq;}}
        List<Integer>order=java.util.stream.IntStream.range(0,n).boxed().sorted((i,j)->Double.compare(cov[j][j],cov[i][i])).toList();StringBuilder code=new StringBuilder("// Principal components of "+t.id+". Arguments are one-based projection indices.\n");for(int j=0;j<n;j++)code.append("double v").append(j).append(" = (").append(javaExpression(names.get(j),t.columns)).append(" - (").append(mean[j]).append("))/").append(scale[j]).append(";\n");code.append("paw.result(\n");for(int rank=0;rank<n;rank++){int k=order.get(rank);for(int j=0;j<n;j++){if(j>0)code.append(" + ");code.append("(").append(vectors[j][k]).append(")*v").append(j);}code.append(rank==n-1?"\n);\n":",\n");output.accept("Component "+(rank+1)+" eigenvalue="+cov[k][k]);if(option.contains("P"))output.accept("  coefficients="+java.util.stream.IntStream.range(0,n).mapToObj(j->names.get(j)+":"+vectors[j][k]).toList());}Path path=file("xtoxsi.jmacro",base);Files.writeString(path,code,StandardOpenOption.CREATE_NEW);output.accept("Wrote "+path);
    }
    private static String javaExpression(String expression,List<String>columns){return "Expression.compile("+javaString(NtupleExpressions.translate(expression,columns))+", List.of("+columns.stream().map(NtupleCommands::javaString).collect(java.util.stream.Collectors.joining(","))+" )).applyAsDouble(new double[]{"+columns.stream().map(c->"paw.value("+javaString(c)+")").collect(java.util.stream.Collectors.joining(","))+"})";}
    private static String javaString(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";}
    final Map<Integer,List<double[]>>graphicalCuts=new HashMap<>();
    private void gcut(List<String>a,Path base,int depth)throws Exception{
        int cid=Integer.parseInt(a.get(1).replace("$",""));if(cid<1||cid>99)throw new IllegalArgumentException("GCUT ID 1..99");Selector s=selector(a.get(2),"%",false);if(s.names.size()>2)throw new IllegalArgumentException("GCUT accepts one or two expressions");List<String>plot=new ArrayList<>(List.of("NTUPLE/PLOT",a.get(2),arg(a,3,"1."),arg(a,4,"99999999"),arg(a,5,"1"),arg(a,6,"100000000"),arg(a,7," "),arg(a,8,"1000000")));projection("NTUPLE/PLOT",plot,base,depth);
        List<double[]>points=new ArrayList<>();try(Query q=new Query(s,arg(a,3,"1."),base,depth)){s.tuple.rows(integer(a,5,1),integer(a,4,99999999),(i,row)->{if(q.weight(i,row)!=0){double[]v=q.values(row);if(Arrays.stream(v).allMatch(Double::isFinite)&&points.size()<100000)points.add(s.names.size()==1?new double[]{v[0],0}:new double[]{v[1],v[0]});}});}if(points.isEmpty())throw new IllegalArgumentException("No finite selected rows for GCUT");List<double[]>vertices=view.graphicalCut("$"+cid,s.names,points);if(vertices==null)throw new java.util.concurrent.CancellationException("Graphical cut cancelled");String expr;if(s.names.size()==1){if(vertices.size()!=2)throw new IllegalArgumentException("Range cut requires two endpoints");expr="("+s.names.getFirst()+">="+Math.min(vertices.get(0)[0],vertices.get(1)[0])+")&&("+s.names.getFirst()+"<="+Math.max(vertices.get(0)[0],vertices.get(1)[0])+")";}else{if(vertices.size()<3)throw new IllegalArgumentException("Polygon needs at least three vertices");expr="0";String x="("+s.names.get(1)+")",y="("+s.names.get(0)+")";for(int i=0;i<vertices.size();i++){double[]u=vertices.get(i),v=vertices.get((i+1)%vertices.size());if(u[1]==v[1])continue;String cross="(("+y+">"+u[1]+")!=("+y+">"+v[1]+"))&&("+x+"<(("+(v[0]-u[0])+"*("+y+"-("+u[1]+"))/"+(v[1]-u[1])+")+ ("+u[0]+")))";expr="("+expr+")!=("+cross+")";}}cuts.put(cid,expr);graphicalCuts.put(cid,vertices);output.accept("$"+cid+" = "+expr);
    }
    private void merge(List<String>a,Path base)throws IOException{
        List<String>inputs=words(a,2);if(inputs.isEmpty())throw new IllegalArgumentException("HMERGE requires input files");Map<String,NtupleStore.Tuple>merged=new LinkedHashMap<>();Map<String,DataSet>histograms=new LinkedHashMap<>();for(String input:inputs){Path path=file(input,base);List<NtupleStore.Tuple>tuples=Workspace.schemas(path).isEmpty()?List.of():NtupleStore.read(path,null);for(var t:tuples){var dest=merged.get(t.id);if(dest==null){dest=new NtupleStore.Tuple(t.id,t.title,t.columns,t.types);merged.put(t.id,dest);}if(!dest.columns.equals(t.columns)||!dest.types.equals(t.types))throw new IllegalArgumentException("HMERGE incompatible Ntuple "+t.id);dest.sources.addAll(t.sources);}Workspace w=new Workspace();if(Workspace.isWorkspace(path)){w.load(path);for(var e:w.objects().entrySet()){if(!histograms.containsKey(e.getKey()))histograms.put(e.getKey(),e.getValue());else{Workspace sum=new Workspace();sum.put("a",histograms.get(e.getKey()));sum.put("b",e.getValue());histograms.put(e.getKey(),sum.arithmetic("a","b","result","ADD"));}}}}
        store.write(file(a.get(1),base),merged.values(),histograms);output.accept("Merged "+inputs.size()+" files");
    }
}
