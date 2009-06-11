package edu.usu.cosl.aggregatord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Vector;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Enumeration;

import java.text.SimpleDateFormat;

import java.util.concurrent.BlockingQueue;

import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;

import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndCategory;

import com.sun.syndication.feed.module.DCModule;
import com.sun.syndication.feed.module.DCSubject;
import com.sun.syndication.feed.module.Module;

import edu.usu.cosl.syndication.feed.module.DCTermsModule;
import edu.usu.cosl.syndication.io.impl.MarkupProperty;

import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

import del.icio.us.Delicious;
import del.icio.us.beans.Post;

import au.id.jericho.lib.html.*;

import edu.usu.cosl.microformats.EventGeneric;
import edu.usu.cosl.microformats.Microformat;
import edu.usu.cosl.syndication.feed.module.content.ContentModule;

import be.cenorm.www.MerlotHarvester;

public class Harvester extends DBThread 
{
	public final static String HARVESTER_USER_AGENT = "Folksemantic Harvester v0.2";
	
	// connection and statement for querying for stale feeds
	private static Connection cnFeeds;
	private static Statement stGetStaleFeeds;
	private static final String QUERY_FEEDS =
		"SELECT feeds.id, feeds.service_id, feeds.title, feeds.uri, feeds.priority, feeds.login, feeds.password, " +
		"services.api_uri, feeds.last_harvested_at, feeds.failed_requests, feeds.harvest_interval, feeds.display_uri, feeds.short_title, tag_filter, default_language_id " +
		"FROM feeds LEFT OUTER JOIN services ON (feeds.service_id = services.id) ";
	private static final String STALE_FEEDS_CONDITION =
		"WHERE failed_requests < 10 AND feeds.id != 0 AND feeds.status >= 0 ";
	private static final String QUERY_STALE_FEEDS = 
		QUERY_FEEDS + STALE_FEEDS_CONDITION + "ORDER BY feeds.priority";

	// query for nuking broken feeds
	// DELETE FROM feeds WHERE feeds.failed_requests = 10 AND feeds.id IN (SELECT f.id FROM (SELECT feeds.id, COUNT(entries.id) FROM feeds LEFT JOIN entries ON entries.feed_id = feeds.id GROUP BY feeds.id, feeds.title, feeds.uri ORDER BY count) AS f WHERE f.count = 0)
	
	private Timestamp startTime;
	
	private int nNewEntries;
	private int nUpdatedEntries;
	private int nDeletedEntries;
	private int nNewEntrySubjects;
	static private int nTotalNewEntries;
	static private int nTotalUpdatedEntries;
	static private int nTotalDeletedEntries;
	static private int nMaxEntries = 1000000;
	
	private Connection cnWorker;
	private PreparedStatement pstFindDuplicateEntries;
	private PreparedStatement pstFindDuplicateOAIEntries;
	private PreparedStatement pstFlagEntryDeleted;
	private PreparedStatement pstGetServiceURI;

	private PreparedStatement pstAddFeed;
	private PreparedStatement pstAddEntry;
	private PreparedStatement pstUpdateEntry;
	private PreparedStatement pstAddEntryImage;

	private PreparedStatement pstGetSubjectID;
	private PreparedStatement pstAddSubject;
	private PreparedStatement pstAddEntrySubject;
	
	private PreparedStatement pstUpdateFeedInfo;
	
	private Statement stNextID;
	
	public static final int DEFAULT_FEED_HARVEST_INTERVAL = 86400; // 24 hours (24*60*60 seconds)
	public static int nFeedHarvestInterval = DEFAULT_FEED_HARVEST_INTERVAL;
	
	public static final int DEFAULT_CONNECTION_TIMEOUT = 180; // seconds
	public static int nConnectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
	
	private static boolean bImportArchivedFeeds = true;
	private static boolean bHarvestFromWire = true;
	private String sArchivePath = "../feed_archive";
	
	private static Logger log = new Logger();
	
	BlockingQueue<FeedInfo> toDoQueue;
	BlockingQueue<String> activeJobsQueue;
	
	private HashSet<String> hsStopWords = new HashSet<String>();
	
    static private Hashtable<String, Integer> htLanguages = new Hashtable<String, Integer>();
    static private int nDefaultLanguageID;
	
	private SimpleDateFormat sdf;
	
	private boolean bTalkToDB = true;
	
	class EntryInfo
	{
		int nEntryID;
		Timestamp date;
	}
	
	public Harvester(BlockingQueue<FeedInfo> toDoQueue, BlockingQueue<String> activeJobsQueue)
	{
		this.toDoQueue = toDoQueue;
		this.activeJobsQueue = activeJobsQueue;
		final String[] asStopWords = {"and","is","a","the","in","--","for","on","to","of","are","with","other"};
		for (int nWord = 0; nWord < asStopWords.length; nWord++)
		{
			hsStopWords.add(asStopWords[nWord]);
		}
		sdf = new SimpleDateFormat();
//		sdf.applyPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
		sdf.applyPattern("yyyy-MM-dd");
	}

	public static Logger getLogger()
	{
		return log;
	}
	public Connection initDBConnection() throws SQLException, ClassNotFoundException
	{
		cnWorker = getConnection();
		
        pstFindDuplicateEntries = cnWorker.prepareStatement("SELECT id, published_at FROM entries WHERE feed_id = ? AND permalink = ?");
        pstFindDuplicateOAIEntries = cnWorker.prepareStatement("SELECT id, published_at FROM entries WHERE oai_identifier = ?");
        pstFlagEntryDeleted = cnWorker.prepareStatement("UPDATE entries SET oai_identifier = 'deleted' WHERE id = ?"); 
        
		pstGetServiceURI = cnWorker.prepareStatement("SELECT api_uri FROM services WHERE id = ?");

		pstAddFeed = cnWorker.prepareStatement("INSERT INTO feeds (id, uri, title, harvest_interval, last_harvested_at, created_at, updated_at, service_id) VALUES (?,?,?,?,?,?,?,?)");
		pstAddEntry = cnWorker.prepareStatement("INSERT INTO entries (feed_id, permalink, author, title, description, content, unique_content, published_at, entry_updated_at, oai_identifier, language_id, harvested_at, direct_link) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)");
		pstUpdateEntry = cnWorker.prepareStatement("UPDATE entries SET feed_id = ?, permalink = ?, author = ?, title = ?, description = ?, content = ?, unique_content = ?, published_at = ?, entry_updated_at = ?, oai_identifier = ?, language_id = ?, harvested_at = ?, direct_link = ? WHERE id = ?");
		pstAddEntryImage = cnWorker.prepareStatement("INSERT INTO entry_images (entry_id, uri, link, alt, title, width, height) VALUES (?,?,?,?,?,?,?)");
		pstGetSubjectID = cnWorker.prepareStatement("SELECT id FROM subjects WHERE name = ?");
		pstAddSubject = cnWorker.prepareStatement("INSERT INTO subjects (name) VALUES (?)");
		pstAddEntrySubject = cnWorker.prepareStatement("INSERT INTO entries_subjects (subject_id, entry_id, autogenerated) VALUES (?, ?, false)");

		pstUpdateFeedInfo = cnWorker.prepareStatement("UPDATE feeds SET last_harvested_at = ?, priority = ?, failed_requests = 0, last_requested_at = ?, error_message = NULL, display_uri = ?, description = ?, title = ? WHERE id = ?");

		stNextID = cnWorker.createStatement();

		return cnWorker;
	}
	
	public void closeDBConnection()
	{
		int nLoc = 0;
		try
		{
			if (pstFindDuplicateEntries != null) pstFindDuplicateEntries.close();
			if (pstFindDuplicateOAIEntries != null) pstFindDuplicateOAIEntries.close();
			if (pstFlagEntryDeleted != null) pstFlagEntryDeleted.close();
			nLoc = 1;
			if (pstGetServiceURI != null) pstGetServiceURI.close();
			nLoc = 2;
			if (pstGetSubjectID != null) pstGetSubjectID.close();
			nLoc = 3;
			
			if (pstAddFeed != null) pstAddFeed.close();
			nLoc = 4;
			if (pstAddEntry != null) pstAddEntry.close();
			if (pstUpdateEntry != null) pstUpdateEntry.close();
			nLoc = 5;
			if (pstAddEntryImage != null) pstAddEntryImage.close();
			nLoc = 6;
			if (pstAddSubject != null) pstAddSubject.close();
			nLoc = 7;
			if (pstAddEntrySubject != null) pstAddEntrySubject.close();
			nLoc = 8;

			if (pstUpdateFeedInfo != null) pstUpdateFeedInfo.close();
			nLoc = 9;
	
			if (stNextID != null) stNextID.close();
			nLoc = 10;
			
			if (cnWorker != null) cnWorker.close();
			nLoc = 11;
		}
		catch (SQLException e)
		{
			Logger.error("closeDBConnection (" + nLoc + "): ", e);
		}
		pstFindDuplicateEntries = null;
		pstFindDuplicateOAIEntries = null;
		pstFlagEntryDeleted = null;
		pstGetServiceURI = null;
		pstGetSubjectID = null;
		pstAddFeed = null;
		pstAddEntry = null;
		pstUpdateEntry = null;
		pstAddEntryImage = null;
		pstAddSubject = null;
		pstAddEntrySubject = null;
		pstUpdateFeedInfo = null;
		stNextID = null;
		cnWorker = null;
	}
	
