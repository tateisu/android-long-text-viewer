package jp.juggler.LongText;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class BulkTextBuilder {
	
	ByteArrayOutputStream offset_list;
	ByteArrayOutputStream length_list;
	ByteArrayOutputStream char_list;
	int line_count =0;
	int text_length =0;
	
	BulkTextBuilder(){
		offset_list = new ByteArrayOutputStream(16384);
		length_list = new ByteArrayOutputStream(16384);
		char_list   = new ByteArrayOutputStream(16384);
	}
	void reset(){
		line_count = 0;
		text_length =0;
		char_list.reset();
		offset_list.reset();
		length_list.reset();
	}

	static final void writeInt(OutputStream bao,int v)  throws IOException{
		bao.write( (v      )&255 );
		bao.write( (v >>  8)&255 );
		bao.write( (v >> 16)&255 );
		bao.write( (v >> 24)&255 );
	}

	void add(String line) throws IOException{
		int line_length = line.length();
		writeInt(offset_list,text_length);
		writeInt(length_list,line_length );
		char_list.write( line.getBytes("UTF-16LE"));
		text_length += line_length;
		line_count++;
	}

	void save(File file) throws IOException{
		FileOutputStream fo = new FileOutputStream(file);
		writeInt(fo,line_count);
		writeInt(fo,text_length);
		fo.write(offset_list.toByteArray());
		fo.write(length_list.toByteArray());
		fo.write(char_list.toByteArray());
		fo.flush();
		fo.close();
	}
}
