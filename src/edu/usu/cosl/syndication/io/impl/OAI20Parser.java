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
import com.sun.syndication.feed.synd.SyndCategoryImpl;
import com.sun.syndication.feed.rss.Channel;
import com.sun.syndication.feed.rss.Item;
import com.sun.syndication.feed.rss.Description;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.impl.BaseWireFeedParser;
import com.sun.syndication.io.impl.DateParser;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 */
public class OAI20Parser extends BaseWireFeedParser {

    private static final String OAI_URI = "http://www.openarchives.org/OAI/2.0/";
    private static final String OAI_DC_URI = "http://www.openarchives.org/OAI/2.0/oai_dc/";
    private static final String NSDL_DC_URI = "http://ns.nsdl.org/nsdl_dc_v1.02/";
    private static final String LOM_URI = "http://ltsc.ieee.org/xsd/LOM";
    private static final String OERR_URI = "http://www.oercommons.org/oerr";
    private static final String DC_URI = "http://purl.org/dc/elements/1.1/";
    private static final String PROVENANCE_URI = "http://ns.nsdl.org/provenance_about_v1.00";
    
    private static final Namespace OAI_NS = Namespace.getNamespace(OAI_URI);
    private static final Namespace OAI_DC_NS = Namespace.getNamespace(OAI_DC_URI);
    private static final Namespace NSDL_DC_NS = Namespace.getNamespace(NSDL_DC_URI);
    private static final Namespace LOM_NS = Namespace.getNamespace(LOM_URI);
    private static final Namespace OERR_NS = Namespace.getNamespace(OERR_URI);
    private static final Namespace DC_NS = Namespace.getNamespace(DC_URI);
    private static final Namespace PROVENANCE_NS = Namespace.getNamespace(PROVENANCE_URI);

    public OAI20Parser() {
        this("oai_2.0");
    }

    protected OAI20Parser(String type) {
        super(type, OAI_NS);
    }

