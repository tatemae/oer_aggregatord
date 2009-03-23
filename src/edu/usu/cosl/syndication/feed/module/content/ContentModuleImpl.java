package edu.usu.cosl.syndication.feed.module.content;

import java.util.List;
import java.util.ArrayList;

import com.sun.syndication.feed.module.ModuleImpl;

import edu.usu.cosl.microformats.Microformat;

public class ContentModuleImpl extends ModuleImpl implements ContentModule 
{
    private static final long serialVersionUID = 2341123;

    private List<Microformat> _microformats = new ArrayList<Microformat>();

    public ContentModuleImpl(){super(ContentModuleImpl.class, URI);}
    public final void copyFrom(Object obj){}
    public Class getInterface(){return ContentModule.class;}
    
    public List getMicroformats(){return _microformats;}
    public void setMicroformats(List<Microformat> list){_microformats = list;}
    public void addMicroformat(Microformat mf){_microformats.add(mf);}
}

