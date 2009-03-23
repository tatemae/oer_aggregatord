package edu.usu.cosl.microformats;

import com.sun.syndication.feed.impl.ObjectBean;

public class EventPerson implements Cloneable 
{
    private ObjectBean _objBean;
    private static final long serialVersionUID = 2341127;

	String _name;
	String _role;
	String _email;
	String _link;
	String _phone;
	
	public EventPerson(String sName, String sRole, String sEmail, String sLink, String sPhone)
	{
		_objBean = new ObjectBean(EventPerson.class, this, null);
		_name = sName;
		_role = sRole;
		_email = sEmail;
		_link = sLink;
		_phone = sPhone;
	}
	
	public void setName(String sName){_name = sName;}
	public void setRole(String sRole){_role = sRole;}
	public void setEmail(String sEmail){_email = sEmail;}
	public void setLink(String sLink){_link = sLink;}
	public void setPhone(String sPhone){_phone = sPhone;}

	public String getName(){return _name;}
	public String getRole(){return _role;}
	public String getEmail(){return _email;}
	public String getLink(){return _link;}
	public String getPhone(){return _phone;}

	public final Object clone() throws CloneNotSupportedException{return _objBean.clone();}
    public EventPerson(){_objBean = new ObjectBean(EventPerson.class, this, null);}
}