	public boolean harvestFeed(FeedInfo feedInfo)
	{
		Logger.info("Harvesting: " + feedInfo.sTitle/* + " (" + feedInfo.sURI + ")" */);

		// track the start time
		startTime = currentTime();

		nNewEntries = 0;
		nUpdatedEntries = 0;
		nDeletedEntries = 0;
		nNewEntrySubjects = 0;
		switch (feedInfo.nServiceID)
		{
			case FeedInfo.SERVICE_DELICIOUS:
			{
				harvestDeliciousFeed(feedInfo);
				break;
			}
			case FeedInfo.SERVICE_FLICKR:
			{
				harvestFlickrFeed(feedInfo);
				break;
			}
			case FeedInfo.SERVICE_MERLOT:
			{
				harvestMerlotFeed(feedInfo);
				break;
			}
			default:
			{
				harvestRSSFeed(feedInfo);
				break;
			}
		}
		boolean bChangesMade = (nNewEntries > 0 ||  nUpdatedEntries > 0 || nDeletedEntries > 0); 
        if (bChangesMade)
        {
            nTotalNewEntries += nNewEntries;
            nTotalUpdatedEntries += nUpdatedEntries;
            nTotalDeletedEntries += nDeletedEntries;
        	Logger.status(feedInfo.sTitle + " harvested (new, updated, deleted): " + nNewEntries + ", " + nUpdatedEntries + ", " + nDeletedEntries + " in " + secondsSinceStr(startTime) + " seconds");
        }
		// remove the feed from the active queue
        if (activeJobsQueue != null) activeJobsQueue.remove(feedInfo.sURI);
        return bChangesMade;
	}
	
	private String getForeignMarkupValue(SyndEntry entry, String sMarkupName)
	{
		String sValue = null;
		
		// the parser stores the OAI identifier in foreign markup
		List lMarkup = (List)entry.getForeignMarkup();
		if (lMarkup != null)
		{
			for (int nProperty = 0; nProperty < lMarkup.size(); nProperty++)
			{
				Object item = lMarkup.get(nProperty);
				if (item instanceof MarkupProperty)
				{
					MarkupProperty mp = (MarkupProperty)item;
					String sName = mp.getName();
					if (sName.equals(sMarkupName))
					{
						sValue = (String)mp.getValue();
						break;
					}
				}
			}
		}
		return sValue == null || sValue.length() == 0 ? null : sValue;
	}
	
	private void harvestMerlotFeed(FeedInfo feedInfo)
	{
		try
		{
			MerlotHarvester merlotHarvester = new MerlotHarvester();
			
            // initialize the database connection and statements
            initDBConnection();
            
            // loop through the entries
    		Timestamp currentTime = currentTime();
    		final String[] asSubjects = new String[]{};
            while (merlotHarvester.hasMoreEntries()&& nNewEntries < nMaxEntries)
            {
            	// get info about the entry
            	SyndEntry entry = merlotHarvester.next();
            	int nLanguageID=getLanguageID(null,entry,feedInfo);
            	// if the entry doesn't already exist in the database
            	if (getEntry(feedInfo.nFeedID, entry.getUri()) == null)
            	{
            		// keep track of the number of new entries
            		nNewEntries++;
            		String sOAIIdentifier = getForeignMarkupValue(entry, "oai_identifier");
            		addOrUpdateRSSEntry(pstAddEntry, feedInfo, entry, null, null, entry.getLink(), asSubjects, currentTime, sOAIIdentifier, 0,nLanguageID);
            	}
            }
    		// update the feed last updated and priority
    		updateFeedInfo(feedInfo);

    		// close the db statements and connections
			closeDBConnection();
		}
		catch (SQLException e) 
		{
			Logger.error("harvestMerlotFeed-1: ", e);
			handleFailedRequest(feedInfo);
		}
		catch (Throwable t)
		{
			Logger.error("harvestMerlotFeed-2: " + t);
			handleFailedRequest(feedInfo);
		}
	}

	
	private int addDeliciousEntry(FeedInfo feedInfo, Post entry) throws SQLException
	{
//		feed_id, permalink, author, title, description, content, unique_content,  
//		published_at, entry_updated_at, oai_identifier, language_id, harvested_at, direct_link		
		
		pstAddEntry.setInt(1, feedInfo.nFeedID);

		pstAddEntry.setString(2, entry.getHref());

		// author
		pstAddEntry.setString(3, null);
		pstAddEntry.setString(4, null);
		
		// description
		String sDescription = entry.getDescription();
		pstAddEntry.setString(5, sDescription == null ? "" : sDescription);

		// content
		pstAddEntry.setString(6, "");
		
		// unique_content
		pstAddEntry.setBoolean(7, false);

		// published_at
		Timestamp currentTime = currentTime();
		Timestamp publishedTime; 
		Date publishedDate = entry.getTimeAsDate();
		if (publishedDate == null) publishedTime = currentTime;
		else publishedTime = new Timestamp(publishedDate.getTime());
		pstAddEntry.setTimestamp(8, publishedTime);
		
		// updated_at
		pstAddEntry.setTimestamp(9, publishedTime);

		// oai_identifier
		pstAddEntry.setString(10, null);
		
		// language id
		pstAddEntry.setInt(11, nDefaultLanguageID);
		
		// harvested_at
		pstAddEntry.setTimestamp(12, currentTime());
		
		// direct_link
		pstAddEntry.setString(13, null);

		pstAddEntry.executeUpdate();
		return getLastID(pstAddEntry);
	}
	
	private void harvestDeliciousFeed(FeedInfo feedInfo)
	{
        if(feedInfo.sServiceUser.length() <= 0 && feedInfo.sServicePassword.length() <= 0)
        {
          // we won't be able to authenticate without a username and password.  It is likely this feed is
          // just rss so send it to the standard harvestor.
          harvestRSSFeed(feedInfo);
        }
        
		try
		{
			// get all the posts from the users account
			// TODO: Add a constructor to the delicious API that takes a user-agent 
			Delicious delicious = new Delicious(feedInfo.sServiceUser,feedInfo.sServicePassword,feedInfo.sServiceURI);
			
			try
			{
				Date lastUpdate = delicious.getLastUpdate();

				// if the feed hasn't been updated since we last requested it, bail now
				if (lastUpdate == null)
				{
					Logger.error("Failed request for delicious feed last update time, HttpResponseCode = " + delicious.getHttpResult());
					handleFailedRequest(feedInfo);
				}
				else if (feedInfo.lLastHarvested > lastUpdate.getTime())
				{
					Logger.info("Delicious feed hasn't been updated since it was last requested.");
					noChanges(feedInfo);
				}
			}
			catch (Exception e)
			{
				handleFailedRequest(feedInfo);
			}
            // initialize the database connection and statements
            initDBConnection();
            
            // add the new feed to the db
	        if (feedInfo.nFeedID == FeedInfo.FEED_ID_UNKNOWN)
	        {
	        	feedInfo.sTitle = feedInfo.sServiceUser + "'s Delicious Bookmarks";
	        	addFeed(feedInfo);
	        }
	        // if no display uri, add it now
	        if (feedInfo.sDisplayURI == null || feedInfo.sDisplayURI.length() == 0) feedInfo.sDisplayURI = "http://del.icio.us/" + feedInfo.sServiceUser;
	        
            // loop through the entries
	        List lEntries = delicious.getAllPosts();
        	Post entry = null;
            for (ListIterator entries = lEntries.listIterator(); entries.hasNext();)
            {
            	// get info about the entry
            	entry = (Post)entries.next();
            	
            	// if the entry doesn't already exist in the database
            	// TODO: Add support for updating entries
            	if (getEntry(feedInfo.nFeedID, entry.getHref()/*, null*/) == null)
            	{
            		// keep track of the number of new entries
            		nNewEntries++;
            		
            		// add the entry now (as part of a batch update)
            		int nEntryID = addDeliciousEntry(feedInfo,entry);
//            		Logger.info("Adding tags for entry: " + entry.getTag());
            		addSubjectsForEntry(nEntryID,entry.getTagsAsArray(" "));
            	}
            }
    		// update the feed last updated and priorty
    		updateFeedInfo(feedInfo);

    		// close the db statements and connections
			closeDBConnection();
		}
		catch (SQLException e) 
		{
			Logger.error("harvestDeliciousFeed-1: ", e);
			handleFailedRequest(feedInfo);
		}
		catch (Throwable t)
		{
			Logger.error("harvestDeliciousFeed-2: " + t);
			handleFailedRequest(feedInfo);
		}
	}
	private void handleFailedRequest(FeedInfo feedInfo)
	{
//		Logger.info("Error occured retrieving " + feedInfo.sURI + "\n" + e);
		Logger.info((feedInfo.nFailedRequests + 1) + " harvest failures for: " + feedInfo.sTitle);

		// how many minutes to wait before requesting again, 
		final int[] anFailedRequestIntervals = {1,5,30,60,6*60,24*60,24*60,24*60,24*60,24*60};

		PreparedStatement stUpdateFeedInfoForFailure = null;
		try
		{
			if (cnWorker == null) cnWorker = getConnection();
			stUpdateFeedInfoForFailure = cnWorker.prepareStatement("UPDATE feeds SET failed_requests = ?, last_requested_at = ?, error_message = ? WHERE id = ?");
			
			// set the last requested time so that the next request will occur in the # of minutes specified above 
			Timestamp lastRequested = timeMinutesFromNow(anFailedRequestIntervals[feedInfo.nFailedRequests] - feedInfo.nRefreshInterval);
			stUpdateFeedInfoForFailure.setInt(1,(feedInfo.nFailedRequests + 1));
			stUpdateFeedInfoForFailure.setTimestamp(2,lastRequested);
			stUpdateFeedInfoForFailure.setString(3,feedInfo.sErrorMessage);
			stUpdateFeedInfoForFailure.setInt(4,feedInfo.nFeedID);
			stUpdateFeedInfoForFailure.executeUpdate();
			stUpdateFeedInfoForFailure.close();
			stUpdateFeedInfoForFailure = null;
		}
		catch (Exception e)
		{
			Logger.error("handleFailedRequest: ", e);
			try
			{
				if (stUpdateFeedInfoForFailure != null)
				{
					stUpdateFeedInfoForFailure.close();
					stUpdateFeedInfoForFailure = null;
				}
			}
			catch (Exception e2){}
		}
		closeDBConnection();
	}
	
