package edu.usu.cosl.syndication.io.impl;

public class MarkupProperty {
	private String sName;
	private Object oValue;
	public MarkupProperty(String sName, Object oValue){this.sName = sName; this.oValue = oValue;}
	public void setName(String sName){this.sName = sName;}
	public void setValue(Object oValue){this.oValue = oValue;}
	public String getName(){return sName;}
	public Object getValue(){return oValue;}
}
