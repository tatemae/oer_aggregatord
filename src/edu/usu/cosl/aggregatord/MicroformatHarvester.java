package edu.usu.cosl.aggregatord;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;

import java.util.Vector;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.Calendar;
import java.util.GregorianCalendar;

import java.text.SimpleDateFormat;
import java.text.ParseException;

import java.net.URL;
//import java.net.URLConnection;
import java.net.MalformedURLException;

import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
//import java.io.FileInputStream;
import java.io.IOException;

import au.id.jericho.lib.html.*;

import edu.usu.cosl.util.DBThread;
import edu.usu.cosl.util.Logger;
import edu.usu.cosl.microformats.EventGeneric;

public class MicroformatHarvester extends DBThread 
{
	private final static String QUERY_ENTRIES_NEEDING_HARVEST = 
		"SELECT entries.id, entries.permalink, age(now(), harvested_at) FROM watched_pages " +
		"INNER JOIN entries ON watched_pages.entry_id = entries.id " +
		"WHERE feed_id = 0 AND (harvested_at IS NULL OR (age(now(), harvested_at) > interval '24:00:00' AND has_microformats = 't'))";

	// seconds to wait before polling the database for pages that need harvesting for microformats
	private static final double DEFAULT_HARVEST_INTERVAL = 20; 
	private static double dHarvestInterval = DEFAULT_HARVEST_INTERVAL;

	private final static String[] asTestPages = {
		"http://fundyfilm.ca/calendar",
//		"http://www.crosbyheritage.co.uk/events",
		"http://finetoothcog.com/site/stolen_bikes",
		"http://www.clinicalpsychologyarena.com/resources/conferences.asp",
		"http://laughingsquid.com/laughing-squid-10th-anniversary-party",
		"http://jhtc.org",
		"http://www.gore-tex.com/remote/Satellite?c=fabrics_content_c&cid=1162322807952&pagename=goretex_en_US%2Ffabrics_content_c%2FKnowWhatsInsideDetail",
		"https://www.urbanbody.com/information/contact-us",
		"http://www.newbury-college.ac.uk/home/default.asp",
		"http://07.pagesd.info/ardeche/agenda.aspx",
		"http://www.comtec-ars.com/press-releases",
		"http://austin.adactio.com/"
	};
	
	private class EntryInfo
	{
		int nEntryID;
		String sLink;
		boolean bKnownToContainMicroformats;
		public EntryInfo(int nEntryID, String sLink, boolean bKnownToContainMicroformats)
		{
			this.nEntryID = nEntryID;
			this.sLink = sLink;
			this.bKnownToContainMicroformats = bKnownToContainMicroformats;
		}
	}
	
	public MicroformatHarvester()
	{
	}

	private void harvestEntries() throws SQLException, ClassNotFoundException
	{
		// get the list of pages that need to be harvested
		Connection cn = getConnection();
		Vector<EntryInfo> vEntries = getEntriesNeedingHarvest(cn);
		
		if (vEntries.size() > 0) Logger.info("Harvesting pages for microformats: " + vEntries.size());
		
		// loop through the entries
		for (ListIterator<EntryInfo> liEntries = vEntries.listIterator(); liEntries.hasNext();)
		{
			// harvest the entry
			EntryInfo entry = liEntries.next(); 
			try
			{
				jerichoParse(cn, entry.sLink, entry.nEntryID, entry.bKnownToContainMicroformats);
			}
			catch (Exception e)
			{
				Logger.error("Error harvesting microformats from: " +  entry.sLink);
			}
		}
		cn.close();
	}
	public void run() 
	{
		try 
		{
			while(!bStop)
			{
				// harvest entries
				harvestEntries();
				
				// sleep until its time to harvest again
				if (!bStop) Thread.sleep((long)(dHarvestInterval*1000));
			}
		}
		catch (Exception e) 
		{
			Logger.error("Error in microformat harvester: " + e);
		}
	}
	
