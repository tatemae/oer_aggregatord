package edu.usu.cosl.util;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

public class Daemon {
	
	static public Logger logger = Logger.getLogger(Daemon.class);

	static private Thread mainThread;
	
	static private void daemonize() {
		mainThread = Thread.currentThread();
		if ("true".equals(System.getenv("recommender.log_to_console"))) {
			System.out.close();
			System.err.close();
		}
	}

	static private boolean shutdownRequested = false;

	static private void addDaemonShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				org.apache.log4j.LogManager.shutdown();
				Daemon.shutdown();
			}
		});
	}
	
	static private Thread getMainDaemonThread() {
		return mainThread;
	}

	static private void shutdown() {
		shutdownRequested = true;
		try {
			getMainDaemonThread().join();
		} catch (InterruptedException e) {
			logger.debug("Interrupted while waiting on main daemon thread to complete.");
		}
	}

	static public boolean isShutdownRequested() {
		return shutdownRequested;
	}

	protected static boolean startup() {
		Appender startupAppender = new ConsoleAppender(new SimpleLayout(),"System.err");
		try {
			logger.addAppender(startupAppender);
			daemonize();
			addDaemonShutdownHook();
			return true;
		} catch (Throwable e) {
			logger.fatal("Startup failed.", e);
		} finally {
			logger.removeAppender(startupAppender);
		}
		return false;
	}
}
