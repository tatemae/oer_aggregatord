package edu.usu.cosl.microformats;

import com.sun.syndication.feed.impl.ObjectBean;

public class EventConcert extends EventGeneric implements Cloneable 
{
    private ObjectBean _objBean;
    private static final long serialVersionUID = 2341129;

    private String _performer;
    private String _tickets;

    public void setPerformer(String sPerformer){_performer = sPerformer;}
    public void setTickets(String sTickets){_tickets = sTickets;}

    public String getPerformer(){return _performer;}
    public String getTickets(){return _tickets;}

    public Object clone() throws CloneNotSupportedException{return _objBean.clone();}
    public EventConcert(){_objBean = new ObjectBean(EventConcert.class, this, null);}
}