	private Vector<EntryInfo> getEntriesNeedingHarvest(Connection cn)
	{
		Statement stGetEntries = null;
		ResultSet rsEntries = null;
		try
		{
			// prepare statement for retrieving groups
			stGetEntries = cn.createStatement();
	
			// create a vector for storing the entries
			Vector<EntryInfo> vEntries = new Vector<EntryInfo>();

			// query the db for shared entries that have not been harvested
			rsEntries = stGetEntries.executeQuery(QUERY_ENTRIES_NEEDING_HARVEST);
			while (rsEntries.next())
			{
				vEntries.add(new EntryInfo(rsEntries.getInt(1), rsEntries.getString(2), rsEntries.getBoolean(3)));
			}
			// close result set, statement, and connection
			rsEntries.close();
			stGetEntries.close();
			
			return vEntries;
		}
		catch (SQLException e)
		{
			Logger.error("tcb1: " + e);
			Logger.error(e.getNextException());
			try
			{
				if (rsEntries != null) rsEntries.close();
				if (stGetEntries != null)
				{
					stGetEntries.close();
					stGetEntries = null;
				}
				if (cn != null)
				{
					cn.close();
					cn = null;
				}
			}
			catch (SQLException e2){}
			return null;
		}
	}

	public String fileName(String sUrl, int nFile)
	{
		String localFile = null;
		try
		{
			URL url = new URL(sUrl);
	
			// Get only file name
			StringTokenizer st = new StringTokenizer(url.getFile(), "/");
			while (st.hasMoreTokens())
				localFile = st.nextToken();
		}
		catch (Exception e)
		{
			Logger.error(e);
		}
		return nFile + "-" + (localFile != null && localFile.length() > 6 ? localFile.substring(0,5) : "") + ".txt";
	}

	public String copyUrl(String sUrl, String sFileName) 
	{
		StringBuffer sb = new StringBuffer();
		try 
		{
			URL url = new URL(sUrl);
//			System.out.println("Opening connection to " + sUrl + "...");
//			URLConnection urlC = url.openConnection();

			// Copy resource to local file, use remote file
			// if no local file name specified
			InputStream is = url.openStream();

			// Print info about resource
//			System.out.print("Copying resource (type: " + urlC.getContentType());
//			Date date = new Date(urlC.getLastModified());
//			Logger.info(", modified on: " + date.toLocaleString() + ")...");
//			System.out.flush();

			FileOutputStream fos = null;
			fos = new FileOutputStream("c:\\temp\\mf\\" + sFileName);

			int oneChar, count = 0;
			while ((oneChar = is.read()) != -1) 
			{
				fos.write(oneChar);
				sb.append((char)oneChar);
				count++;
			}
			is.close();
			fos.close();
//			System.out.println(count + " byte(s) copied");
		}
		catch (Exception e) 
		{
			Logger.error(e);
		}
		return sb.toString();
	}

//	private String readFile(String sFileName) throws IOException
//	{
//		FileInputStream f = new FileInputStream(sFileName);
//		int len=f.available();
//		StringBuffer s = new StringBuffer();
//
//		for(int i=1;i<=len;i++)
//		{
//			s.append((char)f.read());
//		}
//		return s.toString();
//	}
//	
//	private void displaySegments(List segments) 
//	{
//		for (Iterator i=segments.iterator(); i.hasNext();) {
//			Segment segment=(Segment)i.next();
////			System.out.println(segment.getDebugInfo());
//			System.out.println("=====================");
//			System.out.println(segment);
//		}
//	}
	
	private int getEntryID(Connection cn, String sURI) throws SQLException
	{
		PreparedStatement st = cn.prepareStatement("SELECT id FROM entries WHERE feed_id = 0 AND permalink = ?");
		st.setString(1, sURI);
		ResultSet rs = st.executeQuery();
		int nEntryID = 0;
		if (rs.next()) nEntryID = rs.getInt(1);
		else
		{
			rs.close();
			if (sURI.endsWith("/"))
			{
				st.setString(1, sURI.substring(0,sURI.length()-1));
				rs = st.executeQuery();
				if (rs.next()) nEntryID = rs.getInt(1);
			}
			else
			{
				st.setString(1, sURI + "/");
				rs = st.executeQuery();
				if (rs.next()) nEntryID = rs.getInt(1);
			}
		}
		rs.close();
		st.close();
		return nEntryID;
	}
	private Element getSubElement(Element element, String sClass)
	{
		List lChildren = element.findAllStartTags("class", sClass, false);
		Iterator iChildren = lChildren.iterator();
		if (iChildren.hasNext())
		{
			return ((StartTag)iChildren.next()).getElement();
		}
		return null;
	}

	private String getSubElementText(Element element, String sClass)
	{
		Element child = getSubElement(element, sClass);
		return child == null ? null : child.extractText();
	}