    public boolean isMyType(Document document) 
    {
        Element oaiRoot = document.getRootElement();
        Namespace defaultNS = oaiRoot.getNamespace();
        if (defaultNS ==null || !defaultNS.equals(getOAINamespace())) return false;
    	Element request = oaiRoot.getChild("request", getOAINamespace());
    	String sVerb = request.getAttribute("verb").getValue();
    	return "ListRecords".equals(sVerb);
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
    protected Namespace getOAINamespace() {
        return OAI_NS;
    }
    protected Namespace getOAIDCNamespace() {
        return OAI_DC_NS;
    }
    protected Namespace getNSDLDCNamespace() {
        return NSDL_DC_NS;
    }
    protected Namespace getLOMNamespace() {
        return LOM_NS;
    }
    protected Namespace getOERRNamespace() {
        return OERR_NS;
    }
    protected Namespace getDCNamespace() {
        return DC_NS;
    }
    protected Namespace getProvenanceNamespace() {
        return PROVENANCE_NS;
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
        Element eChannel = oaiRoot.getChild("ListRecords", getOAINamespace()); 

        // if there are no new records, there will be no ListRecords element
        if (eChannel == null)
        {
            String sError = oaiRoot.getChildText("error", getOAINamespace());
            System.out.println(sError);
            return null;
        }
        
        Channel channel = new Channel(getType());
        channel.setItems(parseRecords(eChannel));
        
        // store any resumption token as foreign markup
        ArrayList<String> lMarkup = new ArrayList<String>();
        String sResumptionToken = eChannel.getChildTextTrim("resumptionToken", getOAINamespace());
        if (sResumptionToken != null)
        {
	        lMarkup.add(sResumptionToken);
	        channel.setForeignMarkup(lMarkup);
        }

        return channel;
    }


    /**
     * This method exists because RSS0.90 and RSS1.0 have the 'item' elements under the root elemment.
     * And RSS0.91, RSS0.02, RSS0.93, RSS0.94 and RSS2.0 have the item elements under the 'channel' element.
     * <p/>
     */
    protected List getRecords(Element oaiRoot) {
        return oaiRoot.getChildren("record", getOAINamespace());
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

    private void parseNSDLRecord(Item item, Element eRecord, Element eDC, List lMarkup)
    {
    	String sLanguage = eDC.getChildText("language", getDCNamespace());
    	if (sLanguage != null) lMarkup.add(new MarkupProperty("language", sLanguage));
    }

    private String getVCardFullName(String sName)
    {
    	int nStart = sName.indexOf("FN:");
    	if (nStart == -1) return sName;
    	nStart += 3;
    	int nEnd = sName.indexOf("\n", nStart);
    	if (nEnd == -1) return sName;
    	return sName.substring(nStart, nEnd);
    }
    
    private void parseLOMRecord(Item item, Element eRecord, Element eLOM, List lMarkup)
    {
    	// general
        Element eGeneral = eLOM.getChild("general", getLOMNamespace());
        if (eGeneral != null)
        {
            // title
            Element eTitle = eGeneral.getChild("title", getLOMNamespace());
            if (eTitle != null)
            {
            	String sTitle = eTitle.getChildText("string", getLOMNamespace());
            	if (sTitle != null) item.setTitle(sTitle);
            }
            // description
            Element eDescription = eGeneral.getChild("description", getLOMNamespace());
            if (eDescription != null)
            {
            	String sDescription = eDescription.getChildText("string", getLOMNamespace());
            	if (sDescription != null)
            	{
            		Description description = new Description();
            		description.setValue(sDescription);
            		item.setDescription(description);
            	}
            }
        }
        // lifecycle
        Element eLifeCycle = eLOM.getChild("lifeCycle", getLOMNamespace());
        if (eLifeCycle != null)
        {
        	Element eContribute = eLifeCycle.getChild("contribute", getLOMNamespace());
        	if (eContribute != null)
        	{
            	Element eRole = eContribute.getChild("role", getLOMNamespace());
        		if (eRole != null && "author".equals(eRole.getChildText("value", getLOMNamespace())))
        		{
        			String sAuthor = eContribute.getChildText("entity", getLOMNamespace());
        			if (sAuthor != null) item.setAuthor(getVCardFullName(sAuthor));
        		}
        	}
        }
        // meta metadata
        String sLanguage = null;
        Element eMetaMetadata = eLOM.getChild("metaMetadata", getLOMNamespace());
        if (eMetaMetadata != null)
        {
            // language
        	sLanguage = eMetaMetadata.getChildText("language", getLOMNamespace());
	        if (sLanguage != null) lMarkup.add(new MarkupProperty("language", sLanguage));
        }
        // technical
        Element eTechnical = eLOM.getChild("technical", getLOMNamespace());
        if (eTechnical != null)
        {
            String sLocation = eTechnical.getChildTextTrim("location", getLOMNamespace());
            item.setLink(sLocation);
        }
        // classification
        ArrayList<SyndCategoryImpl> lCategories = new ArrayList<SyndCategoryImpl>();
        Element eClassification = eLOM.getChild("classification", getLOMNamespace());
        if (eClassification != null)
        {
            Element eTaxonPath = eClassification.getChild("taxonPath", getLOMNamespace());
            if (eTaxonPath != null)
            {
                Element eTaxon = eTaxonPath.getChild("taxon", getLOMNamespace());
                if (eTaxon != null)
                {
                    Element eEntry = eTaxon.getChild("entry", getLOMNamespace());
                    if (eEntry != null)
                    {
		                List lStrings = eEntry.getChildren("string", getLOMNamespace());
		                for (int nString = 0; nString < lStrings.size(); nString++)
		                {
		                    Element eString = (Element)lStrings.get(nString);
		                    if (eString != null && eString.getAttributeValue("language").equals(sLanguage))
		                    {
		                    	SyndCategoryImpl category = new SyndCategoryImpl();
		                    	category.setName(eString.getText());
		                    	lCategories.add(category);
		                    }
		                }
                    }
                }
            }
        }
        if (lCategories.size() > 0) item.setCategories(lCategories);
    }
    
    private void parseOERRRecord(Item item, Element eRecord, Element eOERR, List lMarkup)
    {
        Element eHeader = eRecord.getChild("header", getOAINamespace());
        if (eHeader != null)
        {
        	// set specifications
        	List lSpecs = eHeader.getChildren("setSpec", getOAINamespace());
            for (int nSpec = 0; nSpec < lSpecs.size(); nSpec++)
            {
                Element eSpec = (Element)lSpecs.get(nSpec);
                if (eSpec != null)
                {
                	String sSpec = eSpec.getText();
                	if (sSpec != null) {
                		if (sSpec.startsWith("language:"))
                    	{
    	                	String sLanguage = sSpec.substring(9);
    	                	lMarkup.add(new MarkupProperty("language", sLanguage));
                    	}
                		else if (sSpec.startsWith("content_type:"))
                    	{
    	                	String sContentType = sSpec.substring(13);
    	                	lMarkup.add(new MarkupProperty("content_type", sContentType));
                    	}
                		else if (sSpec.startsWith("material_types:"))
                    	{
    	                	String sMaterialType = sSpec.substring(15);
    	                	lMarkup.add(new MarkupProperty("material_types", sMaterialType));
                    	}
                		else if (sSpec.startsWith("languages:"))
                    	{
    	                	String sLanguages = sSpec.substring(10);
    	                	lMarkup.add(new MarkupProperty("languages", sLanguages));
                    	}
                	}
                }
            }
        }
    	// direct link
    	String sDirectLink = eOERR.getChildText("url", getOERRNamespace());
    	if (sDirectLink != null) lMarkup.add(new MarkupProperty("direct_link", sDirectLink));
    	
        // title
        String sTitle = eOERR.getChildText("title", getOERRNamespace());
        if (sTitle != null) item.setTitle(sTitle);
        	
        // abstract (description)
        String sAbstract = eOERR.getChildText("abstract", getOERRNamespace());
        Description description = new Description();
        description.setValue(sAbstract);
        item.setDescription(description);
        
        // technical
        String sLocation = eOERR.getChildTextTrim("oer_url", getOERRNamespace());
        item.setLink(sLocation);
        
        // keywords
        ArrayList<SyndCategoryImpl> lCategories = new ArrayList<SyndCategoryImpl>();
        Element eTaxon = eOERR.getChild("keywords", getOERRNamespace());
        if (eTaxon != null)
        {
            List lEntries = eTaxon.getChildren("keyword", getOERRNamespace());
            for (int nEntry = 0; nEntry < lEntries.size(); nEntry++)
            {
                Element eEntry = (Element)lEntries.get(nEntry);
                if (eEntry != null)
                {
                	SyndCategoryImpl category = new SyndCategoryImpl();
                	category.setName(eEntry.getText());
                	lCategories.add(category);
                }
            }
        }
        if (lCategories.size() > 0) item.setCategories(lCategories);

        // authors
        ArrayList<String> lAuthorList = new ArrayList<String>();
        Element eAuthors = eOERR.getChild("authors", getOERRNamespace());
        if (eAuthors != null)
        {
            List lAuthors = eAuthors.getChildren("author", getOERRNamespace());
            for (int nAuthor = 0; nAuthor < lAuthors.size(); nAuthor++)
            {
                Element eAuthor = (Element)lAuthors.get(nAuthor);
                if (eAuthor != null)
                {
                	lAuthorList.add(eAuthor.getText());
                }
            }
        }
        String sAuthors = lAuthorList.toString();
        if (lAuthorList.size() > 0) item.setAuthor(sAuthors.substring(1,sAuthors.length()-1));
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
    private void parseHeader(Item item, Element eRecord, List lMarkup)
    {
        // header
        Element eHeader = eRecord.getChild("header", getOAINamespace());
        if (eHeader != null)
        {
        	// status
        	String sStatus = eHeader.getAttributeValue("status");
        	if (sStatus != null) lMarkup.add(new MarkupProperty("status", sStatus));

        	// oai identifier
            String sOAIIdentifier = eHeader.getChildTextTrim("identifier", getOAINamespace());
            if (sOAIIdentifier != null) lMarkup.add(new MarkupProperty("oai_identifier", sOAIIdentifier));
            
            // date
            String sDateStamp = eHeader.getChildTextTrim("datestamp", getOAINamespace());
            if (sDateStamp != null) item.setPubDate(DateParser.parseDate(sDateStamp));
        }
    }
    
    protected Item parseRecord(Element oaiRoot, Element eRecord) 
    {
    	// create an item for storing the metadata for the record
    	Item item = new Item();
        List lMarkup = new ArrayList<String>();

    	// get the published date and oai identifier
        parseHeader(item, eRecord, lMarkup);
    	
    	// if a record has been deleted, it won't have a metadata section
    	Element eMetadata = eRecord.getChild("metadata", getOAINamespace());
    	if (eMetadata != null)
    	{
	    	// parse
	        item.setModules(parseItemModules(eRecord));
	        
	        // if nsdl dublin core is present, parse it
	        Element eDC = eMetadata.getChild("nsdl_dc", getNSDLDCNamespace());
	        if (eDC == null) eDC = eMetadata.getChild("dc", getOAIDCNamespace());
	        if (eDC != null)
	        {
	        	item.setModules(parseItemModules(eDC));
	        	parseNSDLRecord(item, eRecord, eDC, lMarkup);
	        }
	        else 
	        {
	            Element eLOM = eMetadata.getChild("lom", getLOMNamespace());
	            if (eLOM != null)
	            {
		        	item.setModules(parseItemModules(eLOM));
		        	parseLOMRecord(item, eRecord, eLOM, lMarkup);
	            }
	            else
	            {
	                Element eOERR = eMetadata.getChild("oerr", getOERRNamespace());
	                if (eOERR != null)
	                {
	                	parseOERRRecord(item, eRecord, eOERR, lMarkup);
	                }
	            }
	        }
    	}
        if (lMarkup.size() > 0) item.setForeignMarkup(lMarkup);
        return item;
    }
}
