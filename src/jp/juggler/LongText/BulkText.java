package jp.juggler.LongText;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import jp.juggler.util.LogCategory;

public class BulkText {
	static final LogCategory log = new LogCategory("BulkText");
	int fno;
	int line_count;
	int text_length;
	int[] offset_list;
	int[] length_list;
	String text;

	BulkText(int fno,File file){
		try{
			this.fno = fno;
			
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			FileInputStream fi = new FileInputStream(file);
			try{
				int nRead = 0;
				byte[] tmp = new byte[16384];
				for(;;){
					int delta = fi.read(tmp,0,tmp.length);
					if(delta==-1) break;
					bao.write(tmp,0,delta);
					nRead+=delta;
				}
			}finally{
				fi.close();
			}
			byte[] buf = bao.toByteArray();
			int p = 0;
			line_count =   readInt(buf,p); p+= 4;
			text_length = readInt(buf,p); p+= 4;
			offset_list = new int[line_count];
			length_list = new int[line_count];
			for(int i=0;i<line_count;++i){
				offset_list[i]= 
					((buf[p+0]&255)      )|
					((buf[p+1]&255) <<  8)|
					((buf[p+2]&255) << 16)|
					((buf[p+3]&255) << 24);
				p+=4;
			}
			for(int i=0;i<line_count;++i){
				length_list[i]= 
					((buf[p+0]&255)      )|
					((buf[p+1]&255) <<  8)|
					((buf[p+2]&255) << 16)|
					((buf[p+3]&255) << 24);
				p+=4;
			}
			text = new String(buf,p,buf.length-p,"UTF-16LE");
		}catch(IOException ex){
			ex.printStackTrace();
			line_count = 0;
		}
	}
	
	static final int readInt(byte[] buf,int pos){
		return
		 ((buf[pos+0]&255)      )|
		 ((buf[pos+1]&255) <<  8)|
		 ((buf[pos+2]&255) << 16)|
		 ((buf[pos+3]&255) << 24);
	}

	CharSequence getLine(int lno){
		try{
			int offset = offset_list[lno];
			int length = length_list[lno];
			return text.substring(offset,offset+length);
		}catch(Throwable ex){
			return ex.getMessage(); 
		}
	}
}
