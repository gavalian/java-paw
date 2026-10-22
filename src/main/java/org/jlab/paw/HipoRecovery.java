package org.jlab.paw;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import j4np.hipo5.base.*;
import j4np.hipo5.data.*;
import j4np.hipo5.io.*;

/** Recover complete native records by physical scanning, independent of the trailer index. */
final class HipoRecovery {
    static long recover(Path source)throws IOException{
        Path target=source.toAbsolutePath(),temp=Files.createTempFile(target.getParent(),".paw-recover-",".hipo");long recovered=0;
        try(RandomAccessFile input=new RandomAccessFile(target.toFile(),"r")){
            byte[]bytes=new byte[56];input.readFully(bytes);FileHeader fh=new FileHeader();fh.readHeader(ByteBuffer.wrap(bytes));
            RecordInputStream dictionary=new RecordInputStream();dictionary.readRecord(input,fh.getHeaderLength()+fh.getIndexLength());HipoWriter writer=new HipoWriter();Event event=new Event();
            for(int i=0;i<dictionary.getEntries();i++){event.initFrom(dictionary.getEvent(i));if(event.scan(120,1)>=0)writer.getSchemaFactory().addSchema(Schema.fromJsonString(event.read(120,1).getString()));}
            if(!writer.open(temp.toString()))throw new IOException("Cannot open recovered HIPO output");
            try{long pos=fh.getHeaderLength()+fh.getIndexLength()+4L*((fh.getUserHeaderLength()+3)/4);while(pos+56<=input.length()){
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Recovery cancelled");input.seek(pos);input.readFully(bytes);RecordHeader rh=new RecordHeader();
                try{rh.readHeader(ByteBuffer.wrap(bytes));}catch(HipoException bad){pos+=4;continue;}
                if(rh.getHeaderType().name().contains("TRAILER"))break;
                int length=rh.getLength();if(length<56||pos+length>input.length()){pos+=4;continue;}
                try{RecordInputStream record=new RecordInputStream();record.readRecord(input,pos);for(int i=0;i<record.getEntries();i++){event.initFrom(record.getEvent(i));writer.addEvent(event);recovered++;}}
                catch(RuntimeException corrupt){/* Skip this damaged record; continue at its validated boundary. */}
                pos+=length;
            }}finally{writer.close();}
            if(recovered==0)throw new IOException("No complete HIPO records could be recovered; original preserved");
            // Validate the new trailer before replacing the original.
            HipoReader check=new HipoReader();try{check.setDebugMode(0);check.open(temp.toString());if(check.getErrorCode()!=HipoReader.NO_ERROR)throw new IOException("Recovered file failed HIPO validation");}finally{check.close();}
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}return recovered;
        }catch(HipoException ex){throw new IOException("Cannot recover HIPO header or dictionary; original preserved",ex);}
        finally{Files.deleteIfExists(temp);}
    }
}
