package jp.juggler.LongText;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class DlgBookmark {
	static Dialog create(Activity act){
		LayoutInflater inflater = (LayoutInflater) act.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.bookmark_add,null);		
		AlertDialog.Builder builder = new AlertDialog.Builder(act);
		builder.setView(layout);
		Dialog dialog = builder.create();
		dialog.setTitle("add bookmark");
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(true);
		return dialog;
	}
	
	static class BookmarkInfo{
		Uri uri;
		int lno;
		String fname;
		CharSequence caption;
		long _id;
	}
	
	public static interface EndListener{
		public void onEnd(BookmarkInfo info,boolean bOK);
	}
	
	
	static void prepare(Dialog _dialog,BookmarkInfo _info,EndListener _listener){
		// save args for closure
		final Dialog dialog = _dialog;
		final BookmarkInfo info = _info;
		final EndListener listener = _listener;
		
		TextView tvPosInfo = (TextView)dialog.findViewById(R.id.posinfo);
		tvPosInfo.setText(String.format("%s 行%d",info.fname,info.lno));

		final EditText etCaption = (EditText)dialog.findViewById(R.id.caption);
		etCaption.setText(info.caption);
		
		((Button)dialog.findViewById(R.id.btnOK)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				info.caption = etCaption.getText().toString();
				dialog.dismiss();
				if(listener!=null) listener.onEnd(info,true);
			}
		});
		((Button)dialog.findViewById(R.id.btnCancel)).setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				dialog.dismiss();
				if(listener!=null) listener.onEnd(info,false);
			}
		});
	}
}
