package jp.juggler.LongText;

import jp.juggler.LongText.DlgBookmark.BookmarkInfo;
import jp.juggler.LongText.DlgBookmark.EndListener;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class ActBookmark extends ListActivity{
	MyDBOpenHelper helper;
	SQLiteDatabase ui_db;
	Cursor cur;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        // setContentView(R.layout.custom_list_activity_view);

		helper = new MyDBOpenHelper(this);
	    ui_db = helper.getWritableDatabase();
		cur = ui_db.query("bookmark",null,null,null,null,null,"ctime desc");

		setTitle("select bookmark");
		
		setListAdapter(new SimpleCursorAdapter(
				this
				,R.layout.bookmark_item
				,cur
				,new String[]{ "lno","fname","caption" }
				,new int[]{
						R.id.lno,
						R.id.fname,
						R.id.caption,
				}
		));		

		ListView lv = getListView();
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View arg1,int pos, long arg3) {
				cur.moveToPosition(pos);
				String url = cur.getString(cur.getColumnIndex("uri"));
				int lno = cur.getInt(cur.getColumnIndex("lno"));
				
				Intent intent = new Intent();
				intent.setData(Uri.parse(url));
				intent.putExtra("lno",lno+1);
				setResult(-1,intent);
				finish();
			}
		});
		
		registerForContextMenu(lv);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		cur.close();
		ui_db.close();
		helper.close();
	}
	
	//////////////////////////////////////////////////////////
	
	BookmarkInfo info;
	
	@Override public void onCreateContextMenu(ContextMenu menu, View v,ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.context_menu_bookmark, menu);

		AdapterContextMenuInfo minfo = (AdapterContextMenuInfo)menuInfo;
		if( cur.moveToPosition(minfo.position) ){
			info = new BookmarkInfo();
			info.uri = Uri.parse(cur.getString(cur.getColumnIndex("uri")));
			info.lno = cur.getInt(cur.getColumnIndex("lno"));
			info.fname = cur.getString(cur.getColumnIndex("fname"));
			info.caption = cur.getString(cur.getColumnIndex("caption"));
			info._id = cur.getLong(cur.getColumnIndex("_id"));
		}
	}
	
	static final int DIALOG_BOOKMARK_EDIT = 1;
	static final int DIALOG_BOOKMARK_DELETE = 2;
	
	@Override public boolean onContextItemSelected(MenuItem item) {
		// AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		default:
			return super.onContextItemSelected(item);

		case R.id.bookmark_edit:
			showDialog(DIALOG_BOOKMARK_EDIT);
			return true;

		case R.id.bookmark_delete:
			showDialog(DIALOG_BOOKMARK_DELETE);
			return true;
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id){
		default:
			return super.onCreateDialog(id);

		case DIALOG_BOOKMARK_EDIT:
			return DlgBookmark.create(this);
			
		case DIALOG_BOOKMARK_DELETE:
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.bookmark_delete);
				builder.setMessage("")
				       .setCancelable(true)
				       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int _id) {
				                dialog.dismiss();
				                bookmark_delete();
				           }
				       })
				       .setNegativeButton("No", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int _id) {
				                dialog.cancel();
				           }
				       });
				AlertDialog alert = builder.create();
				return alert;
			}
		}
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch(id){
		default:
			super.onPrepareDialog(id, dialog);
			return;

		case DIALOG_BOOKMARK_EDIT:
			DlgBookmark.prepare(dialog,info,new EndListener() {
				public void onEnd(BookmarkInfo info, boolean bOK) {
					if(bOK){
						ContentValues c = new ContentValues();
						c.put("caption",info.caption.toString()  );
						c.put("ctime", System.currentTimeMillis() );
						ui_db.update("bookmark",c,"_id=?",new String[]{ Long.toString(info._id)});
						cur.requery();
					}
				}
			});
			return;
		case DIALOG_BOOKMARK_DELETE:
			AlertDialog ad = (AlertDialog)dialog;
			ad.setMessage(String.format(getResources().getString(R.string.delete_warning),info.fname,info.lno));
			return;
		}
	}

	void bookmark_delete(){
		ui_db.delete("bookmark","_id=?",new String[]{ Long.toString(info._id)});
		cur.requery();
	}
}