	private String getFlickrId(String sUserName)
	{
		try
		{
			final String sApiKey = "34ac1c70fe4ee52a3f62e75c4c36fed6";
			com.aetrion.flickr.Flickr flickr = new com.aetrion.flickr.Flickr(sApiKey);
			com.aetrion.flickr.people.PeopleInterface pi = flickr.getPeopleInterface();
			com.aetrion.flickr.people.User user = null;
			try {user = pi.findByUsername(sUserName);}catch(Exception e){}
			if (user == null) try {user = pi.findByUsername(sUserName + "@yahoo.com");}catch(Exception e){}
			if (user == null) try {user = pi.findByEmail(sUserName);}catch(Exception e){}
			if (user == null) try {user = pi.findByEmail(sUserName + "@yahoo.com");}catch(Exception e){}
			if (user == null) try {user = pi.getInfo(sUserName);}catch(Exception e){}
			if (user == null && sUserName.indexOf('@') != -1) try {user = pi.findByUsername(sUserName.substring(0,sUserName.indexOf('@')));}catch(Exception e){}
			
			return user == null ? null : user.getId();
		}
		catch (Exception e)
		{
			Logger.error("getFlickrId: " + e);
			return null;
		}
	}
	
	private void harvestFlickrFeed(FeedInfo feedInfo)
	{	
		if ("".equals(feedInfo.sDisplayURI))
		{
			feedInfo.sURI = "http://api.flickr.com/services/feeds/photos_public.gne?id=" + getFlickrId(feedInfo.sServiceUser);
			try
			{
				if (cnWorker == null) cnWorker = getConnection();
				PreparedStatement stUpdateFeedURI = cnWorker.prepareStatement("UPDATE feeds SET uri = ? WHERE id = ?");
				stUpdateFeedURI.setString(1, feedInfo.sURI);
				stUpdateFeedURI.setInt(2, feedInfo.nFeedID);
				stUpdateFeedURI.executeUpdate();
				stUpdateFeedURI.close();
				stUpdateFeedURI = null;
			}
			catch (Exception e)
			{
				Logger.error("harvestFlickrFeed: " + e);
			}
		}
		harvestRSSFeed(feedInfo);
	}

	private String millisToUTC(long lMillis)
	{
    	return sdf.format(new Date(lMillis));
	}
	
