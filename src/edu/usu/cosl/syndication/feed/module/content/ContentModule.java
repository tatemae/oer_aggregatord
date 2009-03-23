package edu.usu.cosl.syndication.feed.module.content;

import java.util.List;
import com.sun.syndication.feed.module.Module;
import com.sun.syndication.feed.CopyFrom;

import edu.usu.cosl.microformats.Microformat;

public abstract interface ContentModule extends Module, CopyFrom 
{
    String URI = "http://purl.org/rss/1.0/modules/content/";

    public List getMicroformats();
    public void setMicroformats(List<Microformat> mf);
    public void addMicroformat(Microformat mf);
}

