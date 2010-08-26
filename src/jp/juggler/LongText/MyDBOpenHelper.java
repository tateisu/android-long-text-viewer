package jp.juggler.LongText;

import jp.juggler.util.LogCategory;
import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

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
	        
			db.beginTransaction();
			try{
				db.setTransactionSuccessful();
			}finally{
				db.endTransaction();
			}
		}
	}


}