	private boolean saveUrlToFile(File saveFile, HttpURLConnection uriConn)
	{
    	boolean bData = false;
        try 
        {
        	InputStreamReader in = new InputStreamReader(uriConn.getInputStream(), "UTF8");
  			char[] data = new char[1024];
            int len = in.read(data);
            if (len > 0)
            {
            	bData = true;
            	FileOutputStream writer = new FileOutputStream(saveFile);
            	final byte[] utf8_bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
   		        writer.write(utf8_bom);
            	OutputStreamWriter out = new OutputStreamWriter(writer, "UTF8");

				while(len >= 0)
				{
					out.write(data, 0, len);
					len = in.read(data);
				}
	            out.flush();
	            out.close();
            }
			in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bData;
	}
	
	private String getFeedArchiveDir(FeedInfo feedInfo)
	{
		return sArchivePath + File.separatorChar + feedInfo.sURI.replaceAll("\\W", "-");
	}
	
	private SyndFeed getSyndFeed(FeedInfo feedInfo) throws SQLException
	{
		// first time through
		if (feedInfo.sFeedURI == null)
		{
			feedInfo.sFeedURI = feedInfo.sURI;

			// if it is an OAI feed, we specify the data range in the query string
	    	if (feedInfo.nServiceID == FeedInfo.SERVICE_OAI 
	    			&& feedInfo.nFailedRequests == 0 
	    			&& feedInfo.sFeedURI.indexOf("&from") == -1 
	    			&& feedInfo.sFeedURI.contains("/"))
	    	{
	    		feedInfo.sFeedURI += "&from=" + millisToUTC(feedInfo.lLastHarvested);
	    	}
		}
		// a ROME feed representing the RSS
        SyndFeed feed = null;
    	HttpURLConnection uriConn = null;
        try
		{
        	// create a connection
        	Logger.info("Requesting: " + feedInfo.sFeedURI);
        	feedInfo.lLastRequested = new Date().getTime();
        	uriConn = (HttpURLConnection)new URL(feedInfo.sFeedURI).openConnection();
        	
        	// set a timeout value just in case it takes forever
        	uriConn.setConnectTimeout(nConnectionTimeout*1000);
        	
			// we've harvested it before, so we specify the Last-Modified date
        	if (feedInfo.nFeedID != FeedInfo.FEED_ID_UNKNOWN && feedInfo.nFailedRequests == 0) 
        		uriConn.setIfModifiedSince(feedInfo.lLastHarvested);
        	
        	// let servers know who we are
			uriConn.setRequestProperty("User-agent", HARVESTER_USER_AGENT);
			
			// build an archive file path
			File fArchiveDir = new File(getFeedArchiveDir(feedInfo));
			if (!fArchiveDir.exists()) fArchiveDir.mkdirs();
			File fArchive = new File(fArchiveDir.getPath() + File.separatorChar + feedInfo.lLastRequested + ".xml");
			
			// save the content to disk
			if (saveUrlToFile(fArchive, uriConn))
			{
				// read the feed from disk
				feed = new SyndFeedInput().build(new XmlReader(fArchive));
			} else {
				fArchive.delete();
				return null;
			}
        }
		catch (com.sun.syndication.io.ParsingFeedException e)
		{
			Logger.error("Failed to parse: " + feedInfo.sFeedURI + " " + e);
			feedInfo.sErrorMessage = e.toString();
			handleFailedRequest(feedInfo);
		}
		catch (Exception e)
		{
			if (feed == null)
			{
				int nResponseCode = HttpURLConnection.HTTP_BAD_REQUEST;
				try {
					if (uriConn != null) 
						nResponseCode = uriConn.getResponseCode();
				}
				catch (IOException ie) {}

				// one possible cause of failure is that it hasn't been modified since we last requested it
				if (nResponseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
					Logger.info( "Feed hasn't been updated: " + feedInfo.sTitle/* + " (" + feedInfo.sURI + ")"*/);
				}
				else
				{
    				Logger.error("Failed request for: " + feedInfo.sFeedURI + " (" + nResponseCode + ") " + e);
    				feedInfo.sErrorMessage = "(" + nResponseCode + ") - " + e;
    				handleFailedRequest(feedInfo);
				}
        		// update the feed last updated and priority
	            if (feedInfo.nFeedID != FeedInfo.FEED_ID_UNKNOWN) 
	            	noChanges(feedInfo);
			}
            return null;
        }
		if (feedInfo.nServiceID == FeedInfo.SERVICE_OAI)
		{
            // when requesting an OAI feed, the parser stores the resumption token in foreign markup 
			Object lMarkup = feed.getForeignMarkup();
			if (lMarkup != null)
			{
				if (lMarkup instanceof List  && ((List)lMarkup).size() > 0)
				{
					Object item = ((List)lMarkup).get(0);
					if (item instanceof String)
					{
						feedInfo.sResumptionToken = (String)item;
						if (feedInfo.sResumptionToken.length() == 0) 
							feedInfo.sResumptionToken = null;
					}
				}
			}
			else feedInfo.sResumptionToken = null;
		}
		return feed;
	}
	
	private void importArchivedFeedData(FeedInfo feedInfo) throws Exception
	{
		File fArchiveDir = new File(getFeedArchiveDir(feedInfo));
		if (!fArchiveDir.exists()) return;
		File[] afArchives = fArchiveDir.listFiles();
		if (afArchives.length > 0) {
			Logger.info("importArchivedFeedData");
			for (int nFile = 0; nFile < afArchives.length; nFile++)
			{
				File fArchive = afArchives[nFile];
				String sFile = fArchive.getName();
				String sDate = sFile.substring(0,sFile.length() - 4);
				Long lDate = Long.parseLong(sDate);
				if (lDate > feedInfo.lLastHarvested)
				{
					try{
						feedInfo.lLastHarvested = lDate;
						harvestRomeFeed(feedInfo, new SyndFeedInput().build(new XmlReader(fArchive)));
					} catch (Exception e) {
						Logger.error("importArchivedFeeds-error: ", e);
					}
				}
			}
		}
	}
	
	private void harvestRSSFeed(FeedInfo feedInfo)
	{
		try 
		{
			if (bImportArchivedFeeds) {
				importArchivedFeedData(feedInfo);
			}
			if (!bHarvestFromWire) return;
	        do
	        {
	        	SyndFeed feed = getSyndFeed(feedInfo);
	        	if (feed == null) return;
	        	
	            // harvest the feed and keep track of the number of new entries
	            harvestRomeFeed(feedInfo, feed);
	            
	            // a resumption token means we requested an OAI feed got back partial results
	            if (feedInfo.sResumptionToken != null)
	            {
	            	feedInfo.sFeedURI = feedInfo.sFeedURI.substring(0, feedInfo.sFeedURI.indexOf('&')) + "&resumptionToken=" + URLEncoder.encode(feedInfo.sResumptionToken, "UTF-8");
	            	Logger.info("New OAI entries: " + nNewEntries + " - " + feedInfo.sTitle);
	            }
	        } while (feedInfo.sResumptionToken != null && nNewEntries < nMaxEntries);
		}
		catch (Throwable t)
		{
			Logger.error("harvestRSSFeed-2: " + t);
			handleFailedRequest(feedInfo);
		}
	}
	
	private boolean entryTagsOverlapFilterTags(String[] asTags, HashSet<String> hsFilterTags)
	{
		if (hsFilterTags == null) return true;
		if (asTags == null) return false;
		for (int nTag = 0; nTag < asTags.length; nTag++)
		{
			if (hsFilterTags.contains(asTags[nTag])) return true;
		}
		return false;
	}

	private String normalizeUrl(String sUrl)
	{
		if (sUrl == null) return sUrl;
		if (sUrl.endsWith("index.html")) return sUrl.substring(0,sUrl.length() - 10);
		if (sUrl.endsWith("index.aspx")) return sUrl.substring(0,sUrl.length() - 10);
		if (sUrl.endsWith("index.shtm")) return sUrl.substring(0,sUrl.length() - 10);
		if (sUrl.endsWith("index.htm")) return sUrl.substring(0,sUrl.length() - 9);
		if (sUrl.endsWith("index.asp")) return sUrl.substring(0,sUrl.length() - 9);
		if (sUrl.endsWith("index.php")) return sUrl.substring(0,sUrl.length() - 9);
		if (sUrl.endsWith("index.cfm")) return sUrl.substring(0,sUrl.length() - 9);
		if (sUrl.endsWith("index.jsp")) return sUrl.substring(0,sUrl.length() - 9);
		if (sUrl.endsWith("index.shtml")) return sUrl.substring(0,sUrl.length() - 11);
		if (sUrl.endsWith("index.jhtml")) return sUrl.substring(0,sUrl.length() - 11);
		return sUrl;
	}
	private int getLanguageID(DCModule dcm, SyndEntry entry, FeedInfo feedInfo)
	{
		int nLanguageID;
		String sLanguage = dcm.getLanguage();
		if (sLanguage == null) sLanguage = getForeignMarkupValue(entry, "language");
		if (sLanguage == null) {
			nLanguageID=feedInfo.nDefaultLanguageID;
		} 
		else if(htLanguages.containsKey(sLanguage.substring(0,2).toLowerCase()))
		{
			nLanguageID=htLanguages.get(sLanguage.substring(0,2).toLowerCase()).intValue();
		}
		else
		{
			nLanguageID=0;
		}
		return nLanguageID;
	}
	private void harvestRomeFeed(FeedInfo feedInfo, SyndFeed feed)
	{
		try
		{
			if (bTalkToDB) 
			{
	            // initialize the database connection and statements
	            initDBConnection();
	            
	            // add the new feed to the db if we've never harvested it before (we are running a test case standalone)
	            if (feedInfo.nFeedID == FeedInfo.FEED_ID_UNKNOWN) addFeed(feedInfo);
			}
            // if the title is empty, get it from the feed
            //if (feedInfo.sTitle == null || feedInfo.sTitle.length() == 0) 
            // Always set the feed title to what the feed says it is
            if (feed.getTitle() != null) feedInfo.sTitle = feed.getTitle();
            
            // address to view the RSS (HTML)
            if (feedInfo.sDisplayURI == null || feedInfo.sDisplayURI.length() == 0) feedInfo.sDisplayURI = feed.getLink();
            if (feedInfo.sDisplayURI == null || feedInfo.sDisplayURI.length() == 0) feedInfo.sDisplayURI = feedInfo.sURI;
            feedInfo.sDisplayURI = feedInfo.sDisplayURI.trim(); 
            
            // if the description is null, make it the title
            feedInfo.sDescription = feed.getDescription();
            if (feedInfo.sDescription == null) feedInfo.sDescription = feedInfo.sTitle == null ? feedInfo.sDisplayURI : feedInfo.sTitle;
            if (feedInfo.sDescription == null) feedInfo.sDescription = ""; 
            
            // get the list of tags to filter on
            HashSet<String> hsFilterTags = null;
            if (feedInfo.sTagFilter != null)
            {
    			String[] aFilterTags = feedInfo.sTagFilter.split(" ");
    			hsFilterTags = new HashSet<String>(aFilterTags.length);
    			for (int nTag = 0; nTag < aFilterTags.length; nTag++)
    			{
    				String sTag = aFilterTags[nTag].toLowerCase();
    				if (!hsFilterTags.contains(sTag)) hsFilterTags.add(sTag);
    			}
            }
            // loop through the entries
            List lEntries = feed.getEntries();
        	SyndEntry entry = null;
            for (ListIterator entries = lEntries.listIterator(); entries.hasNext() && nNewEntries < nMaxEntries;)
            {
            	// get info about the entry
            	entry = (SyndEntry)entries.next();
            	
            	// get the dublin core module metadata for the entry 
            	DCModule dcm = (DCModule)entry.getModule("http://purl.org/dc/elements/1.1/");
            	DCTermsModule dctm = (DCTermsModule)entry.getModule("http://purl.org/dc/terms/");
            	
            	int nLanguageID=getLanguageID(dcm,entry,feedInfo);
             	if(nLanguageID!=0)
             	{
	            	// get the permalink for the entry
	            	String sPermalink = entry.getLink();
	            	if (sPermalink == null)
	            	{
	            		// sometimes getLink returns null when it shouldn't
	            		sPermalink = entry.getUri();
	            		if (sPermalink == null)
	            		{
	            			if (dcm != null) sPermalink = dcm.getIdentifier();
	                		if (sPermalink == null) sPermalink = "";
	            		}
	            	}
	            	sPermalink = normalizeUrl(sPermalink);
	            	
	        		// if no published date is provided, we use the current time
	        		Timestamp updatedTime; 
	        		Date updatedDate = entry.getUpdatedDate();
	        		if (updatedDate == null) updatedDate = entry.getPublishedDate();
	        		Timestamp currentTime = currentTime();
	        		if (updatedDate == null && dctm != null) updatedDate = dctm.getModifiedDate();
	        		if (updatedDate == null || updatedDate.after(currentTime)) updatedTime = currentTime;
	        		else updatedTime = new Timestamp(updatedDate.getTime());
	        		
	            	// assume the entry doesn't already exist in the database
	        		EntryInfo existingEntry; 
	
	        		// if we are parsing OAI, check for an OAI identifier
	        		String sOAIIdentifier = null;
	        		if (feedInfo.nServiceID == FeedInfo.SERVICE_OAI)
	        		{
	            		// the parser stores the OAI identifier in foreign markup
	        			sOAIIdentifier = getForeignMarkupValue(entry, "oai_identifier");
	        			String sStatus = getForeignMarkupValue(entry, "status");
	        			
	        			// get the ID of the entry using its oai identifier
	        			existingEntry = getOAIEntryID(sOAIIdentifier); 
	        			
	        			// metadata says the record is deleted
	        			if ("deleted".equals(sStatus))
	        			{
	            			// the entry exists but now has been deleted
	        				if (existingEntry != null)
	        				{
		        				pstFlagEntryDeleted.setInt(1, existingEntry.nEntryID);
		        				pstFlagEntryDeleted.executeUpdate();
		        				nDeletedEntries++;
	        				}
	        				continue;
	        			}
	        		}
	        		// try to get an existing entry based on the feed and the permalink
	        		else existingEntry = getEntry(feedInfo.nFeedID, sPermalink/*, updatedTime*/);
	        		
	        		// get subjects for the entry
	        		String[] asSubjects=getRSSSubjects(entry, dcm);

	        		// entry with the same permalink doesn't already exist in the database
	        		if (existingEntry == null)
	            	{
	            		if (entryTagsOverlapFilterTags(asSubjects, hsFilterTags))
	            		{
		            		try
		            		{
				            	addOrUpdateRSSEntry(pstAddEntry, feedInfo, entry, dcm, dctm, sPermalink, asSubjects, updatedTime, sOAIIdentifier, 0,nLanguageID);
			            		nNewEntries++;
		            		}
			            	catch (Exception e)
			            	{
			            		Logger.error("Error adding entry");
			            		Logger.error(entry.toString());
			            		Logger.error(e);
			            	}
	            		}
	            	}
	        		// there is already an entry in the db with the same permalink 
	        		else if (existingEntry.date.before(entry.getPublishedDate()))
	        		{
	        			addOrUpdateRSSEntry(pstUpdateEntry, feedInfo, entry, dcm, dctm, sPermalink, asSubjects, updatedTime, sOAIIdentifier, existingEntry.nEntryID,nLanguageID);
	        			nUpdatedEntries++;
	        		}
             	}
    		}
            // if the last entry doesn't have a published date none of them do 
            if (entry != null && entry.getPublishedDate() == null)
			{
				Logger.warn("No published dates in " + feedInfo.sTitle);
			}
    		// update the feed last updated and priority
    		updateFeedInfo(feedInfo);
    		
   			if (nNewEntrySubjects > 0) pstAddEntrySubject.executeBatch();

    		// close the db statements and connections
			closeDBConnection();
		}
		catch (SQLException e) 
		{ 
			Logger.error("harvestRomeFeed-1: ", e);
			handleFailedRequest(feedInfo);
		}
		catch (Throwable t)
		{
			Logger.error("harvestRomeFeed-2: " + t);
			handleFailedRequest(feedInfo);
		}
	}
	
	private void extractImagesFromEntry(int nEntryID, String sDescription)
	{
		try
		{
			Source source = new Source(sDescription);
			source.setLogWriter(new OutputStreamWriter(System.err));
	
			// all images are in the specified entry
			pstAddEntryImage.setInt(1, nEntryID);
	
			// get the list of all images
			List lImageTags = source.findAllStartTags("img");
			for (Iterator iImageTags = lImageTags.iterator(); iImageTags.hasNext();)
			{
				Element imageTag = ((StartTag)iImageTags.next()).getElement();
				
				String alt = imageTag.getAttributeValue("alt") == null ? "" : imageTag.getAttributeValue("alt");
				alt = alt.length() >= 255 ? alt.substring(0, 255) : alt;
				
				String title = imageTag.getAttributeValue("title") == null ? "" : imageTag.getAttributeValue("title");
				title = title.length() >= 255 ? title.substring(0, 255) : alt;
				
				String link = imageTag.getAttributeValue("src") == null ? "" : imageTag.getAttributeValue("src");
				
				pstAddEntryImage.setString(2, link);
				pstAddEntryImage.setString(4, alt);
				pstAddEntryImage.setString(5, title);
				pstAddEntryImage.setInt(6, imageTag.getAttributeValue("width") == null ? 0 : Integer.parseInt(imageTag.getAttributeValue("width")));
				pstAddEntryImage.setInt(7, imageTag.getAttributeValue("height") == null ? 0 : Integer.parseInt(imageTag.getAttributeValue("height")));
				
				// get the href off any enclosing link
				String sEnclosedLink = "";
				StartTag hrefStartTag = source.findPreviousStartTag(imageTag.getBegin(), "a");
				if (hrefStartTag != null)
				{
					EndTag hrefEndTag = source.findPreviousEndTag(imageTag.getBegin(), "a");
					if (hrefEndTag == null || (hrefEndTag.getBegin() < hrefStartTag.getBegin()))
					{
						sEnclosedLink = hrefStartTag.getAttributeValue("href");
					}
				}
				pstAddEntryImage.setString(3, sEnclosedLink);
				pstAddEntryImage.addBatch();
			}
			
			pstAddEntryImage.executeBatch();
			
		}
		catch (SQLException e)
		{
			Logger.error("extractImagesFromEntry: ", e);
		}
	}

	private void parseEncodedContent(SyndEntry entry, int nEntryID) throws SQLException
	{
		Module m = entry.getModule("http://purl.org/rss/1.0/modules/content/");
		if (m instanceof ContentModule)
		{
	    	ContentModule cm = (ContentModule)m;
	    	if (cm != null)
	    	{
	    		MicroformatDBManager mdm = MicroformatDBManager.create(cnWorker); 
	    		for (ListIterator li = cm.getMicroformats().listIterator(); li.hasNext();)
	    		{
	    			Microformat mf = (Microformat)li.next();
	    			if (mf instanceof EventGeneric) mdm.addEventToDB((EventGeneric)mf, nEntryID);
	    		}
	    		mdm.close();
	    	}
		}
	}

	private void noChanges(FeedInfo feedInfo) throws SQLException
	{
		try
		{
	        initDBConnection();
			updateFeedInfo(feedInfo);
			closeDBConnection();
		} catch (ClassNotFoundException e) {
			Logger.error("noChanges-error: ", e);
		}
	}
	
	private String fixMITEntryTitle(String sTitle)
	{
		// get the course identifier
		int nFirstSpace = sTitle.indexOf(' ');
		String sIdentifier = sTitle.substring(0, nFirstSpace);
		
		// get the actual title
		int nSemester = sTitle.indexOf("Fall");
		if (nSemester == -1) nSemester = sTitle.indexOf("Spring ");
		if (nSemester == -1) nSemester = sTitle.indexOf("Summer ");
		if (nSemester == -1) nSemester = sTitle.indexOf("January ");
		if (nSemester == -1) nSemester = sTitle.indexOf("February ");
		if (nSemester == -1) nSemester = sTitle.indexOf("March ");
		if (nSemester == -1) nSemester = sTitle.indexOf("April ");
		if (nSemester == -1) nSemester = sTitle.indexOf("May ");
		if (nSemester == -1) nSemester = sTitle.indexOf("June ");
		if (nSemester == -1) nSemester = sTitle.indexOf("July ");
		if (nSemester == -1) nSemester = sTitle.indexOf("August ");
		if (nSemester == -1) nSemester = sTitle.indexOf("September ");
		if (nSemester == -1) nSemester = sTitle.indexOf("October ");
		if (nSemester == -1) nSemester = sTitle.indexOf("November ");
		if (nSemester == -1) nSemester = sTitle.indexOf("December ");
		if (nSemester == -1) nSemester = sTitle.indexOf("(MIT)");
		if (nSemester > nFirstSpace + 2) nSemester -= 2;
		String sCourseName = sTitle.substring(nFirstSpace + 1, nSemester);
		
		// get the semester
		String sSemester = sTitle.substring(nSemester);
		
		// build the fixed title
		sTitle = sCourseName + " " + sIdentifier + sSemester;
		
		// lets drop MIT from the title
		return sTitle.replace(" (MIT)","");
	}
	
	HashSet<String> hsEntrySubjects;
	
	private void addSubjectsForEntry(int nEntryID, String[] asSubjects)
	{
		try 
		{
			hsEntrySubjects = new HashSet<String>();
			for (int nSubject = 0; nSubject < asSubjects.length; nSubject++){
				addEntrySubject(nEntryID, asSubjects[nSubject]);
			}
		} catch(SQLException e) {
			Logger.error("addSubjectsForEntry: " + nEntryID + " - " + arrayToList(asSubjects));
		}
	}
	
	private static String[] normalizeSubjectList(String sList)
	{
		return sList.split("--|[;:,\\)(/\".]");
	}
	
	private static String arrayToList(String[] asSubjects)
	{
		String sList = "";
		for (int nSubject = 0; nSubject < asSubjects.length; nSubject ++) {
			sList += asSubjects[nSubject] + ",";
		}
		return sList;
	}
	
	private void addEntrySubject(int nEntryID, String sSubject) throws SQLException
	{
		// TODO: use the version of this method that takes a locale
		sSubject = sSubject.toLowerCase();
		if (sSubject.length() > 0)
		{
			String[] asSubjects = normalizeSubjectList(sSubject);
			if (asSubjects.length > 1) Logger.info("Converted: " + sSubject + " => " + arrayToList(asSubjects));
			for (int nSubject = 0; nSubject < asSubjects.length; nSubject++)
			{
				String sNormalizedSubject = asSubjects[nSubject].trim();
				if (sNormalizedSubject.length() > 0 && !hsEntrySubjects.contains(sNormalizedSubject)) {
					pstAddEntrySubject.setInt(1, getSubjectID(sNormalizedSubject));
					pstAddEntrySubject.setInt(2, nEntryID);
					try {
					pstAddEntrySubject.executeUpdate();
					} catch(Exception e) {
						Logger.error("Error adding subject (" + sNormalizedSubject + ") to entry: " + nEntryID, e);
					}
					nNewEntrySubjects++;
					hsEntrySubjects.add(sNormalizedSubject);
					if (nNewEntrySubjects > 100) {
//						pstAddEntrySubject.executeBatch();
						nNewEntrySubjects = 0;
					}
				}
			}
		}
	}
	
	private int getSubjectID(String sSubject) throws SQLException
	{
		// set up the prepared statement
		pstGetSubjectID.setString(1, sSubject);
		
		// do the query
		ResultSet rs = pstGetSubjectID.executeQuery();
		
		// if the subject isn't already in the database, add it now
		int nSubjectID = 0;
		if (!rs.next()) nSubjectID = addSubject(sSubject);
		
		// return the subject's id
		else nSubjectID = rs.getInt(1);

		rs.close();
		return nSubjectID;
	}
	
	private int addSubject(String sSubject) throws SQLException
	{
		pstAddSubject.setString(1, sSubject);
		pstAddSubject.executeUpdate();
		return getLastID(pstAddSubject);
	}
	
	private int addOrUpdateRSSEntry(PreparedStatement st, FeedInfo feedInfo, SyndEntry entry, DCModule dcm, DCTermsModule dctm, String sURI, String[] asSubjects, Timestamp updatedAt, String sOAIIdentifier, int nEntryID, int nLanguageID) throws SQLException
	{
		// id, feed_id, permalink, author, title, 
		// description, content, unique_content, published_at, 
		// entry_updated_at, oai_identifier, language, harvested_at, direct_link
		// get the description for the entry, because we need it twice
		SyndContent scDescription = entry.getDescription();
		String sDescription = scDescription == null ? null : scDescription.getValue();
		if (sDescription == null && dcm != null) sDescription = dcm.getDescription();
		if (sDescription == null) sDescription = "";

		st.setInt(1, feedInfo.nFeedID);

		// permalink
		if (sURI == null)
		{
			Logger.info("Null permalink in: " + feedInfo.sTitle/* + "(" + feedInfo.sURI + ")"*/);
			return 0;
		}
		st.setString(2, sURI);
		
		// author
		String sAuthor = entry.getAuthor();
		if (sAuthor == null) sAuthor = dcm.getCreator();
		if (sAuthor == null) sAuthor = dctm.getCreator();
		st.setString(3, sAuthor);

		// title
		String sTitle = entry.getTitle();
		if (sTitle == null && dcm != null) sTitle = dcm.getTitle();
		if (sTitle != null)
		{
			if (sTitle.indexOf("(MIT)") != -1) sTitle = fixMITEntryTitle(sTitle);
			else if (feedInfo.sShortTitle != null) sTitle.replace(feedInfo.sShortTitle + " ", "");
		}
		st.setString(4, sTitle == null ? "" : sTitle);
		
		// description
		st.setString(5, sDescription);
		
		// content
		String sContent = null;
		List listContents = entry.getContents();
		if (listContents.size() > 0)
		{
			SyndContentImpl content = (SyndContentImpl)listContents.get(0);
			sContent = content.getValue();
		}
		// only store the content if it is different than the description
		boolean bUniqueContent = (sContent != null && !sContent.equals(sDescription));
		st.setString(6, bUniqueContent ? sContent : "");

		// unique_content
		st.setBoolean(7, bUniqueContent);
		
		// published_at
		Timestamp currentTime = currentTime();
		Timestamp publishedTime; 
		Date publishedDate = entry.getPublishedDate();
		if (publishedDate == null && dctm != null) publishedDate = (Date)dctm.getCreatedDate();
		if (publishedDate == null || publishedDate.after(currentTime)) publishedTime = currentTime;
		else publishedTime = new Timestamp(publishedDate.getTime());
		st.setTimestamp(8, publishedTime);

		// updated_at
		st.setTimestamp(9, updatedAt);

		// oai_identifier
		st.setString(10, sOAIIdentifier);
		
		// language
		st.setInt(11,nLanguageID);
		// harvested at
		st.setTimestamp(12, currentTime);
		
		// direct link
		String sDirectLink = getForeignMarkupValue(entry, "direct_link");
		sDirectLink = normalizeUrl(sDirectLink);
		st.setString(13, sDirectLink);
		
		// if we are updating, we specify the id last
		if (st == pstUpdateEntry) st.setInt(14, nEntryID);
		
		st.executeUpdate();
		nEntryID = getLastID(st);
		
    	// add the subjects for the entry
    	if (nEntryID != 0 && asSubjects != null) 
    	{
    		addSubjectsForEntry(nEntryID, asSubjects);
		}
		// check for microformats in the content:encoded element (where the structured blogging plugin puts them)
		parseEncodedContent(entry, nEntryID);
		
		// get images out of the entry
		extractImagesFromEntry(nEntryID, sDescription);

		return nEntryID;
	}
	
	private static Timestamp timeMinutesFromNow(int nMinutes)
	{
		Calendar cal = new GregorianCalendar();
		cal.add(Calendar.MINUTE, nMinutes);
		return new Timestamp(cal.getTime().getTime());
	}

	public static double secondsSince(Timestamp time)
	{
		return ((currentTime().getTime() - time.getTime())/1000.0);
	}

	public static String secondsSinceStr(Timestamp time)
	{
		String sMilis = "" + secondsSince(time);
		return sMilis.substring(0,sMilis.indexOf('.') + 2);
	}
	
	private String[] getRSSSubjects(SyndEntry entry, DCModule dcm)
	{	
		int nSubjects = 0;
		String[] asSubjects = null;

		// try to get subjects from the rss categories
		List lCategories = entry.getCategories();
		int nCategories = lCategories.size();
		if (nCategories != 0)
		{
			asSubjects = new String[nCategories];
			for (ListIterator liCategories = lCategories.listIterator(); liCategories.hasNext();)
			{
				SyndCategory category = (SyndCategory)liCategories.next();
				asSubjects[nSubjects++] = category.getName();
			}
		}
		// if we don't have rss categories, try dublin core subjects
		else if (dcm != null)
    	{
    		List lSubjects = dcm.getSubjects();
    		asSubjects = new String[lSubjects.size()];
    		for (ListIterator liSubjects = lSubjects.listIterator(); liSubjects.hasNext();)
    		{
    			DCSubject subject = (DCSubject)liSubjects.next();
    			asSubjects[nSubjects++] = subject.getValue();
    		}
    	}
		return asSubjects == null ? null : asSubjects;
	}
	
	
	private void addFeed(FeedInfo feedInfo) throws SQLException
	{
		Logger.info("Adding feed: " + feedInfo.sTitle/* + " (" + feedInfo.sURI + ")."*/);
		
		// set up the prepared statement
		pstAddFeed.setString(1, feedInfo.sURI);
		pstAddFeed.setString(2, feedInfo.sTitle == null ? "" : feedInfo.sTitle);
		pstAddFeed.setInt(3, nFeedHarvestInterval);
		Timestamp currentTime = currentTime();
		pstAddFeed.setTimestamp(4, currentTime);
		pstAddFeed.setTimestamp(5, currentTime);
		pstAddFeed.setTimestamp(6, currentTime);
		pstAddFeed.setInt(7, feedInfo.nServiceID);

		// add the feed
		pstAddFeed.executeUpdate();
		
		// if not the default service, we need the serviceURI
		if (feedInfo.nServiceID != FeedInfo.SERVICE_RSS) getServiceURI(feedInfo);
	}
	
	private EntryInfo getEntry(int nFeedID, String sEntryPermalink/*, Timestamp sUpdatedAt*/) throws SQLException
	{
		if (!bTalkToDB) return null;
		
		EntryInfo entry = null;
		
		// set up the prepared statement
		pstFindDuplicateEntries.setInt(1, nFeedID);
		pstFindDuplicateEntries.setString(2, sEntryPermalink);
		// TODO: Add updated at to the query and update the entry if it has been updated
		
		// do the query
		ResultSet rs = pstFindDuplicateEntries.executeQuery();
		if (rs.next())
		{
			entry = new EntryInfo();
			entry.nEntryID = rs.getInt(1);
			entry.date = rs.getTimestamp(2);
		}
		rs.close();
		return entry;
	}

	private EntryInfo getOAIEntryID(String sIdentifier) throws SQLException
	{
		EntryInfo entry = null;
		// set up the prepared statement
		pstFindDuplicateOAIEntries.setString(1, sIdentifier);
		
		// do the query
		ResultSet rs = pstFindDuplicateOAIEntries.executeQuery();
		if (rs.next())
		{
			entry = new EntryInfo();
			entry.nEntryID = rs.getInt(1);
			entry.date = rs.getTimestamp(2);
		}
		rs.close();
		return entry;
	}

	private void getServiceURI(FeedInfo feedInfo)
	{
		try
		{
			// set up the prepared statement
			pstGetServiceURI.setInt(1, feedInfo.nServiceID);
			
			// get the service URI
			ResultSet rs = pstGetServiceURI.executeQuery();
			if (rs.next()) feedInfo.sServiceURI = rs.getString(1);
			rs.close();
		}
		catch (SQLException e)
		{
			Logger.error("getServiceURI: ", e);
		}
	}

// this is how you do it in postgres	
//	private int getNextID(String sTable) throws SQLException
//	{
////		ResultSet rsNextID = stNextID.executeQuery("SELECT nextval('" + sTable + "_id_seq')");
//		stNextID.executeQuery("INSERT INTO " + sTable + " VALUES ()')");
//		ResultSet rsNextID = stNextID.executeQuery("SELECT LAST_INSERT_ID()");
//		try
//		{
//			if (!rsNextID.next())
//			{
//				rsNextID.close();
//				throw new SQLException("Unable to retrieve the id for a newly added entry.");
//			}
//			int nNextID = rsNextID.getInt(1);
//			if (nNextID == 0)
//			{
//				rsNextID.close();
//				throw new SQLException("Unable to retrieve the id for a newly added entry.");
//			}
//			return nNextID;
//		}
//		catch (SQLException e)
//		{
//			if (rsNextID != null) rsNextID.close();
//			throw e;
//		}
//	}
	
	private int getLastID(Statement st) throws SQLException
	{
		ResultSet rsLastID = st.executeQuery("SELECT LAST_INSERT_ID()");
		try
		{
			if (!rsLastID.next())
			{
				rsLastID.close();
				throw new SQLException("Unable to retrieve the id for a newly added entry.");
			}
			int nLastID = rsLastID.getInt(1);
			if (nLastID == 0)
			{
				rsLastID.close();
				throw new SQLException("Unable to retrieve the id for a newly added entry.");
			}
			return nLastID;
		}
		catch (SQLException e)
		{
			if (rsLastID != null) rsLastID.close();
			throw e;
		}
	}
	
	private void updateFeedInfo(FeedInfo feedInfo)
	{
		try
		{
			// we just harvested a newly added feed
			if (feedInfo.nPriority == FeedInfo.PRIORITY_NOW)
			{
				// so bump its priority down to the default
				feedInfo.nPriority = FeedInfo.PRIORITY_DEFAULT; 
			}
			// last_harvested_at = ?, priority = ?, failed_requests = 0, display_uri = ?, description = ? WHERE id = ?");
			// store the last_harvested_at and new priority fields
			pstUpdateFeedInfo.setTimestamp(1, new Timestamp(feedInfo.lLastHarvested));
			pstUpdateFeedInfo.setInt(2,feedInfo.nPriority);
			pstUpdateFeedInfo.setTimestamp(3, new Timestamp(feedInfo.lLastRequested));
			pstUpdateFeedInfo.setString(4,feedInfo.sDisplayURI);
			pstUpdateFeedInfo.setString(5,feedInfo.sDescription == null ? "" : feedInfo.sDescription);
			pstUpdateFeedInfo.setString(6,feedInfo.sTitle);
			pstUpdateFeedInfo.setInt(7,feedInfo.nFeedID);
			pstUpdateFeedInfo.executeUpdate();
			
//			Logger.i("Updating feed info: " + currentTime + "," + feedInfo.nPriority + "," + feedInfo.nFeedID);
		}
		catch (SQLException e)
		{
			Logger.error("updateFeedInfo: ", e);
		}
	}
	
	private static FeedInfo getFeedInfo(int nFeedID) throws Exception
	{
		Connection cn = getConnection();
		Statement st = cn.createStatement();
		String sSQL = QUERY_FEEDS + "WHERE feeds.id = " + nFeedID;
		ResultSet rs = st.executeQuery(sSQL);
		FeedInfo feed = new FeedInfo();
		if (rs.next())
		{
			feed = getFeedInfo(rs);
		}
		// close result set, statement, and connection
		rs.close();
		st.close();
		cn.close();
		return feed;
	}
	
	private static FeedInfo getFeedInfo(ResultSet rs) throws SQLException 
	{
//		"SELECT feeds.id, feeds.service_id, feeds.title, feeds.uri, feeds.priority, feeds.login, feeds.password, " +
//		"services.api_uri, feeds.last_harvested_at, feeds.failed_requests, feeds.harvest_interval, feeds.display_uri, feeds.short_title, tag_filter " +
		FeedInfo feedInfo = new FeedInfo();
		feedInfo.nFeedID = rs.getInt("id");
		feedInfo.nServiceID = rs.getInt("service_id");
		feedInfo.sTitle = rs.getString("title");
		feedInfo.sURI = rs.getString("uri");
		feedInfo.nPriority = rs.getInt("priority");

		feedInfo.sServiceUser = rs.getString("login");
		feedInfo.sServicePassword = rs.getString("password");

		feedInfo.sServiceURI = rs.getString("api_uri");
		if (feedInfo.sServiceURI == null || feedInfo.sServiceURI.length() == 0) feedInfo.sServiceURI = feedInfo.sURI;
		Date dtLastModified = rs.getDate("last_harvested_at");
		feedInfo.lLastHarvested = dtLastModified == null ? 0 : dtLastModified.getTime();
		feedInfo.nFailedRequests = rs.getInt("failed_requests");
		feedInfo.nRefreshInterval = rs.getInt("harvest_interval");
		feedInfo.sDisplayURI = rs.getString("display_uri");
		feedInfo.sShortTitle = rs.getString("short_title");
		
		feedInfo.sTagFilter = rs.getString("tag_filter");
		feedInfo.nDefaultLanguageID = rs.getInt("default_language_id");
		if (feedInfo.nDefaultLanguageID == 0) feedInfo.nDefaultLanguageID = nDefaultLanguageID;
		return feedInfo;
	}

	private static String getShortTitle(String sTitle)
	{
		if (sTitle == null || sTitle.length() < 5) return sTitle;
		
		// look for something parenthesized
		int nOpenParen = sTitle.indexOf('(');
		if (nOpenParen != -1)
		{
			int nCloseParen = sTitle.indexOf(')', nOpenParen);
			if (nCloseParen != -1) return sTitle.substring(nOpenParen+1, nCloseParen);
		}
		// look for 3 chars or more all capitalized
		for (int nChar = 0; nChar < sTitle.length(); nChar++)
		{
			if (Character.isUpperCase(sTitle.charAt(nChar)) && nChar < sTitle.length() - 3)
			{
				int nChar2 = nChar;
				boolean bAllUppercase = true;
				for (nChar2 = nChar; nChar2 < sTitle.length(); nChar2++)
				{
					char ch = sTitle.charAt(nChar2);
					if (Character.isWhitespace(ch)) break;
					if (!Character.isUpperCase(ch))
					{
						bAllUppercase = false;
						break;
					}
				}
				if (bAllUppercase) return sTitle.substring(nChar, nChar2);
			}
		}
		return sTitle;
	}
	
	private static Hashtable<String,String> buildShortNamesHashTable()
	{
		Hashtable<String,String> htShortNames = new Hashtable<String,String>();
		htShortNames.put("ArsDigita University", "ArsDigita");
		htShortNames.put("Berkman Center for Internet and Society", "Berkmen Center");
		htShortNames.put("Carnegie Mellon University - Open Learning Initiative", "CMU OLI");
		htShortNames.put("Entrepreneurship and Emerging Enterprises at Syracuse University ", "Syracuse");
		htShortNames.put("Federation of American Scientists Learning Technologies Project", "FAS");
		htShortNames.put("Foothill-De Anza Community College", "FHDA");
		htShortNames.put("Individual Authors", "Individuals");
		htShortNames.put("Learn Activity (University of Hong Kong)", "HKU");
		htShortNames.put("Light and Matter - Physics and astronomy resources", "Light and Matter");
		htShortNames.put("Notre Dame Opencourseware", "ND OCW");
		htShortNames.put("Project Gutenberg", "Gutenberg");
		htShortNames.put("Qedoc Learning Resources", "Qedoc");
		htShortNames.put("Sofia - Foothill De Anza College", "Sofia");
		htShortNames.put("TakingITGlobal TIGed Activities", "TIGed");
		htShortNames.put("The Chem Collective", "Chem Collective");
		htShortNames.put("The Open University", "Open University");
		htShortNames.put("The Why Files", "Why Files");
		htShortNames.put("Tufts University OpenCourseWare", "Tufts OCW");
		htShortNames.put("Utah State University OpenCourseWare", "USU OCW");
		htShortNames.put("Wireless Networking in the Developing World", "WNDW");
		htShortNames.put("Calisphere - California Digital Library", "Calispher");
		htShortNames.put("Carnegie Academy for the Scholarship of Teaching and Learning in Higher Education", "CASTL Higher Ed");
		htShortNames.put("Carnegie Academy for the Scholarship of Teaching and Learning in K-12 Education", "CASTL K-12");
		htShortNames.put("Internet History Sourcebooks Project", "Internet History");
		htShortNames.put("Lawrence Berkeley National Laboratory", "LBL");
		htShortNames.put("NASA's Destination Tomorrow", "Destination Tomorrow");
		htShortNames.put("NASA SCI Files", "NASA SCI");
		htShortNames.put("Open Context (Alexandria Archive Institute)", "Alexandria");
		htShortNames.put("Stanford Encyclopedia of Philosophy", "SEP");
		htShortNames.put("The Tech Museum of Innovation Design Challenges", "Tech Museum");
		htShortNames.put("Women Working, 1800-1930, Open Collections Program", "Women Working");
		return htShortNames;
	}
	
	private static void discoverOAISets()
	{
		try
		{
			Hashtable<String,String> htShortNames = buildShortNamesHashTable();
			
			// loop through the registered oai end points
			Connection cn = getConnection();
			Statement stEndpoints = cn.createStatement();
			PreparedStatement pstFeeds = cn.prepareStatement("SELECT title FROM feeds WHERE uri = ?");
			PreparedStatement pstAddFeed = cn.prepareStatement("INSERT INTO feeds (uri, title, short_title, harvested_from_display_uri, harvested_from_title, harvested_from_short_title, harvest_interval, priority, service_id) VALUES (?,?,?,?,?,?,?,1,13)");
			ResultSet rsEndpoints = stEndpoints.executeQuery("SELECT * FROM oai_endpoints");
			while (rsEndpoints.next())
			{
				String sURI = rsEndpoints.getString("uri");
				String sMetadataFormat = rsEndpoints.getString("metadata_prefix");
				String sHarvestedFromDisplayURI = rsEndpoints.getString("display_uri");
				String sHarvestedFromTitle = rsEndpoints.getString("title");
				String sHarvestedFromShortTitle = rsEndpoints.getString("short_title");
				
				// request the sets
				String sListSetsURI = sURI + "?verb=ListSets";
				HttpURLConnection uriConn = (HttpURLConnection)new URL(sListSetsURI).openConnection();
            	uriConn.setConnectTimeout(nConnectionTimeout*1000);
    			uriConn.setRequestProperty("User-agent", HARVESTER_USER_AGENT);

    			// request and parse the xml
	            SyndFeed feed = new SyndFeedInput().build(new XmlReader(uriConn));
	            List lEntries = feed.getEntries();
	            for (ListIterator entries = lEntries.listIterator(); entries.hasNext();)
	            {
	            	// get info about the entry
	            	SyndEntry entry = (SyndEntry)entries.next();
	            	
	            	// get the set id and name
	            	String sSetID = entry.getUri();
	            	String sSetTitle = entry.getTitle();

	            	// build the feed uri for the set
	            	String sFeedURI = sURI + "?verb=ListRecords&metadataPrefix=" + sMetadataFormat + "&set=" + sSetID; 
	            	
	            	// query to see if a feed for the set already exists in the database
	            	pstFeeds.setString(1, sFeedURI);
	            	ResultSet rsFeeds = pstFeeds.executeQuery();
	            	if (!rsFeeds.next())
	            	{
	            		Logger.info("Discovered new OAI set: " + sSetTitle);
	            		pstAddFeed.setString(1, sFeedURI);
	            		pstAddFeed.setString(2, sSetTitle);
	            		String sShortTitle = htShortNames.get(sSetTitle);
	            		if (sShortTitle == null) sShortTitle = getShortTitle(sSetTitle);
	            		pstAddFeed.setString(3, sShortTitle);
	            		pstAddFeed.setString(4, sHarvestedFromDisplayURI);
	            		pstAddFeed.setString(5, sHarvestedFromTitle);
	            		pstAddFeed.setString(6, sHarvestedFromShortTitle);
	            		pstAddFeed.setInt(7, nFeedHarvestInterval);
	            		pstAddFeed.addBatch();
	            	}
	            }
			}
			pstAddFeed.executeBatch();
			pstFeeds.close();
			stEndpoints.close();
			rsEndpoints.close();
			cn.close();
		}
		catch (Exception e)
		{
			Logger.error(e);
		}
	}
	
	private static boolean bDiscoverOAISets = true; 

	public static Vector<FeedInfo> getStaleFeeds()
	{
		ResultSet rsStaleFeeds = null;
		try
		{
			// see if any of the registered OAI end points has new collection sets
			if (bDiscoverOAISets) discoverOAISets();
			
			// prepare statement for retrieving stale feeds
			cnFeeds = getConnection();
			stGetStaleFeeds = cnFeeds.createStatement();
			
			getLanguageMappings(cnFeeds);	
	
			// create a vector for storing the feeds
			Vector<FeedInfo> vFeeds = new Vector<FeedInfo>();

			// query the db for feeds whose were refreshed longer ago than their refresh interval
			rsStaleFeeds = stGetStaleFeeds.executeQuery(QUERY_STALE_FEEDS);
			while (rsStaleFeeds.next())
			{
				vFeeds.add(getFeedInfo(rsStaleFeeds));
			}
			// close result set, statement, and connection
			rsStaleFeeds.close();
			stGetStaleFeeds.close();
			stGetStaleFeeds = null;
			cnFeeds.close();
			
			// return the list of stale feeds
//			Logger.info("Stale feeds: " + vFeeds.size());
			return vFeeds;
		}
		catch (SQLException e)
		{
			Logger.error("getStaleFeeds-1: ", e);
			try
			{
				if (rsStaleFeeds != null) rsStaleFeeds.close();
				if (stGetStaleFeeds != null)
				{
					stGetStaleFeeds.close();
					stGetStaleFeeds = null;
				}
				if (cnFeeds != null)
				{
					cnFeeds.close();
					cnFeeds = null;
				}
			}
			catch (SQLException e2){}
			return null;
		}
		catch (ClassNotFoundException e)
		{
			Logger.error("getStaleFeeds-2: " + e);
			return null;
		}
	}

	public static void setConnectionTimeout(int nTimeout)
	{
		nConnectionTimeout = nTimeout;
	}
	
	public static int getConnectionTimeout()
	{
		return nConnectionTimeout;
	}
	
	public static void setFeedRefreshInterval(int nInterval)
	{
		nFeedHarvestInterval = nInterval;
	}
	
	public void run() 
	{
		try 
		{
			while (true) 
			{
				// retrieve a feed object from the queue; block if the queue is empty
				FeedInfo feedInfo = toDoQueue.take();
				
//				System.out.println("retrieved from queue: " + feedInfo.sURI);
				
				// kill the thread if the end-of-stream marker was retrieved
				if (feedInfo == FeedInfo.NO_MORE_WORK) break;
				
				// harvest the feed
				else harvestFeed(feedInfo);
			}
		}
		catch (InterruptedException e) 
		{
			Logger.error("run: " + e);
		}
	}

	private static void testFeed(String sUrl, int nServiceID, boolean bTalkToDB, int nFeedID) throws Exception
	{
		FeedInfo fi = nFeedID == 0 ? new FeedInfo(sUrl) : getFeedInfo(nFeedID);
		fi.nServiceID = nServiceID;
		fi.nFeedID = nFeedID;
		fi.nPriority = FeedInfo.PRIORITY_NOW;
		fi.sURI = sUrl;
		Harvester harvester = new Harvester(null, null);
		harvester.bTalkToDB = bTalkToDB;
		if (bTalkToDB) harvester.initDBConnection();
		harvester.harvestFeed(fi);
	}
//	private static void testExtractImages() throws SQLException
//	{
//		Harvester harvester = new Harvester(null, null);
//		Logger.setDBLogLevel(10);
//		Logger.setLogToConsole(true);
//		harvester.extractImagesFromEntry(1, "<p><a href=\"http://www.flickr.com/people/ncho/\">ncho_1</a> posted a photo:</p><p><a href=\"http://www.flickr.com/photos/ncho/231089375/\" title=\"0557666-R2-023-10\"><img src=\"http://farm1.static.flickr.com/67/231089375_45a54cc3ce_m.jpg\" width=\"240\" height=\"98\" alt=\"0557666-R2-023-10\" style=\"border: 1px solid #ddd;\" /></a></p>");
//	}
	
	private static void getConfigOptions()
	{
		Properties properties = new Properties();
	    try 
	    {
	    	// load the property file
	    	FileInputStream in = new FileInputStream("aggregatord.properties");
	        properties.load(in);
	        in.close();
	        
	        String sValue = properties.getProperty("discover_oai_sets");
	        if (sValue != null) bDiscoverOAISets = "true".equals(sValue);
	        sValue = properties.getProperty("import_archived_feed_data");
	        if (sValue != null) bImportArchivedFeeds = "true".equals(sValue);
	        sValue = properties.getProperty("harvest_from_wire");
	        if (sValue != null) bHarvestFromWire = "true".equals(sValue);
	        
	        Logger.getOptions(properties);
	    }
	    catch (IOException e) 
	    {
	    	System.out.println("error reading aggregatord.properties file");
	    }
	}
//	private void testCases()
//	{
//		// rss
//		// oai
//		// flickr
//		// delicious
//	}
	
	private static String readFile(String sUrl) throws IOException
	{
		StringBuffer s = new StringBuffer();
		try
		{
			URL url = new URL(sUrl);
			InputStream f = url.openStream();
			int len=f.available();
	
			for(int i=1;i<=len;i++)
			{
				s.append((char)f.read());
			}
			f.close();
		}
		catch(IOException e)
		{
			if (e.toString().indexOf("code: 400") == -1) throw e;
			else if (e.toString().indexOf("code: 500") == -1)
			{
				try
				{
					Thread.sleep(1000*60*5);
				}
				catch(Exception e2){}
				throw e;
			}
			else System.out.println(e);
		}
		return s.toString();
	}
	
	public static String getFixedURL(String sURI) throws IOException
	{
		// download the web page
		String sHTML = readFile(sURI);

		// parse it
		Source source = new Source(sHTML);
		source.setLogWriter(new OutputStreamWriter(System.err));

		// get the list of all links
		List lLinks = source.findAllStartTags("a");
		for (Iterator iLinks = lLinks.iterator(); iLinks.hasNext();)
		{
			Element linkTag = ((StartTag)iLinks.next()).getElement();
			if ("item_controls_view".equals(linkTag.getAttributeValue("class")))
			{
				return linkTag.getAttributeValue("href");
			}
		}
		return sURI;
	}
	
	public static String getLogMessages()
	{
		return Logger.getMessages();
	}
    static public void getLanguageMappings(Connection cn)
    {
    	try{
    	PreparedStatement pstGetSupportLanguages=cn.prepareStatement("SELECT id, locale, is_default FROM languages where muck_raker_supported=1");
    	ResultSet result=pstGetSupportLanguages.executeQuery();
	    	while(result.next()){
	    		Integer nLanguageID = result.getInt(1);
	    		htLanguages.put(result.getString(2).substring(0,2), nLanguageID);
	    		if (result.getBoolean(3) == true)
	    			nDefaultLanguageID = nLanguageID;
	    	}
    	}
    	catch(Exception e){
    		Logger.error("Read from table language");
    	}
    }

	private static void updateLanguageRecordCounts() 
	{
		try
		{
			Connection cn = getConnection();
			Statement st = cn.createStatement();
			st.executeUpdate("UPDATE feeds SET entries_count = (SELECT count(*) FROM entries WHERE feed_id = feeds.id)");
			st.executeUpdate("UPDATE languages SET indexed_records = (SELECT count(*) FROM entries WHERE entries.language_id = languages.id)");
			st.close();
			cn.close();
		}
		catch (Exception e)
		{
			Logger.error("updateLanguagesRecordCount", e);
		}
	}

	private static boolean harvestStaleFeeds(boolean bTest) 
	{
		getConfigOptions();
		Logger.status("harvest - begin");
		boolean bChanges = false;
		System.setProperty("sun.net.client.defaultReadTimeout","" + nConnectionTimeout*1000);
		
		Vector<FeedInfo> vFeeds = getStaleFeeds();
		for (Enumeration<FeedInfo> eFeeds = vFeeds.elements(); eFeeds.hasMoreElements();) 
		{
			Harvester h = new Harvester(null,null);
			if (bTest) h.nMaxEntries = 2000;
			if (h.harvestFeed(eFeeds.nextElement())) bChanges = true;
		}
		if (bChanges) updateLanguageRecordCounts();
		
    	Logger.status("harvest - end (new, updated, deleted): " + nTotalNewEntries + ", " + nTotalUpdatedEntries + ", " + nTotalDeletedEntries);
		return bChanges;
	}
	
	public static boolean harvest(boolean bTest)
	{
		try
		{
			Harvester.loadDBDriver();
			return harvestStaleFeeds(bTest);
		}
		catch (Exception e)
		{
			Logger.error(e);
		}
		return false;
	}
	
	public static void main(String[] args) 
	{
		harvest(false);
	}
}

