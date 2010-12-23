package jp.juggler.util;

import java.lang.Thread.UncaughtExceptionHandler;

public class ExceptionHandler implements Thread.UncaughtExceptionHandler {
	static final LogCategory log = new LogCategory("ExceptionHandler");
	static UncaughtExceptionHandler orig_handler;
	public void uncaughtException(Thread th, Throwable ex) {
		ex.printStackTrace();
		orig_handler.uncaughtException(th,ex);
	}
	public static void regist(){
		UncaughtExceptionHandler cur_handler = Thread.getDefaultUncaughtExceptionHandler();
		if( cur_handler instanceof ExceptionHandler ) return;
		orig_handler = cur_handler;
		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
		log.d("setDefaultUncaughtExceptionHandler");
	}
}
