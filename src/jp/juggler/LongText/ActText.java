package jp.juggler.LongText;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;

import org.mozilla.intl.chardet.nsDetector;
import org.mozilla.intl.chardet.nsICharsetDetectionObserver;
import org.mozilla.intl.chardet.nsPSMDetector;

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
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class ActText extends Activity {
	static final LogCategory log = new LogCategory("TextViewer");

	final int file_step = 1000;
	final int cache_limit = 3;

	ListView lvTextList;
	TextListAdapter adapter;
	Handler ui_handler;
	ProgressDialog progress_encoding = null;
	File tmpdir;
	
	Intent opener;
	int task_id;

	void initUI(){
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.text_act);
        lvTextList = (ListView)findViewById(R.id.list);
        
        adapter = new TextListAdapter(this,cache);
        lvTextList.setAdapter(adapter);
        ui_handler = new Handler();
        
        lvTextList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lvTextList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos,long id) {
				int old_pos = lvTextList.getCheckedItemPosition();
				if(old_pos != ListView.INVALID_POSITION){
					lvTextList.setItemChecked (old_pos,false);
				}
				lvTextList.setItemChecked (pos,true);
			}
		});
	}

    /////////////////////////

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
	void initOpener(Intent intent){
		task_id = getTaskId();
		opener = intent;
		encoding = null;
		encoding_list = null;
		adapter.clear();
		cache.clear();
		tmpdir = new File(Environment.getExternalStorageDirectory(),String.format("tmp_LongText_%d",task_id));
		text_loader = new TextLoader();
	}
    
	@Override protected void onDestroy() {
    	log.d("onDestroy");
		super.onDestroy();
	}

	@Override protected void onRestart() {
    	log.d("onRestart");
		super.onRestart();
	}


	@Override
	protected void onStart() {
		log.d("onStart");
		super.onStart();
	}
	
	@Override
	protected void onStop() {
		log.d("onStop");
		super.onStop();
	}
	
	@Override
	protected void onResume() {
		log.d("onResume");
		super.onResume();
		start_worker();
	}

	@Override
	protected void onPause() {
    	log.d("onPause");
		super.onPause();
		stop_worker();
		if( isFinishing() ){
			log.d("isFinishing");
			// TODO delete cache files
			delete_tmp();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		log.d("onSaveInstanceState");
		stop_worker();
		if(encoding!=null && !encoding.startsWith("error:")) state.putString("encoding",encoding);
		if(encoding_list!=null && encoding_list.length>0) state.putStringArray("encoding_list",encoding_list);
		state.putInt("lno_cached",text_loader.lno_cached);
		state.putBoolean("load_complete",text_loader.bLoadComplete);
	}

	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);
		log.d("onRestoreInstanceState");
		encoding = state.getString("encoding");
		encoding_list = state.getStringArray("encoding_list");
		text_loader.lno_cached = state.getInt("lno_cached",0);
		text_loader.bLoadComplete = state.getBoolean("load_complete",false);
	}

	////////////////////////////////////////////////////////////////
	
	// 
	boolean bStart;
	String encoding = null;
	String[] encoding_list = null;
	
	void start_worker(){
		if( !bStart){
			bStart = true;
			next_step();
		}
	}
	void stop_worker(){
		if(bStart){
			bStart = false;
			if( encoding_checker != null ) encoding_checker.joinLoop(log,"encding_checker");
			text_loader.worker_stop();
		}
	}

	//////////////////////////////////////////////////////
	
	void next_step(){
		if(!bStart) return ;
		
		log.d("next_step encoding=%s list=%s",encoding,encoding_list);

		if(encoding != null ){
			text_loader.worker_start();
			return;
		}
		
		if( progress_encoding == null ){
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
		}
	}

	////////////////////////////////////////////////////
	
	void prepare_lines(int lines){
		try{
			if(!bStart) return;
			adapter.setLineCount(lines);
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}

	MyTextCache cache = new MyTextCache();
	class MyTextCache implements TextCache{
		LinkedList<BulkText> cache = new  LinkedList<BulkText>();

		void clear(){
			cache.clear();
		}

		public CharSequence getLine(int lno){
			int fno = lno / file_step;
			try{
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
		log.d("delete tmpdir %s",tmpdir.getPath());
		try{
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
	class TextLoader{
		WorkerBase worker;
		LineNumberReader reader;

		int lno_cached = 0;
		volatile boolean bLoadComplete =false;
		
		TextLoader(){
			try{
				// prepare cache directory
				if( ! tmpdir.exists() ){
					if( ! tmpdir.mkdir() ) throw new Exception("cannot create directory: "+tmpdir.getPath());
				}
				if( ! tmpdir.canWrite() ) throw new Exception("missing permission to write to "+tmpdir.getPath());
			}catch(Throwable ex){
				bLoadComplete = true;
				Toast.makeText(ActText.this,ex.getMessage(),Toast.LENGTH_SHORT).show();
			}
		}
		
		void worker_stop(){
			// stop worker thread
			if(worker!=null){
				worker.joinLoop(log,"text_loader");
				worker = null;
			}
			// close input
			try{
				if(reader!=null) reader.close();
			}catch(Throwable ex){
				ex.printStackTrace();
			}
		}

		void worker_start(){
			try{
				prepare_lines(lno_cached);
				if( bLoadComplete ) return;

				log.d("worker_start");
		        
				setProgressBarVisibility(true);
		        setProgressBarIndeterminate(true);

				// prepare input files
				Context context = getApplicationContext();
				ContentResolver cr = context.getContentResolver();
				reader = new LineNumberReader(new InputStreamReader(cr.openInputStream(opener.getData()),encoding));
				
				// prepare worker thread
				worker = new WorkerBase(){
					volatile boolean bCancelled = false;
					@Override public void cancel() {
						bCancelled = true;
						notifyEx();
					}
					public void run(){
						try{
							int lno = 0;
							while( !bCancelled && lno < lno_cached ){
								reader.readLine();
								++lno;
							}
							BulkTextBuilder builder = new BulkTextBuilder();
							while(!bCancelled){
								String line = reader.readLine();
								if(line==null) break;
								builder.add(line);
								++lno;
								if( builder.line_count == file_step ){
									builder.save(new File(tmpdir,String.format("%d",(lno-1)/file_step )));
									builder.reset();
									lno_cached = lno;
									ui_handler.post(new Runnable() {
										@Override public void run() {
											prepare_lines(lno_cached);
										}
									});
								}
							}
							// end of input
							if(!bCancelled){
								if( builder.line_count > 0){
									builder.save(new File(tmpdir,String.format("%d",(lno-1)/file_step)));
									builder.reset();
								}
								lno_cached = lno;
								bLoadComplete = true;
								ui_handler.post(new Runnable() {
									@Override public void run() {
										prepare_lines(lno_cached);
									}
								});
							}
						}catch(Throwable ex){
							ex.printStackTrace();
						}finally{
							ui_handler.post(new Runnable() {
								@Override public void run() {
									try{
								        setProgressBarVisibility(false);
								        setProgressBarIndeterminate(false);
									}catch(Throwable ex){
										ex.printStackTrace();
									}
								}
							});
							
						}
					}
				};
				worker.setPriority(Thread.MIN_PRIORITY);
				worker.start();
			} catch (Throwable ex) {
				ex.printStackTrace();
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
		String charset = null;
		
		public void run(){
			Context context = getApplicationContext();
			try{
				boolean isAscii = true ;
				nsDetector det = new nsDetector(nsPSMDetector.ALL) ;
				det.Init(new nsICharsetDetectionObserver() {
					public void Notify(String charset) {
						EncodingChecker.this.charset = charset;
					}
				});

				ContentResolver cr = context.getContentResolver();
				InputStream is = cr.openInputStream(opener.getData());
				try{
					byte[] sample = new byte[16384];
					int nRead = 0;
					while(charset==null){
						if(bCancelled) throw new Exception("キャンセルされました");
						int delta = is.read(sample);
						if(delta==-1) throw new Exception("指定されたデータはサイズ0です");
						if(isAscii){
							isAscii = det.isAscii(sample,delta);
						}
						if(!isAscii){
							if(det.DoIt(sample,delta, false)) break;
						}
						nRead += delta;
						if(nRead >= 65536) break;
					}
					det.DataEnd();
				}finally{
					is.close();
				}
				if(isAscii) charset = "ASCII";
				String[] list;
				if( charset != null ){
					list = new String[]{ charset };
				}else{
					list = det.getProbableCharsets() ;
				}
				Arrays.sort(list,new Comparator<String>(){
					@Override public int compare(String a, String b) {
						return a.compareToIgnoreCase(b);
					}
				});
				encoding_list = list;
				progress_encoding.dismiss();
				ui_handler.post(new Runnable() {
					@Override public void run() {
						if(bCancelled) return;
						if(encoding_list.length < 1 ){
							Toast.makeText(ActText.this,"解釈可能な文字エンコーディングがありません",Toast.LENGTH_SHORT).show();
						}else if(encoding_list.length ==  1 ){
     	 					encoding = encoding_list[0];
     	 					next_step();
						}else{
							// エンコーディングを選択
							AlertDialog.Builder dlg = new AlertDialog.Builder(ActText.this); 
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
						}
					}
				});
			}catch(Throwable ex){
				ex.printStackTrace();
				final String strError = ex.getMessage();
				ui_handler.post(new Runnable() {
					@Override public void run() {
						if(bCancelled) return;
						Toast.makeText(ActText.this,strError,Toast.LENGTH_SHORT).show();
						ActText.this.finish();
					}
				});
			}
		}
    }
}