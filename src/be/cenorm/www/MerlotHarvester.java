package be.cenorm.www;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;

import edu.usu.cosl.util.Logger;
import edu.usu.cosl.syndication.io.impl.MarkupProperty;

import java.io.StringReader;
import java.rmi.RemoteException;
import java.util.List;
import java.util.ListIterator;

import javax.xml.rpc.ServiceException;

public class MerlotHarvester 
{
	private SqiTargetBindingStub searchStub;
	private final static String sKey = "ariadnekey";
	private final static String[] asTerms = {"of","the","to"};
	private int nTerm;
	private String sQuery;
	private int nOffset = 1;
	private int nEntry = 1;
	private ListIterator entries;
	private SyndEntry entry;
	
	public MerlotHarvester()
	{
		nTerm = 0;
		try
		{
			MerlotTargetServiceBindingLocator locator = new MerlotTargetServiceBindingLocator();
			searchStub = (SqiTargetBindingStub)locator.getMerlotTargetServiceBinding();
			searchStub.setMaxQueryResults(sKey, 0);
			searchStub.setResultsSetSize(sKey, 25);
			updateQueryString();
			loadNextEntry();
		}
		catch (RemoteException e)
		{
			Logger.error("MerlotHarvester", e);
		}
		catch (ServiceException e)
		{
			Logger.error("MerlotHarvester", e);
		}
	}
	
	private void updateQueryString()
	{
		sQuery = "<simpleQuery><term>" + asTerms[nTerm] + "</term></simpleQuery>";
		nOffset = 1;
	}
	
	private void queryMerlot()
	{
		try
		{
			Logger.info("Merlot Query: '" + asTerms[nTerm] + "' - " + nOffset);
			String sResult = searchStub.synchronousQuery(sKey, sQuery, nOffset);
			nOffset += 25;
//			Logger.info(sResult);
			if (!"dummy".equals(sResult))
			{
				SyndFeed feed = new SyndFeedInput().build(new StringReader(sResult));
	            entries = feed.getEntries().listIterator();
			}
			else
			{
				entry = null;
			}
		}
		catch (Exception e)
		{
			System.out.println(e);
			entry = null;
			return;
		}
	}
	
	private void loadNextEntry()
	{
		// need to request the next batch
		if (nEntry == 1)
		{
			queryMerlot();
		}
		if (entries != null && entries.hasNext())
		{
	       	// get info about the entry
	       	entry = (SyndEntry)entries.next();
		}
		else if (nTerm < asTerms.length - 1)
		{
			nTerm++;
			updateQueryString();
			queryMerlot();
			nEntry = 1;
	       	entry = (SyndEntry)entries.next();
		}
		else entry = null;
       	
		// we keep track of how many (they always come in batches of 25 or less)
       	nEntry++;
       	
       	if (nEntry == 26) nEntry = 1;
	}
	
	public boolean hasMoreEntries()
	{
		return (!(searchStub == null || entry == null));
	}
	
	public SyndEntry next() throws RemoteException
	{
		SyndEntry currentEntry = entry;
		loadNextEntry();
		return currentEntry;
	}
	
	public static void main(String[] args) 
	{
		Logger.setLogToConsole(true);
		Logger.setLogLevel(10);
		try
		{
			MerlotHarvester harvester = new MerlotHarvester();
			int nEntries = 1;
			while (harvester.hasMoreEntries())
			{
				SyndEntry entry = harvester.next();

				// the parser stores the OAI identifier in foreign markup
				String sOAIIdentifier = "";
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
    						if (sName.equals("oai_identifier"))
    						{
    							sOAIIdentifier = (String)mp.getValue();
    						}
    					}
					}
    			}
				Logger.info((nEntries++) + " - " + entry.getTitle() + " - " + sOAIIdentifier + " - " + entry.getLink());
			}
		}
		catch (Exception e)
		{
			Logger.error(e);
		}
	}
}
