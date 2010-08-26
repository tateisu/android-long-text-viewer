package jp.juggler.LongText;

import jp.juggler.util.ExceptionHandler;
import android.app.Application;

public class MyApp extends Application {
	@Override public void onCreate() {
		super.onCreate();
		ExceptionHandler.regist();
	}

}