	private String getSubElementAttribute(Element element, String sClass, String sAttr)
	{
		Element child = getSubElement(element, sClass);
		return child == null ? null : child.getAttributeValue(sAttr);
	}
	
	private String getChildTagAttribute(Element element, String sTagName, String sAttr)
	{
		List lChildren = element.findAllStartTags(sTagName);
		Iterator iChildren = lChildren.iterator();
		if (iChildren.hasNext())
		{
			StartTag child = (StartTag)iChildren.next();
			return child.getAttributeValue(sAttr);
		}
		else return null;
	}

	private String parseFormattedDate(String sDate, String sParsePattern, String sFormatPattern)
	{
		try
		{
			SimpleDateFormat dateFormat = new SimpleDateFormat(sParsePattern);
			Date parsedDate = dateFormat.parse(sDate);
			Calendar cal = GregorianCalendar.getInstance();
			cal.setTime(parsedDate);
			int nHour = cal.get(Calendar.HOUR_OF_DAY);
			if (nHour == 0) dateFormat.applyPattern("yyyy-MM-dd");
			else dateFormat.applyPattern(sFormatPattern);
			return dateFormat.format(parsedDate);
		}
		catch (ParseException e){return null;}
	}
	
	private String parseDate(String sDate)
	{
		String sNormalizedDate = parseFormattedDate(sDate, "yyyyMMdd'T'HHmmssZ", "dd MMMM yyyy hh:mm a");
		if (sNormalizedDate == null ) sNormalizedDate = parseFormattedDate(sDate, "yyyyMMdd'T'HHmmZ", "dd MMMM yyyy hh:mm a");
		if (sNormalizedDate == null ) sNormalizedDate = parseFormattedDate(sDate, "yyyyMMdd'T'HHmm'Z'", "dd MMMM yyyy hh:mm a");
		if (sNormalizedDate == null ) sNormalizedDate = parseFormattedDate(sDate, "yyyyMMdd'T'HHmmss", "dd MMMM yyyy hh:mm a");
		if (sNormalizedDate == null ) sNormalizedDate = parseFormattedDate(sDate, "yyyyMMdd'T'HHmm", "dd MMMM yyyy hh:mm a");
		if (sNormalizedDate == null ) sNormalizedDate = parseFormattedDate(sDate, "yyyy-MM-dd", "yyyy-MM-dd");
		if (sNormalizedDate == null ) sNormalizedDate = parseFormattedDate(sDate, "yyyyMMddssZ", "yyyy-MM-dd");
		if (sNormalizedDate == null ) sNormalizedDate = parseFormattedDate(sDate, "yyyyMMdd", "yyyy-MM-dd");
		return (sNormalizedDate == null) ? sDate : sNormalizedDate;
	}
	
	private String getDateFromElement(Element event, String sClass)
	{
		Element date = getSubElement(event, sClass);
		if (date == null) return null;
		
		// see if a date is specified in the title attribute
		String sDate = date.getAttributeValue("title");

		// try to get the date from the enclosed text
		if (sDate == null) sDate = date.extractText();

		// try to parse it so we can convert it
		return parseDate(sDate);
	}
	
