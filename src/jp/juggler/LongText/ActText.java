package jp.juggler.LongText;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;

import org.mozilla.intl.chardet.nsDetector;
import org.mozilla.intl.chardet.nsICharsetDetectionObserver;
import org.mozilla.intl.chardet.nsPSMDetector;

import jp.juggler.LongText.BulkText.Line;
import jp.juggler.LongText.DlgBookmark.BookmarkInfo;
import jp.juggler.LongText.DlgBookmark.EndListener;
import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.ClipboardManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class ActText extends Activity {
	static final LogCategory log = new LogCategory("TextViewer");

	final int file_step = 1000;
	final int cache_limit = 3;

	ListView lvTextList;
	TextListAdapter adapter;
	Handler ui_handler;
	ProgressDialog progress_encoding = null;
	ProgressBar pbLoading;
	File tmpdir;
	TextView tvNaviLine;
	MyDBOpenHelper helper;
	SQLiteDatabase ui_db;

	Intent opener;
	int task_id;

	void initUI(){
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.text_act);
        lvTextList = (ListView)findViewById(R.id.list);
        pbLoading = (ProgressBar)findViewById(R.id.progress);
        tvNaviLine = (TextView)findViewById(R.id.navi);

        adapter = new TextListAdapter(this,text_cache);
        lvTextList.setAdapter(adapter);
        ui_handler = new Handler();
        
        lvTextList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        lvTextList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos,long id) {
				
				{
					int old_pos = lvTextList.getCheckedItemPosition();
					if(old_pos != ListView.INVALID_POSITION){
						lvTextList.setItemChecked (old_pos,false);
					}
					lvTextList.setItemChecked (pos,true);
					lno_select = pos+1;
					update_navi();
				}
			}
		});
        lvTextList.setOnScrollListener(new OnScrollListener() {
			
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
			}
			
			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,int visibleItemCount, int totalItemCount){
				lno_visible = 1+firstVisibleItem;
				update_navi();
			}
		});
        
        tvNaviLine.setTextColor(0x80ffffff);
        
        registerForContextMenu(lvTextList);
	}
	
	int lno_visible;
	int lno_select;
	void update_navi(){
		tvNaviLine.setText(String.format("選択行=%d,スクロール行=%d",lno_select,lno_visible));
	}

    /////////////////////////

	@Override public void onCreate(Bundle savedInstanceState) {
    	log.d("onCreate");
        super.onCreate(savedInstanceState);
        initUI();
        helper = new MyDBOpenHelper(this);
        ui_db = helper.getWritableDatabase();

        initOpener(getIntent());
        // onRestoreInstanceState(savedInstanceState);
    }

	@Override protected void onNewIntent(Intent intent) {
    	log.d("onNewIntent");
        initOpener(intent);
	}

	@Override protected void onDestroy() {
    	log.d("onDestroy");
		super.onDestroy();
		ui_db.close();
		helper.close();
	}

	void initOpener(Intent intent){
		task_id = getTaskId();
		opener = intent;
		encoding = null;
		encoding_list = null;
		adapter.clear();
		text_cache.clear();

		try{
			if(tmpdir!=null) MyDBOpenHelper.delete_tmpdir(this,ui_db,tmpdir);
			tmpdir = MyDBOpenHelper.make_tmpdir(this,ui_db);
			MyDBOpenHelper.sweep_tmpdir(this,ui_db,tmpdir.getParentFile());
		}catch(Throwable ex){
			ex.printStackTrace();
			Toast.makeText(this,ex.getMessage(),Toast.LENGTH_SHORT).show();
			tmpdir = null;
			finish();
			return;
		}
		text_loader = new TextLoader();
		loaded_line = 0;
		lno_moveto = Integer.MIN_VALUE;
		int lno = intent.getIntExtra("lno",1);
		moveto(lno);
	}
    

	@Override protected void onRestart() {
    	log.d("onRestart");
		super.onRestart();
	}


	boolean bStart = false;
	@Override
	protected void onStart() {
		log.d("onStart");
		super.onStart();
		bStart = true;
	}
	
	@Override
	protected void onStop() {
		log.d("onStop");
		super.onStop();
		bStart = false;
	}
	
	@Override
	protected void onResume() {
		log.d("onResume");
		super.onResume();
		start_worker();
		moveto(Integer.MIN_VALUE);
	}

	@Override
	protected void onPause() {
    	log.d("onPause");
		super.onPause();
		stop_worker();
		if( isFinishing() ){
			log.d("isFinishing");
			MyDBOpenHelper.delete_tmpdir(this,ui_db,tmpdir);
			tmpdir = null;
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
	// context menu

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		log.d("view=%s",v);
		// select line
		{
			AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
			int pos = info.position;
			int old_pos = lvTextList.getCheckedItemPosition();
			if(old_pos != ListView.INVALID_POSITION){
				lvTextList.setItemChecked (old_pos,false);
			}
			lvTextList.setItemChecked (pos,true);
			lno_select = pos+1;
			update_navi();
		}
		// open menu
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		int pos = info.position;
		switch (item.getItemId()) {
		default:
			return super.onContextItemSelected(item);

		case R.id.bookmark_add:
			showDialog(DIALOG_BOOKMARK_ADD);
			return true;
		case R.id.copyline_1: copyline(pos,1); return true;
		case R.id.copyline_5: copyline(pos,5); return true;
		case R.id.copyline_10: copyline(pos,10); return true;
		case R.id.copyline_20: copyline(pos,20); return true;
		}
	}
	
	void copyline(int start,int length){
		StringBuffer sb = new StringBuffer();
		BulkText.Line line = new Line();
		for(int i=0;i<length;++i){
			if(start+i >= loaded_line) break;
			if(i>0) sb.append("\n");
			text_cache.loadLine(line, start+i);
			sb.append(line);
		}
		if(sb.length()>0){
			ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
			cm.setText(sb.toString());
		}
	}

	static final int DIALOG_BOOKMARK_ADD = 0;
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id){
		default:
			return super.onCreateDialog(id);
		case DIALOG_BOOKMARK_ADD:
			return DlgBookmark.create(this);
				
		}
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch(id){
		default:
			super.onPrepareDialog(id, dialog);
			return;

		case DIALOG_BOOKMARK_ADD:
			BookmarkInfo info = new BookmarkInfo();
			info.uri = opener.getData();
			info.lno = lno_select;
			info.fname = fname;
			BulkText.Line line = new Line();
			if( lno_select <=0 ){
				info.caption = "";
			}else{
				text_cache.loadLine(line,lno_select-1);
				if( line.length()>100 ){
					info.caption = line.subSequence(0,100)+"…"; 
				}else{
					info.caption = line.toString();
				}
			}

			DlgBookmark.prepare(dialog,info,new EndListener() {
				@Override
				public void onEnd(BookmarkInfo info, boolean bOK) {
					if(bOK){
						ContentValues c = new ContentValues();
						c.put("uri", info.uri.toString() );
						c.put("lno", info.lno);
						c.put("fname", info.fname );
						c.put("caption",info.caption.toString()  );
						c.put("ctime", System.currentTimeMillis() );
						ui_db.insert("bookmark", null,c );
					}
				}
			});
			

			return;
		}

	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.text_options, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    default:
	        return super.onOptionsItemSelected(item);

	    case R.id.bookmark:
	    	Intent intent = new Intent(this,ActBookmark.class);
	    	startActivityForResult(intent,1);
	        return true;
	    }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode){
		default:
			super.onActivityResult(requestCode, resultCode, data);
			return;
		case 1:
			// ブックマークが選択された
			if( data!=null && data.getData() != null ){
				if( data.getDataString().equals(opener.getDataString()) ){
					int lno = data.getIntExtra("lno",1);
					log.d("jump to in-page bookmark: %d",lno);
					moveto(lno-1);
				}else{
					data.setComponent(new ComponentName(ActText.this,ActText.class));
					data.setAction(Intent.ACTION_VIEW);
					finish();
					startActivity(data);
				}
			}
			return;
		}
	}


	////////////////////////////////////////////////////////////////


	// 
	boolean bStartWorker;
	String encoding = null;
	String[] encoding_list = null;
	
	void start_worker(){
		if( !bStartWorker){
			bStartWorker = true;
			next_step();
		}
	}
	void stop_worker(){
		if(bStartWorker){
			bStartWorker = false;
			if( encoding_checker != null ) encoding_checker.joinLoop(log,"encding_checker");
			text_loader.worker_stop();
		}
	}

	//////////////////////////////////////////////////////
	
	void next_step(){
		if(!bStartWorker) return ;
		
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
	int loaded_line = 0;
	int lno_moveto = Integer.MIN_VALUE;
	void moveto(int lno){
		if(lno != Integer.MIN_VALUE ) lno_moveto = lno;
		if( lno_moveto == Integer.MIN_VALUE ){
		//	log.d("not pending move");
		}else if( ! bStart ){
			log.d("lno=%d ,but activity is not started.",lno_moveto);
		}else if( lno_moveto >=  loaded_line ){
		//	log.d("lno=%d is not loaded now.",lno_moveto);
		}else{
			int n = (lvTextList.getLastVisiblePosition() - lvTextList.getFirstVisiblePosition())/4;
			if(n<3) n=3;
			n = lno_moveto - n; 
			if(n<0) n=0;
			log.d("select lno=%d",n);
			lvTextList.setSelection(n);
			lno_moveto = Integer.MIN_VALUE;
		}
	}
	void prepare_lines(int lines){
		if(!bStartWorker) return;
		loaded_line = lines;
		adapter.setLineCount(lines);
		moveto(Integer.MIN_VALUE);
	}

	MyTextCache text_cache = new MyTextCache();
	class MyTextCache implements TextCache{
		LinkedList<BulkText> cache = new  LinkedList<BulkText>();

		void clear(){
			cache.clear();
		}

		@Override
		public void loadLine(Line line, int lno) {
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
					bulk.loadLine(line,lno - fno * file_step);
					return;
				}
				++i;
			}
			while( cache.size() >= cache_limit ){
				cache.removeLast();
			}
			BulkText bulk = new BulkText(fno,new File(tmpdir,String.format("%d",fno)));
			cache.addFirst(bulk);
			bulk.loadLine(line,lno - fno * file_step);  
		}
	};
	

	///////////////////////////////////////////////////////////////
	// テキストを一時ファイルに保存
	
	String fname;
	
	TextLoader text_loader = null;
	class TextLoader{
		WorkerBase worker;
		LineNumberReader reader;

		int lno_cached = 0;
		volatile boolean bLoadComplete =false;
		
		TextLoader(){
			if( !tmpdir.canWrite() ){
				bLoadComplete = true;
				pbLoading.setVisibility(View.GONE);
				Toast.makeText(ActText.this,"cannot write to tmpdir",Toast.LENGTH_SHORT).show();
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
			}catch(IOException ex){
				ex.printStackTrace();
			}
		}

		void worker_start(){
			prepare_lines(lno_cached);
			if( bLoadComplete ){
				pbLoading.setVisibility(View.GONE);
				return;
			}

			log.d("worker_start");
	        
			setProgressBarVisibility(true);
	        setProgressBarIndeterminate(true);

	        Uri uri = opener.getData();
	        fname = uri.getLastPathSegment();
	        
			// prepare input files
			Context context = getApplicationContext();
			ContentResolver cr = context.getContentResolver();
			try{
				reader = new LineNumberReader(new InputStreamReader(cr.openInputStream(opener.getData()),encoding));
			}catch(IOException ex){
				final String errstr = ex.getMessage();
				bLoadComplete = true;
				ui_handler.post(new Runnable() {
					@Override
					public void run() {
						pbLoading.setVisibility(View.GONE);
						Toast.makeText(ActText.this,errstr,Toast.LENGTH_SHORT).show();
					}
				});
				return;
			}
			
			// prepare worker thread
			worker = new WorkerBase(){
				volatile boolean bCancelled = false;
				@Override
				public void cancel() {
					bCancelled = true;
					notifyEx();
				}
				@Override
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
									@Override
									public void run() {
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
								@Override
								public void run() {
									prepare_lines(lno_cached);
									pbLoading.setVisibility(View.GONE);
								}
							});
						}
					}catch(IOException ex){
						ex.printStackTrace();
					}finally{
						ui_handler.post(new Runnable() {
							@Override
							public void run() {
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
		
		@Override
		public void run(){
			Context context = getApplicationContext();
			try{
				boolean isAscii = true ;
				nsDetector det = new nsDetector(nsPSMDetector.ALL) ;
				det.Init(new nsICharsetDetectionObserver() {
					@Override
					public void Notify(String _charset) {
						EncodingChecker.this.charset = _charset;
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
						if(delta==-1) break;
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
					@Override
					public int compare(String a, String b) {
						return a.compareToIgnoreCase(b);
					}
				});
				encoding_list = list;
				progress_encoding.dismiss();
				ui_handler.post(new Runnable() {
					@Override
					public void run() {
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
			     	 			@Override
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
			}catch(Exception ex){
				ex.printStackTrace();
				final String strError = ex.getMessage();
				ui_handler.post(new Runnable() {
					@Override
					public void run() {
						if(bCancelled) return;
						Toast.makeText(ActText.this,strError,Toast.LENGTH_SHORT).show();
						ActText.this.finish();
					}
				});
			}
		}
    }
    /*
	void hoge(){
		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.bookmark_select,(ViewGroup) findViewById(R.id.root));
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(layout);
		Dialog dialog = builder.create();
		dialog.setTitle("select bookmark");
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(true);
		final Cursor cur = ui_db.query("bookmark",null,null,null,null,null,"ctime desc");
		ListView lvBookmark = (ListView)dialog.findViewById(R.id.list);
		final SimpleCursorAdapter adapter = new SimpleCursorAdapter(
				this
				,R.layout.bookmark_item
				,cur
				,new String[]{ "lno","fname","caption" }
				,new int[]{
						R.id.lno,
						R.id.fname,
						R.id.caption,
				}
		);
		lvBookmark.setAdapter(adapter);
		lvBookmark.setOnItemClickListener(new OnItemClickListener() {
			@Override public void onItemClick(AdapterView<?> arg0, View arg1,int pos, long arg3) {
				cur.moveToPosition(pos);
				String url = cur.getString(cur.getColumnIndex("uri"));
				int lno = cur.getInt(cur.getColumnIndex("lno"));
				log.d("url=%s,%s",url,opener.getDataString());
				if( url .equals(opener.getDataString()) ){
					moveto(lno-1);
				}else{
					Intent intent = new Intent(ActText.this,ActText.class);
					intent.setAction(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(url));
					intent.putExtra("lno",lno+1);
					finish();
					startActivity(intent);
				}
				dismissDialog(DIALOG_BOOKMARK_SELECT);
			}
		});
	}
*/
}