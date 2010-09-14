package jp.juggler.LongText;

import java.io.File;
import java.util.Random;

import jp.juggler.util.LogCategory;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.os.Process;
import android.util.SparseBooleanArray;

public class MyDBOpenHelper extends SQLiteOpenHelper {
	static final LogCategory log=new LogCategory("LT_DBOpenHelper");
    static final int DATABASE_VERSION = 2;
    static final String DATABASE_NAME ="text_viewer";
    Activity context;
    
    public MyDBOpenHelper(Activity context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

	@Override
	public void onCreate(SQLiteDatabase db) {

	}
	@Override
	public void onUpgrade(SQLiteDatabase db, int v_old, int v_new) {
	}
	

	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
		
		if(!db.isReadOnly()){

			// create table
	        db.execSQL("create table if not exists bookmark("
        		+"_id integer primary key AUTOINCREMENT"
        		+",uri text"
        		+",lno int"
        		+",fname text"
        		+",caption text"
        		+",ctime int"
	        +")");
	        
			// create table
	        db.execSQL("create table if not exists tmpdir("
        		+"_id integer primary key AUTOINCREMENT"
        		+",pid integer"
        		+",tmpdir text unique not null"
        		+",ctime int"
	        +")");
	        
	        
	        
			db.beginTransaction();
			try{
				db.setTransactionSuccessful();
			}finally{
				db.endTransaction();
			}
		}
	}
	
	static void sweep_tmpdir(Context context,SQLiteDatabase db,File tmpdir){
		db.beginTransaction();
		try{
			// check alives processes
			SparseBooleanArray  live_pid = new SparseBooleanArray();
			ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
			StringBuilder sb = new StringBuilder();
			for( RunningAppProcessInfo info : am.getRunningAppProcesses() ){
				live_pid.put(info.pid,true);
				if(sb.length()>0) sb.append(',');
				sb.append(Integer.toString(info.pid));
			}
			// reveme old entry not running 
			String where;
			if(live_pid.size()>0){
				where = "pid not in ("+sb.toString()+")";
			}else{
				where = null;
			}
			db.delete("tmpdir",where,null);

			// clean tmpdir
			if( tmpdir != null && tmpdir.exists() ){
				for( String entry : tmpdir.list() ){
					if(entry.startsWith(".")) continue;
					File subdir = new File(tmpdir,entry);
					boolean is_live = false;
					Cursor c = db.query("tmpdir",null,"tmpdir=?",new String[]{ subdir.getPath() },null,null,null);
					is_live = c.moveToFirst();
					c.close();
					if(is_live) continue;
					log.d("delete %s",subdir);
					delete_dir(subdir);
				}
			}
			db.setTransactionSuccessful();
		}finally{
			db.endTransaction();
		}
	}
	private static boolean delete_dir(File path) {
		if(path.exists()){
			if( path.isDirectory()){
				for(String sub : path.list()){
					if(sub.equals(".") || sub.equals("..") ) continue;
					if(!delete_dir(new File(path,sub))) return false;
				}
			}
			if(!path.delete()){
				log.d("cannot delete %s",path.getPath());
				return false;
			}
		}
		return true;
	}

	static void delete_tmpdir(Context context,SQLiteDatabase db,File tmpdir){
		db.beginTransaction();
		try{
			db.delete("tmpdir","tmpdir=?",new String[]{ tmpdir.getPath() });
			delete_dir(tmpdir);
			db.setTransactionSuccessful();
		}finally{
			db.endTransaction();
		}
	}

	static File make_tmpdir(Context context,SQLiteDatabase db) throws Exception {
		
		File extdir = Environment.getExternalStorageDirectory();
		File tmp_top = new File(extdir,"tmp_LongText");
		if( ! tmp_top.exists() ){
			if( ! tmp_top.mkdir() ) throw new Exception("cannot create directory: "+tmp_top.getPath());
		}
		if( ! tmp_top.canWrite() ) throw new Exception("missing permission to write to "+tmp_top.getPath());
		
		File tmpdir;
		Random r = new Random();
		do{
			tmpdir = new File(tmp_top,String.format("%d",r.nextInt()));
		}while( tmpdir.exists() );
		if( ! tmpdir.mkdir() ) throw new Exception("cannot create directory: "+tmp_top.getPath());
		if( ! tmpdir.canWrite() ) throw new Exception("missing permission to write to "+tmp_top.getPath());
		ContentValues v = new ContentValues();
		v.put("pid"		,Process.myPid() );
		v.put("tmpdir"	,tmpdir.getPath());
		v.put("ctime"	,System.currentTimeMillis());
		db.insert("tmpdir",null,v);

		return tmpdir;
	}
	
}

