package edu.usu.cosl.microformats;

import java.util.List;
import java.util.ArrayList;

import com.sun.syndication.feed.impl.ObjectBean;

public class EventGeneric extends Microformat implements Cloneable 
{
    private ObjectBean _objBean;
    private static final long serialVersionUID = 2341125;

    private String _name;
    private String _price;
    private String _image;
    private String _address;
    private String _subaddress;
    private String _city;
    private String _state;
    private String _country;
    private String _postcode;
    private ArrayList<EventLink> _links = new ArrayList<EventLink>();
    private String _begins;
    private String _ends;
    private String _description;
    private String _tags;
    private String _duration;
    private String _location;

    public void setName(String sName){_name = sName;}
    public void setPrice(String sPrice){_price = sPrice;}
    public void setImage(String sImage){_image = sImage;}
    public void setAddress(String sAddress){_address = sAddress;}
    public void setSubAddress(String sSubAddress){_subaddress = sSubAddress;}
    public void setCity(String sCity){_city = sCity;}
    public void setState(String sState){_state = sState;}
    public void setCountry(String sCountry){_country = sCountry;}
    public void setPostcode(String sPostcode){_postcode = sPostcode;}
    public void addLink(String sURL, String sTitle){_links.add(new EventLink(sURL, sTitle));}
    public void setLinks(List lLinks){_links = (ArrayList<EventLink>)lLinks;}
    public void setBegins(String sBegins){_begins = sBegins;}
    public void setEnds(String sEnds){_ends = sEnds;}
    public void setDescription(String sDescription){_description = sDescription;}
    public void setTags(String sTags){_tags = sTags;}
    public void setDuration(String sDuration){_duration = sDuration;}
    public void setLocation(String sLocation){_location = sLocation;}

    public String getName(){return _name;}
    public String getPrice(){return _price;}
    public String getImage(){return _image;}
    public String getAddress(){return _address;}
    public String getSubAddress(){return _subaddress;}
    public String getCity(){return _city;}
    public String getState(){return _state;}
    public String getCountry(){return _country;}
    public String getPostcode(){return _postcode;}
    public List getLinks(){return _links;}
    public String getBegins(){return _begins;}
    public String getEnds(){return _ends;}
    public String getDescription(){return _description;}
    public String getTags(){return _tags;}
    public String getDuration(){return _duration;}
    public String getLocation(){return _location;}

    public Object clone() throws CloneNotSupportedException{return _objBean.clone();}
    public EventGeneric(){_objBean = new ObjectBean(EventGeneric.class, this, null);}

}