	private double jerichoParse(Connection cn, String sPage, int nEntryID, boolean bKnownToContainMicroformats) throws IOException, SQLException
	{
		// see: http://microformats.org/wiki/hcalendar-cheatsheet
		
		// jericho
		Logger.info("Harvesting for microformats: " + sPage);
		
		Timestamp time = Harvester.currentTime();
		au.id.jericho.lib.html.Source source = new Source(new URL(sPage));
		source.setLogWriter(new OutputStreamWriter(System.err));

		// get the list of all vevents
		List lEvents = source.findAllStartTags("class", "vevent", false);
//		Logger.info("Events: " + lEvents.size()); 
//		Logger.info("======================================");
		PreparedStatement stUpdateEntryInfo = cn.prepareStatement("UPDATE watched_pages SET harvested_at = ?, has_microformats = ? WHERE entry_id = ?");
		int nNewEvents = 0;
		MicroformatDBManager mdm = MicroformatDBManager.create(cn); 
		for (Iterator iEvents = lEvents.iterator(); iEvents.hasNext();) 
		{
			EventGeneric mfEvent = new EventGeneric();
			
			// event
			Element event = ((StartTag)iEvents.next()).getElement();
//			Logger.info("==================================================");

			String sSummary = getSubElementText(event, "summary");
			if (sSummary != null) 
			{
				if (sSummary.length() > 200)
				{
					Logger.error("Event name truncated: " + sSummary);
					sSummary = sSummary.substring(0,197) + "...";
				}
				mfEvent.setName(sSummary);
			}
			
//			Logger.info("Microformat summary: " + sSummary);
			
			String sDescription = getSubElementText(event, "description");
			if (sDescription != null) mfEvent.setDescription(sDescription);

			String sDuration = getSubElementText(event, "duration");
			if (sDuration != null) mfEvent.setDuration(sDuration);
			
			// end date
			String sEndDate = getDateFromElement(event, "dtend");
			
			// start date
			String sStartDate = getDateFromElement(event, "dtstart");
			if (sStartDate != null)
			{
				// if the start date is only a day number, try to use the end date to get the full date
				if (sStartDate != null && sStartDate.length() < 3 && sEndDate != null && sEndDate.length() > 3 && sEndDate.contains(" "))
				{
					int nStartDate = 0;
					try
					{
						nStartDate = Integer.parseInt(sStartDate);
					}
					catch (Exception e){}
					if (nStartDate != 0) sStartDate = nStartDate + sEndDate.substring(sEndDate.indexOf(' '), sEndDate.length());
				}
			}
			if (sStartDate != null)
			{
				mfEvent.setBegins(sStartDate); 
//				Logger.info("dtstart    : " + sStartDate);
			}
			if (sEndDate != null)
			{
				mfEvent.setEnds(sEndDate); 
//				Logger.info("dtend      : " + sEndDate);
			}

			// location
			String sLocation = null;
			Element location = getSubElement(event, "location");
			if (location != null)
			{
				sLocation = location.extractText();
				if (sLocation == null || sLocation.length() == 0) sLocation = location.getAttributeValue("title");
			}
			if (sLocation != null) mfEvent.setLocation(sLocation); // Logger.info("location   : " + sLocation);

			// url
			String sURL = getSubElementAttribute(event, "url", "href");
			if (sURL == null)
			{
				Element summary = getSubElement(event, "summary");
				if (summary != null)
				{
					sURL = getChildTagAttribute(summary, "a", "href");
				}
			}
			if (sURL != null && !sURL.startsWith("http"))
			{
				try
				{
					if (sURL.startsWith("/")) sURL = "http://" + new URL(sPage).getHost() + sURL;
					else if (!sURL.startsWith("http://")) sURL = sPage + sURL;
				}
				catch (MalformedURLException e){sURL = sPage + sURL;}
			}
			if (sURL != null) mfEvent.addLink(sURL, ""); //Logger.info("url        : " + sURL);
			try
			{
				if (mdm.addEventToDB(mfEvent, nEntryID))
				{
						nNewEvents++;
				}
			}
			catch (Exception e)
			{
				Logger.error("Error adding event to database.");
				Logger.error(mfEvent.toString());
				Logger.error(e);
			}
		}
		mdm.close();
		stUpdateEntryInfo.setTimestamp(1, Harvester.currentTime());
		stUpdateEntryInfo.setBoolean(2, bKnownToContainMicroformats ? true : lEvents.size() > 0);
		stUpdateEntryInfo.setInt(3, nEntryID);
		stUpdateEntryInfo.execute();
		if (nNewEvents > 0) Logger.info("Harvested events from " + sPage + ": " + nNewEvents);
		return Harvester.secondsSince(time);
	}

	public static void main(String[] args) 
	{
		try
		{
			Logger.setLogToConsole(true);
			Logger.setConsoleLogLevel(10);
			MicroformatHarvester mfHarvester = new MicroformatHarvester();
			Connection cn = getConnection();
			for (int nPage = 0; nPage < asTestPages.length; nPage++)
			{
//				Logger.info("======================================");
//				Logger.info("Processing: " + asPages[nPage]);
//				String sData = harvester.copyUrl(asPages[nPage],harvester.fileName(asPages[nPage],nPage));
//				String sData = readFile("c:\\temp\\mf\\1.html");
//				String sData = readFile("c:\\temp\\mf\\" + fileName(asPages[nPage],nPage));

				// jericho
//				jerichoParse(sData, asPages[nPage]);
				int nEntryID = mfHarvester.getEntryID(cn, asTestPages[nPage]);
				nEntryID = 1;
				mfHarvester.jerichoParse(cn, asTestPages[nPage], nEntryID, false);
			}
			cn.close();
		}
		catch (Exception e)
		{
			Logger.error(e);
		}
	}

}
