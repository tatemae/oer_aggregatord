package edu.usu.cosl.microformats;

import com.sun.syndication.feed.impl.ObjectBean;

public class EventLink implements Cloneable 
{
    private ObjectBean _objBean;
    private static final long serialVersionUID = 2341126;

	String _url;
	String _title;

	public EventLink(String sURL, String sTitle)
	{
		_objBean = new ObjectBean(EventLink.class, this, null);
		_url = sURL;
		_title = sTitle;
	}
	
	public void setURL(String sURL){_url = sURL;}
	public void setTitle(String sTitle){_title = sTitle;}

	public String getURL(){return _url;}
	public String getTitle(){return _title;}

	public final Object clone() throws CloneNotSupportedException{return _objBean.clone();}
    public EventLink(){_objBean = new ObjectBean(EventLink.class, this, null);}
}
