package edu.usu.cosl.aggregatord;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.ListIterator;

import com.sun.syndication.io.impl.DateParser;

import edu.usu.cosl.util.Logger;
import edu.usu.cosl.util.DBThread;
import edu.usu.cosl.microformats.EventConcert;
import edu.usu.cosl.microformats.EventConference;
import edu.usu.cosl.microformats.EventGeneric;
import edu.usu.cosl.microformats.EventLink;
import edu.usu.cosl.microformats.EventPerson;

public class MicroformatDBManager extends DBThread {

	private PreparedStatement stAddEvent;
	private PreparedStatement stAddConference;
	private PreparedStatement stAddConcert;
	private PreparedStatement stAddConferencePerson;
	private PreparedStatement stAddEventLink;
	private PreparedStatement stEventInDB;
	private Statement stNextID;

	public static MicroformatDBManager create(Connection cn) throws SQLException
	{
		MicroformatDBManager mdm = new MicroformatDBManager();
		mdm.stAddEvent = cn.prepareStatement("INSERT INTO micro_events (id, entry_id, name, price, image, address, subaddress, city, state, postcode, country, begins, ends, description, tags, duration, location) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
		mdm.stAddConference = cn.prepareStatement("INSERT INTO micro_conferences (micro_event_id, theme, register_by, submit_by) VALUES (?,?,?,?)");
		mdm.stAddConcert = cn.prepareStatement("INSERT INTO micro_concerts (micro_event_id, performer, ticket_url) VALUES (?,?,?)");
		mdm.stAddConferencePerson = cn.prepareStatement("INSERT INTO micro_event_persons (micro_event_id, user_id, name, role, email, link, phone) VALUES (?,?,?,?,?,?,?)");
		mdm.stAddEventLink = cn.prepareStatement("INSERT INTO micro_event_links (micro_event_id, uri, title) VALUES (?,?,?)");
		mdm.stEventInDB = cn.prepareStatement("SELECT COUNT (*) FROM micro_events WHERE entry_id = ? AND name = ? AND begins = ?");
		mdm.stNextID = cn.createStatement();
		return mdm;
	}
	public void close() throws SQLException
	{
		if (stAddEvent != null) stAddEvent.close(); 
		if (stAddConference != null) stAddConference.close(); 
		if (stAddConcert != null) stAddConcert.close(); 
		if (stAddConferencePerson != null) stAddConferencePerson.close(); 
		if (stAddEventLink != null) stAddEventLink.close(); 
		if (stEventInDB != null) stEventInDB.close();
	
		stAddConcert = null;
		stAddConference = null;
		stAddConferencePerson = null;
		stAddEventLink = null;
		stAddEvent = null;
		stEventInDB = null;
	}

	private void addEventPersonToDB(int nConferenceID, EventPerson person) throws SQLException
	{
		stAddConferencePerson.setInt(1, nConferenceID);
		stAddConferencePerson.setInt(2, 0);
		stAddConferencePerson.setString(3, person.getName());
		stAddConferencePerson.setString(4, person.getRole());
		stAddConferencePerson.setString(5, person.getEmail());
		stAddConferencePerson.setString(6, person.getLink());
		stAddConferencePerson.setString(7, person.getPhone());
		stAddConferencePerson.executeUpdate();
	}
	private void addEventLinkToDB(int nEventID, EventLink link) throws SQLException
	{
		stAddEventLink.setInt(1, nEventID);
		stAddEventLink.setString(2, link.getURL());
		stAddEventLink.setString(3, link.getTitle());
		stAddEventLink.executeUpdate();
	}
	private void addConferenceToDB(int nEventID, EventConference event) throws SQLException
	{
		stAddConference.setInt(1, nEventID);
		stAddConference.setString(2, event.getTheme());
	    stAddConference.setTimestamp(3, dateToTimestamp(event.getRegisterBy()));
	    stAddConference.setTimestamp(4, dateToTimestamp(event.getSubmitBy()));
		stAddConference.executeUpdate();

		List l = event.getPeople();
	    if (l != null)
	    {
	    	for (ListIterator li = l.listIterator(); li.hasNext();)
	    	{
	    		addEventPersonToDB(nEventID, (EventPerson)li.next());
	    	}
	    }
	    stAddConference.executeUpdate();
	}
	private void addConcertToDB(int nEventID, EventConcert event) throws SQLException
	{
		stAddConcert.setInt(1, nEventID);
		stAddConcert.setString(2, event.getPerformer());
		stAddConcert.setString(3, event.getTickets());
		stAddConcert.executeUpdate();
	}
	private String nonNullValue(String sValue)
	{
		return sValue == null ? "" : sValue;
	}
	private int getNextID(String sTable) throws SQLException
	{
		ResultSet rsNextID = stNextID.executeQuery("SELECT nextval('" + sTable + "_id_seq')");
		try
		{
			if (!rsNextID.next())
			{
				rsNextID.close();
				throw new SQLException("Unable to retrieve the id for a newly added entry.");
			}
			int nNextID = rsNextID.getInt(1);
			rsNextID.close();
			return nNextID;
		}
		catch (SQLException e)
		{
			if (rsNextID != null) rsNextID.close();
			throw e;
		}
	}
	private int addGenericEventToDB(EventGeneric event, int nEntryID) throws SQLException
	{
		int nConferenceID = getNextID("micro_conferences");
		stAddEvent.setInt(1, nConferenceID);
	    stAddEvent.setInt(2, nEntryID);
		stAddEvent.setString(3, event.getName());
		stAddEvent.setString(4, nonNullValue(event.getPrice()));
		stAddEvent.setString(5, nonNullValue(event.getImage()));
	    stAddEvent.setString(6, nonNullValue(event.getAddress()));
	    stAddEvent.setString(7, nonNullValue(event.getSubAddress()));
	    stAddEvent.setString(8, nonNullValue(event.getCity()));
	    stAddEvent.setString(9, nonNullValue(event.getState()));
	    stAddEvent.setString(10, nonNullValue(event.getPostcode()));
	    stAddEvent.setString(11, nonNullValue(event.getCountry()));
	    stAddEvent.setTimestamp(12, dateToTimestamp(event.getBegins()));
	    stAddEvent.setTimestamp(13, dateToTimestamp(event.getEnds()));
	    stAddEvent.setString(14, nonNullValue(event.getDescription()));
	    stAddEvent.setString(15, nonNullValue(event.getTags()));
	    stAddEvent.setString(16, event.getDuration());
	    stAddEvent.setString(17, event.getLocation());
	    stAddEvent.executeUpdate();
	    
	    List l = event.getLinks();
	    if (l != null)
	    {
	    	for (ListIterator li = l.listIterator(); li.hasNext();)
	    	{
	    		addEventLinkToDB(nConferenceID, (EventLink)li.next());
	    	}
	    }
	    return nConferenceID;
	}
	private Timestamp dateToTimestamp(String sDate)
	{
		if (sDate == null) return null;
		int nIndex = sDate.indexOf("T");
		if (nIndex != -1)
		{
			Calendar cal = new GregorianCalendar();
			long offset = cal.get(Calendar.ZONE_OFFSET)/(60*60*1000);
			if (!sDate.endsWith("Z") && sDate.indexOf('+',nIndex) == -1 && sDate.indexOf('-',nIndex) == -1)
			{
				sDate += ((offset < 0 ? offset : "+" + offset) + ":00");
			}
		}
		Date date = DateParser.parseDate(sDate);
		return date == null ? currentTime() : new Timestamp(date.getTime());
	}
	public boolean eventInDB(EventGeneric event, int nEntryID)
	{
		int nCount = 0;
		ResultSet rs = null;
		try
		{
			stEventInDB.setInt(1, nEntryID);
			stEventInDB.setString(2, event.getName());
			stEventInDB.setTimestamp(3, dateToTimestamp(event.getBegins()));
			rs = stEventInDB.executeQuery();
			rs.next();
			nCount = rs.getInt(1);
			rs.close();
			rs = null;
		}
		catch (Exception e)
		{
			Logger.error(e);
			if (stEventInDB != null)
			{
				try
				{
					if (rs != null) rs.close();
//					stEventInDB.close();
				}
				catch (Exception e2){}
			}
		}
		return nCount > 0;
	}
	public boolean addEventToDB(EventGeneric mf, int nEntryID) throws SQLException
	{
		if (nEntryID == 0 || eventInDB(mf, nEntryID)) return false;

		int nEventID = addGenericEventToDB((EventGeneric)mf, nEntryID);
		if (mf instanceof EventConference) addConferenceToDB(nEventID, (EventConference)mf);
		else if (mf instanceof EventConcert) addConcertToDB(nEventID, (EventConcert)mf);
		return true;
	}
}
