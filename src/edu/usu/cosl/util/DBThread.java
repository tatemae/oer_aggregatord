package edu.usu.cosl.util;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Timestamp;

import java.util.Properties;
import java.util.Date;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;

import org.apache.commons.dbcp.ConnectionFactory; 
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDriver;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.pool.impl.StackKeyedObjectPoolFactory;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.SimpleLayout;

public class DBThread extends Thread {

	protected boolean bStop = false;
	static public Logger logger = Logger.getLogger(DBThread.class);
	
	private static String sUser = "root";
	private static String sPassword = "";

	private static String sDatabase;
	private static String sDBConnectionPrefix = "jdbc:mysql://localhost/";
	private static String sDBConnection;

	private static String sPool = "aggregator";
	private static String sJDBCConnection = "jdbc:apache:commons:dbcp:" + sPool;
	
	private static boolean bDriverLoaded = false;
	protected static String sRailsEnv = "development";

	public static void loadDBDriver() throws ClassNotFoundException
	{
		// initialize the connection pool
//		Class.forName("org.postgresql.Driver");
		Class.forName("com.mysql.jdbc.Driver");
		Class.forName("org.apache.commons.dbcp.PoolingDriver");
		
		GenericObjectPool connectionPool = new GenericObjectPool(null);
		ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(sDBConnection, sUser, sPassword);
		StackKeyedObjectPoolFactory pstPoolFactory = new StackKeyedObjectPoolFactory(20);
		final String sValidationQuery = null;
		PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory,connectionPool,pstPoolFactory,sValidationQuery,false,true);
		PoolingDriver driver = new PoolingDriver();
		driver.registerPool(sPool,connectionPool);
		bDriverLoaded = true;
	}
	public static Connection getConnection() throws ClassNotFoundException, SQLException
	{
		if (!bDriverLoaded) loadDBDriver();
		return DriverManager.getConnection(sJDBCConnection);
	}
	public static Timestamp currentTime()
	{
		return new Timestamp(new Date().getTime());
	}
	public static Properties loadPropertyFile(String sFile) throws Exception
	{
	    try 
	    {
	    	Properties properties = new Properties();
	    	FileInputStream in = new FileInputStream(sFile);
	        properties.load(in);
	        in.close();
	        return properties;
	    } catch (Exception e) {
	    	logger.error("Unable to load properties file: " + sFile, e);
	    	throw e;
	    }
	}
	public static void getLoggerAndDBOptions(String sFile) throws Exception
	{
		getLoggerAndDBOptions(loadPropertyFile(sFile));
	}
	public static void getLoggerAndDBOptions(Properties properties) throws Exception
	{
		PropertyConfigurator.configure(properties);
		
        String sValue = properties.getProperty("db_yml");
        String sDBConfigFile = (sValue == null) ? "config/database.yml" : sValue.trim();
        sDBConfigFile = System.getProperty("RAILS_DB_CONFIG", sDBConfigFile).trim();

        sValue = properties.getProperty("rails_env");
        if (sValue != null) sRailsEnv = sValue;
        sRailsEnv = System.getProperty("RAILS_ENV", sRailsEnv);
        
		try
		{
			logger.info("Looking in " + sDBConfigFile + " for " + sRailsEnv + " database settings");
			BufferedReader reader = new BufferedReader(new FileReader(sDBConfigFile));
			String sLine = reader.readLine();
			while(sLine != null)
			{
				sLine = sLine.trim();
				if (sLine.startsWith(sRailsEnv)) 
				{
					sLine = reader.readLine();
					while(sLine != null && sLine.startsWith(" "))
					{
						sLine = sLine.trim();
						if (sLine.startsWith("database:")) {
							sDatabase = sLine.substring(9).trim();
							sDBConnection = sDBConnectionPrefix + sDatabase;
						}
						else if (sLine.startsWith("username:")) 
							sUser = sLine.substring(10).trim();
						else if (sLine.startsWith("password:")) 
							sPassword = sLine.substring(9).trim();
						sLine = reader.readLine();
					}
					break;
				}
				sLine = reader.readLine();
			}
			if (sDatabase == null) {
				throw new Exception("A database was not specified");
			}
		} 
		catch (Exception e)
		{
			logger.error("Unable to load database configuration file", e);
			throw e;
		}
	}
	static private File getPidFile() {
		return new File(System.getenv("daemon.pidfile"));
	}

	static private Thread mainThread;
	
	static private void daemonize() {
		mainThread = Thread.currentThread();
		getPidFile().deleteOnExit();
		System.out.close();
		System.err.close();
	}

	static private boolean shutdownRequested = false;

	static private void addDaemonShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				DBThread.shutdown();
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
			addDaemonShutdownHook();
		} catch (InterruptedException e) {
			logger.error("Interrupted which waiting on main daemon thread to complete.");
		}
	}

	static public boolean isShutdownRequested() {
		return shutdownRequested;
	}

	protected static void startup() {
		Appender startupAppender = new ConsoleAppender(new SimpleLayout(),"System.err");
		try {
			logger.addAppender(startupAppender);
			// do sanity checks and startup actions
			daemonize();
		} catch (Throwable e) {
			logger.fatal("Startup failed.", e);
		} finally {
			logger.removeAppender(startupAppender);
		}
	}

}
