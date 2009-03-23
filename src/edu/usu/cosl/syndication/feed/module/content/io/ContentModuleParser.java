package edu.usu.cosl.syndication.feed.module.content.io;

import java.util.List;
import java.util.ListIterator;

import java.io.StringReader;

import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

import com.sun.syndication.feed.module.Module;
import com.sun.syndication.io.ModuleParser;
import com.sun.syndication.io.impl.XmlFixerReader;

import edu.usu.cosl.microformats.EventConcert;
import edu.usu.cosl.microformats.EventConference;
import edu.usu.cosl.microformats.EventGeneric;
import edu.usu.cosl.syndication.feed.module.content.ContentModule;
import edu.usu.cosl.syndication.feed.module.content.ContentModuleImpl;

public class ContentModuleParser implements ModuleParser {

	private static final String URI  = "http://purl.org/rss/1.0/modules/content/";
    private static final Namespace SB_NS = Namespace.getNamespace("http://www.structuredblogging.org/xmlns");


	public String getNamespaceUri() 
	{
		return URI;
	}

	public Module parse(Element element) 
	{
		ContentModule cm = new ContentModuleImpl();
		parseMicroformats(element,cm);
		return cm;
	}

	private void parseConferenceEvent(Element eParent, EventConference event)
	{
    	Element e = eParent.getChild("theme", SB_NS);
    	if (e != null) event.setTheme(e.getValue());

    	List l = eParent.getChildren("person", SB_NS);
    	for (ListIterator li = l.listIterator(); li.hasNext();)
    	{
    		Element pe = (Element)li.next();
    		String sName = pe.getValue();
    		String sRole = pe.getAttributeValue("role");
    		String sEmail = pe.getAttributeValue("email");
    		String sLink = pe.getAttributeValue("url");
    		String sPhone = pe.getAttributeValue("phone");
        	if (sName.length() > 0) event.addPerson(sName, sRole, sEmail, sLink, sPhone);
    	}
    	
    	e = eParent.getChild("registerby", SB_NS);
    	if (e != null) event.setRegisterBy(e.getValue());
    	
    	e = eParent.getChild("submitby", SB_NS);
    	if (e != null) event.setSubmitBy(e.getValue());
    	
	}
	private void parseConcertEvent(Element eParent, EventConcert event)
	{
    	Element e = eParent.getChild("performer", SB_NS);
    	if (e != null) event.setPerformer(e.getValue());
    	
    	e = eParent.getChild("tickets", SB_NS);
    	if (e != null) event.setTickets(e.getValue());
	}
	private void parseEvent(Element eParent, ContentModule cm)
	{
    	String sType = eParent.getAttributeValue("type");
    	EventGeneric event;
    	if ("event/conference".equals(sType)) event = new EventConference();
    	else if ("event/concert".equals(sType)) event = new EventConcert(); 
    	else event = new EventGeneric();

    	Element e = eParent.getChild("name", SB_NS);
    	if (e != null) event.setName(e.getValue());
    	
    	e = eParent.getChild("price", SB_NS);
    	if (e != null) event.setPrice(e.getValue());
    	
    	e = eParent.getChild("image", SB_NS);
    	if (e != null) event.setImage(e.getValue());
    	
    	e = eParent.getChild("location", SB_NS);
    	if (e != null)
    	{
    		String sValue = e.getAttributeValue("address");
    		event.setAddress(sValue);
    		String sLocation = sValue + ", ";
    		sValue = e.getAttributeValue("subaddress");
    		event.setSubAddress(sValue);
    		if (sValue != null ) sLocation += sValue + ", ";
    		sValue = e.getAttributeValue("city");
    		event.setCity(sValue);
    		if (sValue != null ) sLocation += sValue + ", ";
    		sValue = e.getAttributeValue("state");
    		event.setState(sValue);
    		if (sValue != null ) sLocation += sValue + ", ";
    		sValue = e.getAttributeValue("postcode");
    		event.setPostcode(sValue);
    		if (sValue != null ) sLocation += sValue + ", ";
    		sValue = e.getAttributeValue("country");
    		event.setCountry(sValue);
    		if (sValue != null ) sLocation += sValue + ", ";
    		
    		event.setLocation(sLocation);
    	}
    	
    	List l = eParent.getChildren("link", SB_NS);
    	for (ListIterator li = l.listIterator(); li.hasNext();)
    	{
    		Element le = (Element)li.next();
    		String sURL = le.getAttributeValue("url");
    		String sTitle = le.getValue();
        	if (sURL != null && sTitle != null) event.addLink(sURL, sTitle);
    	}

    	e = eParent.getChild("begins", SB_NS);
    	if (e != null) event.setBegins(e.getValue());
    	
    	e = eParent.getChild("ends", SB_NS);
    	if (e != null) event.setEnds(e.getValue());
    	
    	e = eParent.getChild("description", SB_NS);
    	if (e != null) event.setDescription(e.getValue());
    	
    	e = eParent.getChild("tags", SB_NS);
    	if (e != null) event.setTags(e.getValue());
    	
    	if (event instanceof EventConference) parseConferenceEvent(eParent, (EventConference)event);
    	else if (event instanceof EventConcert) parseConcertEvent(eParent, (EventConcert)event);
    	
    	cm.addMicroformat(event);
    }
    
    private void parseMicroformats(Element eParent, ContentModule cm)
    {
        try 
        {
        	// the structured blogging plugin puts microformat data in a content:encoded tag
            Element e = eParent.getChild("encoded",org.jdom.Namespace.getNamespace("http://purl.org/rss/1.0/modules/content/"));
            if (e != null)
            {
            	// the structured blogging plugin hides microformat data inside of a script tag
            	// for more information see: http://structuredblogging.org/resources.php
            	// for some reason bad HTML can get inserted before the script tag, so we skip past it
            	String sData = e.getValue();
            	int nStart = sData.indexOf("<script");
            	if (nStart != -1)
            	{
            		sData = sData.substring(nStart);
            		
            		// build a dom tree from the contents of the script tag
            		e = new SAXBuilder().build(new XmlFixerReader(new StringReader(sData))).getRootElement();
            		if (e != null)
            		{
	            		// contains subnode 
			            e = e.getChild("subnode",org.jdom.Namespace.getNamespace("http://www.structuredblogging.org/xmlns#subnode"));
	            		if (e != null)
	            		{
		            		// contains xml-structured-blog-entry
	            			Element xsbe = e.getChild("xml-structured-blog-entry",SB_NS);
	            			if (xsbe != null)
	            			{
	            				// do we have an event?
	            				e = xsbe.getChild("event", SB_NS);
	            				if (e != null) parseEvent(e, cm);

	            				// other microformats: review, card
	            			}
	            		}
            		}
            	}
            }
        }
        catch (Exception ex) {System.out.println(ex);}
    }
    
}

