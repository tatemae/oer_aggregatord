package edu.usu.cosl.aggregatord;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.Date;

public class DBThread extends Thread {

	protected boolean bStop = false;
	protected static Logger log = Harvester.getLogger();
	private final static String sUserConnectionURL = "jdbc:apache:commons:dbcp:/aggregatord";
	private static boolean bDriverLoaded = false;

	public static void loadDBDriver() throws ClassNotFoundException
	{
		// initialize the connection pool
		Class.forName("org.postgresql.Driver");
		Class.forName("org.apache.commons.dbcp.PoolingDriver");
		bDriverLoaded = true;
	}
	public static Connection getConnection() throws ClassNotFoundException, SQLException
	{
		if (!bDriverLoaded) loadDBDriver();
		return DriverManager.getConnection(sUserConnectionURL);
	}
	public void requestStop()
	{
		bStop = true;
	}
	public static Timestamp currentTime()
	{
		return new Timestamp(new Date().getTime());
	}
}
