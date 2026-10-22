package org.jlab.paw;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import j4np.hipo5.data.*;
import j4np.hipo5.io.*;

/** Persistent, row-indexed 32-bit PAW masks in native HIPO storage. */
final class NtupleMask {
    final String name;final Path file;final boolean writable;
    String tuple="";final Map<Long,Integer> rows=new TreeMap<>();final Properties definitions=new Properties();
    NtupleMask(String name,Path file,boolean writable){this.name=name;this.file=file;this.writable=writable;}
    void set(long row,int bit,boolean value){if(!writable)throw new IllegalArgumentException("Mask is read-only");int flag=flag(bit);rows.put(row,value?rows.getOrDefault(row,0)|flag:rows.getOrDefault(row,0)&~flag);}
    boolean get(long row,int bit){return (rows.getOrDefault(row,0)&flag(bit))!=0;}
    static int flag(int bit){if(bit<1||bit>32)throw new IllegalArgumentException("Mask bit is 1..32");return 1<<(bit-1);}
    void write()throws IOException{
        Path temp=Files.createTempFile(file.toAbsolutePath().getParent(),".paw-mask-",".hipo");HipoWriter writer=new HipoWriter();Schema schema=new Schema.SchemaBuilder("PAW::Mask",32102,1).addEntry("row","L","One-based row").addEntry("bits","I","32 selection bits").build();writer.getSchemaFactory().addSchema(schema);
        try{if(!writer.open(temp.toString()))throw new IOException("Cannot write mask");try{Event header=new Event();header.write(new Node(32102,2,tuple));StringWriter data=new StringWriter();definitions.store(data,"Mask definitions");header.write(new Node(32102,3,data.toString()));writer.addEvent(header);for(var entry:rows.entrySet()){Bank bank=new Bank(schema,1);bank.putLong("row",0,entry.getKey());bank.putInt("bits",0,entry.getValue());Event event=new Event();event.write(bank);writer.addEvent(event);}}finally{writer.close();}try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
    static NtupleMask read(String name,Path file,boolean writable)throws IOException{
        NtupleMask mask=new NtupleMask(name,file,writable);HipoReader reader=new HipoReader();try{reader.setDebugMode(0);reader.open(file.toString());if(!reader.getSchemaFactory().hasSchema("PAW::Mask"))throw new IOException("Not a HIPO mask");Bank bank=new Bank(reader.getSchemaFactory().getSchema("PAW::Mask"));Event event=new Event();while(reader.hasNext()){reader.nextEvent(event);if(event.scan(32102,2)>=0)mask.tuple=event.read(32102,2).getString();if(event.scan(32102,3)>=0)mask.definitions.load(new StringReader(event.read(32102,3).getString()));event.read(bank);for(int i=0;i<bank.getRows();i++)mask.rows.put(bank.getLong("row",i),bank.getInt("bits",i));}}finally{reader.close();}return mask;
    }
}
