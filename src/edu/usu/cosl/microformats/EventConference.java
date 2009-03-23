package edu.usu.cosl.microformats;

import java.util.List;
import java.util.ArrayList;

import com.sun.syndication.feed.impl.ObjectBean;

public class EventConference extends EventGeneric implements Cloneable 
{
    private ObjectBean _objBean;
    private static final long serialVersionUID = 2341128;

    private String _theme;
    private ArrayList<EventPerson> _people = new ArrayList<EventPerson>();
    private String _registerby;
    private String _submitby;

    public void setTheme(String sTheme){_theme = sTheme;}
    public void addPerson(String sPerson, String sRole, String sEmail, String sLink, String sPhone)
    {_people.add(new EventPerson(sPerson, sRole, sEmail, sLink, sPhone));}
    public void setPeople(List lPeople){_people = (ArrayList<EventPerson>)lPeople;}
    public void setRegisterBy(String sRegisterBy){_registerby = sRegisterBy;}
    public void setSubmitBy(String sSubmitBy){_submitby = sSubmitBy;}

    public String getTheme(){return _theme;}
    public List getPeople(){return _people;}
    public String getRegisterBy(){return _registerby;}
    public String getSubmitBy(){return _submitby;}

    public Object clone() throws CloneNotSupportedException{return _objBean.clone();}
    public EventConference(){_objBean = new ObjectBean(EventConference.class, this, null);}

}

