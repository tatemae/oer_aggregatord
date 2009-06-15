package edu.usu.cosl.aggregatord;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.Date;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

public class DBThread extends Thread {

	protected boolean bStop = false;
	protected static Logger log = Harvester.getLogger();
	private final static String sDriver = "jdbc:apache:commons:dbcp:/";
	private static String sConnectionURL = "aggregator";
	private static String sUser = "root";
	private static String sPassword = "";
	private static String sJoclFile = "tmp";
	private static String sDatabase = "aggregator";
	private static boolean bDriverLoaded = false;
	private static String sRailsEnv = "production";

	public static void loadDBDriver() throws ClassNotFoundException, IOException
	{
		// initialize the connection pool
//		Class.forName("org.postgresql.Driver");
		Class.forName("com.mysql.jdbc.Driver");
		Class.forName("org.apache.commons.dbcp.PoolingDriver");
		writeJoclFile();
		bDriverLoaded = true;
	}
	private static void writeJoclFile() throws IOException
	{
		FileWriter writer = new FileWriter("tmp.jocl");
		writer.write("<object class='org.apache.commons.dbcp.PoolableConnectionFactory' xmlns='http://apache.org/xml/xmlns/jakarta/commons/jocl'><object class='org.apache.commons.dbcp.DriverManagerConnectionFactory'>");
		writer.write("<string value='jdbc:mysql://localhost/" + sDatabase + "?user=" + sUser + "&amp;password=" + sPassword + "'/>");
		writer.write("<object class='java.util.Properties' null='true'/></object><object class='org.apache.commons.pool.impl.GenericObjectPool'><object class='org.apache.commons.pool.PoolableObjectFactory' null='true'/><int value='30'/><byte value='1'/><long value='2000'/><int value='10'/><boolean value='false'/><boolean value='false'/><long value='10000'/><int value='5'/><long value='5000'/><boolean value='true'/></object><object class='org.apache.commons.pool.impl.StackKeyedObjectPoolFactory'><int value='5'/></object><string value='SELECT 1'/><boolean value='false'/><boolean value='true'/></object>");	
	}
	public static Connection getConnection() throws ClassNotFoundException, SQLException, IOException
	{
		return getConnection(sJoclFile);
	}
	public static Connection getConnection(String sJocl) throws ClassNotFoundException, SQLException, IOException
	{
		if (!bDriverLoaded) loadDBDriver();
		return DriverManager.getConnection(sDriver + sJocl);
	}
	public static Timestamp currentTime()
	{
		return new Timestamp(new Date().getTime());
	}
	public static void getDBOptions(String sYMLFile)
	{
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(sYMLFile);
			String sLine = reader.readLine();
			while(sLine != null)
			{
				sLine = reader.readLine().trim();
				if (sLine.startsWith(sRailsEnv)) 
				{
					
				}
			}
		} 
		catch (Exception e)
		{
			Logger.error("getDBOptions ", e);
		}
	}
}
