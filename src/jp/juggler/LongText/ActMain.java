package jp.juggler.LongText;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class ActMain extends Activity {
	Button btnChoose;
	Button btnBookmark;
	
	void initUI(){
        setContentView(R.layout.main);
        btnChoose = (Button)findViewById(R.id.btnChoose);
        btnBookmark = (Button)findViewById(R.id.btnBookmark);
        
        btnChoose.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try{
					Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
					intent.setType("text/*");
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					intent = Intent.createChooser(intent,"choose text..");
					startActivityForResult(intent,2);
				}catch(ActivityNotFoundException ex ){
					Toast toast = Toast.makeText(ActMain.this,getText(R.string.no_activity),Toast.LENGTH_SHORT);
					toast.show();			
				}
			}
		});
        btnBookmark.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try{
					Intent intent = new Intent(ActMain.this,ActBookmark.class);
					startActivityForResult(intent,1);
				}catch(ActivityNotFoundException ex ){
					Toast toast = Toast.makeText(ActMain.this,getText(R.string.no_activity),Toast.LENGTH_SHORT);
					toast.show();			
				}
			}
		});
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initUI();
    }
    
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch(requestCode){
		case 2:
			try{
				if(data!=null){
					Intent intent = new Intent(this,ActText.class);
					intent.setAction(Intent.ACTION_VIEW);
					intent.setDataAndType(data.getData(),data.getType());
					startActivity(intent);
				}
			}catch(ActivityNotFoundException ex ){
				Toast toast = Toast.makeText(this,getText(R.string.no_activity),Toast.LENGTH_SHORT);
				toast.show();
			}
			break;
		case 1:
			// ブックマークが選択された
			if( data!=null && data.getData() != null ){
				data.setComponent(new ComponentName(ActMain.this,ActText.class));
				data.setAction(Intent.ACTION_VIEW);
				startActivity(data);
			}
		}
	}
	
}