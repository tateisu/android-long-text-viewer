package jp.juggler.LongText;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.Map.Entry;

import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Window;
import android.widget.ListView;
import android.widget.Toast;

public class ActText extends Activity {
	static final LogCategory log = new LogCategory("TextViewer");

	final int file_step = 1000;
	final int cache_limit = 3;
    static final String[] enc_16bit = new String[]{
		"UTF-16BE", "UTF-16LE", "UTF-16",
	};

	ListView lvTextList;
	TextListAdapter adapter;
	Handler ui_handler;
	Intent opener;
	ProgressDialog progress_encoding = null;

	boolean bStart;
	int task_id;
	// 
	String encoding = null;
	String[] encoding_list = null;
	
    /////////////////////////
	void initUI(){
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.text_act);
        lvTextList = (ListView)findViewById(R.id.list);
        
        adapter = new TextListAdapter(this,cache);
        lvTextList.setAdapter(adapter);
        ui_handler = new Handler();
	}

	@Override public void onCreate(Bundle savedInstanceState) {
    	log.d("onCreate");
        super.onCreate(savedInstanceState);
        initUI();
        initOpener(getIntent());
    }
	@Override protected void onNewIntent(Intent intent) {
    	log.d("onNewIntent");
        initOpener(intent);
	}
    
	@Override protected void onDestroy() {
    	log.d("onDestroy");
		super.onDestroy();
	}


	void initOpener(Intent intent){
		opener = intent;
		encoding = null;
		encoding_list = null;
		text_loader = null;
	}
	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);
		encoding = state.getString("encoding");
		encoding_list = state.getStringArray("encoding_list");
		log.d("onRestoreInstanceState encoding=%s list=%s",encoding,encoding_list);
	}

	
	@Override
	protected void onStart() {
		log.d("onStart");
		super.onStart();
	}

	@Override
	protected void onResume() {
		log.d("onResume");
		super.onResume();
		if( !bStart){
			bStart = true;
			task_id = getTaskId();
			next_step();
		}
	}
	

	@Override
	protected void onStop() {
		log.d("onStop");
		super.onStop();
		bStart = false;
		if( text_loader != null ) text_loader.joinLoop(log,"text_loader");
		if( encoding_checker != null ) encoding_checker.joinLoop(log,"encding_checker");
		if( progress_encoding != null ) progress_encoding.cancel();
		adapter.clear();
		cache.clear();
		delete_tmp();
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		log.d("onSaveInstanceState");
		super.onSaveInstanceState(state);
		if(encoding!=null && !encoding.startsWith("error:")) state.putString("encoding",encoding);
		if(encoding_list!=null && encoding_list.length>0) state.putStringArray("encoding_list",encoding_list);
	}


	//////////////////////////////////////////////////////
	
	void next_step(){
		if(!bStart) return ;
		
		log.d("next_step encoding=%s list=%s",encoding,encoding_list);

		if( encoding == null ){
			if( encoding_list == null ){
				// エンコーディングを調べていない

				// make progress dialog
				progress_encoding = new ProgressDialog(this);
				progress_encoding.setProgressStyle(ProgressDialog.STYLE_SPINNER);
				progress_encoding.setMessage("guess encoding...");
				progress_encoding.setCancelable(false);
				progress_encoding.show();
				// make encoding check thread
				encoding_checker = new EncodingChecker();
				encoding_checker.start();
				// 
				return;
			}else if(encoding_list.length < 1 ){
				Toast.makeText(this,"解釈可能な文字エンコーディングがありません",Toast.LENGTH_SHORT).show();
				finish();
				return;
			}else if(encoding_list.length == 1 ){
				encoding = encoding_list[0];
			}else{
				// エンコーディングを選択
				AlertDialog.Builder dlg = new AlertDialog.Builder(this); 
		        dlg.setTitle("select encoding");
		        dlg.setItems(encoding_list, new DialogInterface.OnClickListener(){ 
     	 			public void onClick(DialogInterface dialog, int pos){
     	 				if(pos >= 0 && pos <  encoding_list.length ){
     	 					encoding = encoding_list[pos];
     	 					next_step();
     	 				}else{
     	 					ActText.this.finish();
     	 				}
     	 			}
	        	});
		        dlg.setOnCancelListener(new OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						log.d("cancelled");
 	 					ActText.this.finish();
					}
				});
		        dlg.show();
		        return;
			}
		}
		if( encoding.startsWith("error")){
			Toast.makeText(this,encoding.substring(6),Toast.LENGTH_SHORT).show();
			finish();
			return;		
		}
		if( text_loader != null ){
			log.d("loader already started.");
			return;
		}
        setProgressBarVisibility(true);
        setProgressBarIndeterminate(true);
		text_loader = new TextLoader();
		text_loader.start();
	}

	void prepare_lines(int lines){
		if(!bStart) return;
		adapter.addLineCount(lines);
	}

	MyTextCache cache = new MyTextCache();
	class MyTextCache implements TextCache{
		LinkedList<BulkText> cache = new  LinkedList<BulkText>();

		void clear(){
			cache.clear();
		}

		public CharSequence getLine(int lno){
			
			try{
				int fno = lno / file_step;
				// check cache
				int i=0;
				for(BulkText bulk : cache ){
					if( bulk.fno == fno ){
						if( i> 0){
							// move cache to first
							cache.remove(i);
							cache.addFirst(bulk);
						}
						return bulk.getLine(lno - fno * file_step);  
					}
					++i;
				}
				while( cache.size() >= cache_limit ){
					cache.removeLast();
				}
				File extdir = Environment.getExternalStorageDirectory();
				File tmpdir = new File(extdir,String.format("tmp_LongText_%d",task_id));
				BulkText bulk = new BulkText(fno,new File(tmpdir,String.format("%d",fno)));
				cache.addFirst(bulk);
				return bulk.getLine(lno - fno * file_step);  
			}catch(Throwable ex){
				ex.printStackTrace();
				return ex.getMessage();
			}
		}
	};
	
	void delete_tmp(){
		try{
			File extdir = Environment.getExternalStorageDirectory();
			File tmpdir = new File(extdir,String.format("tmp_LongText_%d",task_id));
			if(tmpdir.exists()){
				for( String entry : tmpdir.list() ){
					try{
						new File(tmpdir,entry).delete();
					}catch(Throwable ex){
					}
				}
				tmpdir.delete();
			}
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}

	///////////////////////////////////////////////////////////////
	// テキストを一時ファイルに保存
	
	TextLoader text_loader = null;
    class TextLoader extends WorkerBase{
    	volatile boolean bCancelled = false;
		@Override public void cancel() {
			bCancelled = true;
			notifyEx();
		}
		public void run(){
			try{
				Context context = getApplicationContext();
				ContentResolver cr = context.getContentResolver();
				InputStream is = cr.openInputStream(opener.getData());
				try{
					File extdir = Environment.getExternalStorageDirectory();
					if( ! extdir.canWrite() ) throw new Exception("missing permission to write to "+extdir.getPath());
					File tmpdir = new File(extdir,String.format("tmp_LongText_%d",task_id));
					if( ! tmpdir.exists() ){
						if( ! tmpdir.mkdir() ) throw new Exception("cannot create directory: "+tmpdir.getPath());
					}
					if( ! tmpdir.canWrite() ) throw new Exception("missing permission to write to "+tmpdir.getPath());
	
					LineNumberReader reader = new LineNumberReader(new InputStreamReader(is,encoding));
					
					BulkTextBuilder builder = new BulkTextBuilder();
					int fno=0;
					while(!bCancelled){
						String line = reader.readLine();
						if(line==null) break;
						builder.add(line);
						if( builder.line_count == file_step ){
							builder.save(new File(tmpdir,String.format("%d",fno++)));
							builder.reset();
							ui_handler.post(new Runnable() {
								@Override public void run() {
									prepare_lines(file_step);
								}
							});
						}
					}
					if( builder.line_count > 0){
						final int lines = builder.line_count;
						builder.save(new File(tmpdir,String.format("%d",fno++)));
						builder.reset();
						ui_handler.post(new Runnable() {
							@Override public void run() {
								prepare_lines(lines);
						        setProgressBarVisibility(false);
						        setProgressBarIndeterminate(false);
							}
						});
					}
				}finally{
					is.close();
				}
			}catch(Throwable ex){
				ex.printStackTrace();
				encoding_list = null;
				encoding = "error:"+ex.getMessage();
				ui_handler.post(new Runnable() {
					@Override public void run() {
						if(bCancelled) return;
						next_step();
					}
				});
			}
		}
    }
	
    ///////////////////////////////////////////////////////////////
	// 文字コード検出

	EncodingChecker encoding_checker;
    class EncodingChecker extends WorkerBase{
    	volatile boolean bCancelled = false;
		@Override public void cancel() {
			bCancelled = true;
			notifyEx();
		}
		public void run(){
			Context context = getApplicationContext();
			try{
				ContentResolver cr = context.getContentResolver();
				InputStream is = cr.openInputStream(opener.getData());
				byte[] sample = new byte[16384];
				int delta = is.read(sample);
				is.close();
				if(delta<=0) throw new Exception("指定されたデータはサイズ0です");

				TreeSet<String> found_charsets = new TreeSet<String>();
				
				// check 16bit 
				if( delta >= 2  && (delta&1)==0 ){
					for( String enc : enc_16bit ){
						if(bCancelled) new Exception("キャンセルされました");
						try{
							// log.d("try %s",enc);
							if(new String(sample,0,delta,enc).length() > 0 ) 
								found_charsets.add(enc);
						}catch(Throwable ex){
							log.d("enc=%s error=%s",enc,ex.getMessage());
						}
					}
				}
				// 改行コードにあたるまでsampleの末尾を削る
				while( delta > 0 && sample[delta-1]!=0x0d && sample[delta-1]!=0x0a ){
					--delta;
					if(bCancelled) new Exception("キャンセルされました");
				}
				log.d("sample length=%d",delta);
				// 全ての文字コードをチェックする
				for( Entry<String, Charset> pair : java.nio.charset.Charset.availableCharsets().entrySet() ){
					if(bCancelled) new Exception("キャンセルされました");
					String enc = pair.getKey();

					// workaround to avoid SYSSEGV
					if(enc.equals("x-iscii-pa")) continue;
					
					try{
						// log.d("try %s",enc);
						if(new String(sample,0,delta,enc).length() > 0 ) 
							found_charsets.add(enc);
					}catch(Throwable ex){
						log.d("enc=%s error=%s",enc,ex.getMessage());
					}
				}
				if(bCancelled) new Exception("キャンセルされました");

				String[] list = new String[ found_charsets.size() ];
				int n=0;
				for( String enc : found_charsets ){
					list[n++] = enc;
				}
				Arrays.sort(list,new Comparator<String>(){
					@Override public int compare(String a, String b) {
						return a.compareToIgnoreCase(b);
					}
					
				});

				encoding_list = list;
				if(list.length==1) encoding =list[0];
			}catch(Throwable ex){
				ex.printStackTrace();
				encoding = "error:"+ex.getMessage();
			}finally{
				progress_encoding.dismiss();
				// send notification
				ui_handler.post(new Runnable() {
					@Override public void run() {
						if(bCancelled) return;
						next_step();
					}
				});
			}
		}
    }
}