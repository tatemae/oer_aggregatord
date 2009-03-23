/*
 * Based on com.sun.syndication.io.impl.RSS090Parser
 * 
 * Copyright 2004 Sun Microsystems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package edu.usu.cosl.syndication.io.impl;

import com.sun.syndication.feed.WireFeed;
import com.sun.syndication.feed.rss.Channel;
import com.sun.syndication.feed.rss.Item;
import com.sun.syndication.feed.rss.Description;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.impl.BaseWireFeedParser;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 */
public class MerlotParser extends BaseWireFeedParser {

    private static final String SQI_MERLOT_URI = "";
    
    private static final Namespace SQI_MERLOT_NS = Namespace.getNamespace(SQI_MERLOT_URI);

    public MerlotParser() {
        this("sqi_merlot_lom");
    }

    protected MerlotParser(String type) {
        super(type);
    }

    public boolean isMyType(Document document) 
    {
        Element oaiRoot = document.getRootElement();
        Namespace defaultNS = oaiRoot.getNamespace();
        if (defaultNS.getPrefix().length() == 0)
        {
        	Element lom = oaiRoot.getChild("lom", getSQIMerlotNamespace());
        	if (lom != null) return true;
        	if (oaiRoot.getChildren().isEmpty()) return true;
        }
        return false;
    }

    public WireFeed parse(Document document, boolean validate) throws IllegalArgumentException,FeedException {
        if (validate) {
            validateFeed(document);
        }
        Element oaiRoot = document.getRootElement();
        return parseRecordList(oaiRoot);
    }

    protected void validateFeed(Document document) throws FeedException {
        // TBD
        // here we have to validate the Feed against a schema or whatever
        // not sure how to do it
        // one posibility would be to inject our own schema for the feed (they don't exist out there)
        // to the document, produce an ouput and attempt to parse it again with validation turned on.
        // otherwise will have to check the document elements by hand.
    }

    /**
     * Returns the namespace used by RDF elements in document of the RSS version the parser supports.
     * <P>
     * This implementation returns the EMTPY namespace.
     * <p>
     *
     * @return returns the EMPTY namespace.
     */
    protected Namespace getSQIMerlotNamespace() {
        return SQI_MERLOT_NS;
    }
    /**
     * Parses the root element of an RSS document into a Channel bean.
     * <p/>
     * It reads title, link and description and delegates to parseImage, parseItems
     * and parseTextInput. This delegation always passes the root element of the RSS
     * document as different RSS version may have this information in different parts
     * of the XML tree (no assumptions made thanks to the specs variaty)
     * <p/>
     *
     * @param oaiRoot the root element of the RSS document to parse.
     * @return the parsed Channel bean.
     */
    protected WireFeed parseRecordList(Element oaiRoot) throws FeedException
    {
        Channel channel = new Channel(getType());
        channel.setItems(parseRecords(oaiRoot));
        return channel;
    }


    /**
     * This method exists because RSS0.90 and RSS1.0 have the 'item' elements under the root elemment.
     * And RSS0.91, RSS0.02, RSS0.93, RSS0.94 and RSS2.0 have the item elements under the 'channel' element.
     * <p/>
     */
    protected List getRecords(Element oaiRoot) {
        return oaiRoot.getChildren("lom", getSQIMerlotNamespace());
    }

    /**
     * Parses the root element of an RSS document looking for all items information.
     * <p/>
     * It iterates through the item elements list, obtained from the getItems() method, and invoke parseItem()
     * for each item element. The resulting RSSItem of each item element is stored in a list.
     * <p/>
     *
     * @param oaiRoot the root element of the RSS document to parse for all items information.
     * @return a list with all the parsed RSSItem beans.
     */
    protected List parseRecords(Element recordList)  {
        Collection eItems = getRecords(recordList);

        List items = new ArrayList();
        int nItem = 1;
        for (Iterator i=eItems.iterator();i.hasNext();) {
            Element eItem = (Element) i.next();
            Item item = parseRecord(recordList,eItem);
            if (item != null) items.add(item);
        }
        return items;
    }

    private void parseLOMRecord(Item item, Element eRecord)
    {
        ArrayList<MarkupProperty> lMarkup = new ArrayList<MarkupProperty>();
        
    	// general
        Element eGeneral = eRecord.getChild("general", getSQIMerlotNamespace());
        if (eGeneral != null)
        {
        	// oai identifier
            Element eIdentifier = eGeneral.getChild("identifier", getSQIMerlotNamespace());
            if (eIdentifier != null)
            {
            	String sOAIIdentifier = eIdentifier.getChildText("entry", getSQIMerlotNamespace());
            	if (sOAIIdentifier != null)
            	{
	    	        lMarkup.add(new MarkupProperty("oai_identifier", sOAIIdentifier));
	    	        item.setLink(sOAIIdentifier);
	    	        item.setUri(sOAIIdentifier);
            	}
            }
            // title
            Element eTitle = eGeneral.getChild("title", getSQIMerlotNamespace());
            if (eTitle != null)
            {
            	String sTitle = eTitle.getChildText("string", getSQIMerlotNamespace());
            	if (sTitle != null) item.setTitle(sTitle);
            }
            // description
            Element eDescription = eGeneral.getChild("description", getSQIMerlotNamespace());
            if (eDescription != null)
            {
            	String sDescription = eDescription.getChildText("string", getSQIMerlotNamespace());
            	if (sDescription != null)
            	{
                	Description description = new Description();
                	description.setValue(sDescription);
            		item.setDescription(description);
            	}
            }
        }
        // technical
        Element eTechnical = eRecord.getChild("technical", getSQIMerlotNamespace());
        if (eTechnical != null)
        {
            String sLocation = eTechnical.getChildTextTrim("location", getSQIMerlotNamespace());
	        lMarkup.add(new MarkupProperty("direct_link", sLocation));
        }
        item.setForeignMarkup(lMarkup);
    }
    
    /**
     * Parses an item element of an RSS document looking for item information.
     * <p/>
     * It reads title and link out of the 'item' element.
     * <p/>
     *
     * @param oaiRoot the root element of the RSS document in case it's needed for context.
     * @param eItem the item element to parse.
     * @return the parsed RSSItem bean.
     */
    protected Item parseRecord(Element oaiRoot, Element eLOM) 
    {
    	// create an item for storing the metadata for the record
    	Item item = new Item();
       	parseLOMRecord(item, eLOM);
        return item;
    }
}
