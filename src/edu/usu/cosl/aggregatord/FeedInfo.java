package edu.usu.cosl.aggregatord;

public class FeedInfo 
{
	public final static int OWNER_ID_UNKNOWN = 0;
	public final static int FEED_ID_UNKNOWN = -1;

	public final static int SERVICE_RSS = 0;
	public final static int SERVICE_DELICIOUS = 1;
	public final static int SERVICE_FLICKR = 6;
	public final static int SERVICE_OAI = 13;
	public final static int SERVICE_MERLOT = 23;
	
	public final static int PRIORITY_NOW = 1;
	public final static int PRIORITY_DEFAULT = 10;
	
	public int nFeedID;
	public String sURI;
	public String sDescription;
	public String sDisplayURI;
	public int nServiceID;
	public int nOwnerID;
	public String sServiceUser;
	public String sServicePassword;
	public String sServiceURI;
	public String sTitle;
	public String sShortTitle;
	public int nPriority = PRIORITY_DEFAULT;
	public long lLastHarvested;
	public int nRefreshInterval;
	public int nFailedRequests = 0;
	public String sErrorMessage;
	public int nTagsToCache = 0;
	public String sTagFilter;
	public String sLanguage = "en";
	
	public static FeedInfo NO_MORE_WORK = new FeedInfo(null,SERVICE_RSS,FEED_ID_UNKNOWN,OWNER_ID_UNKNOWN,null,null,PRIORITY_DEFAULT);
	
	public FeedInfo(String sURI, int nServiceID, int nFeedID, int nOwnerID, String sServiceUser, String sServicePassword, int nPriority)
	{
		this.sURI = sURI;
		this.sServiceURI = sURI;
		this.nServiceID = nServiceID;
		this.nFeedID = nFeedID;
		this.nOwnerID = nOwnerID;
		this.sServiceUser = sServiceUser;
		this.sServicePassword = sServicePassword;
	}
	public FeedInfo(String sURI, int nServiceID, int nFeedID, int nPriority)
	{
		this(sURI,nServiceID,nFeedID,OWNER_ID_UNKNOWN,null,null,nPriority);
	}
	public FeedInfo(String sURI, int nServiceID)
	{
		this(sURI,nServiceID,FEED_ID_UNKNOWN,OWNER_ID_UNKNOWN,null,null,PRIORITY_DEFAULT);
	}
	public FeedInfo(String sURI)
	{
		this(sURI,SERVICE_RSS,FEED_ID_UNKNOWN,OWNER_ID_UNKNOWN,null,null,PRIORITY_DEFAULT);
	}
	public FeedInfo()
	{
		this(null,SERVICE_RSS,FEED_ID_UNKNOWN,OWNER_ID_UNKNOWN,null,null,PRIORITY_DEFAULT);
	}
	public boolean equals(FeedInfo fi)
	{
		return this.sURI != null && this.sURI.equals(fi.sURI);
	}
}
