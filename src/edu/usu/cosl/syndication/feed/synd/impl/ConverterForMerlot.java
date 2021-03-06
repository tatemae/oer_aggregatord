/*
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
package edu.usu.cosl.syndication.feed.synd.impl;

import com.sun.syndication.feed.WireFeed;
import com.sun.syndication.feed.module.impl.ModuleUtils;
import com.sun.syndication.feed.rss.Channel;
import com.sun.syndication.feed.rss.Description;
import com.sun.syndication.feed.rss.Image;
import com.sun.syndication.feed.rss.Item;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.Converter;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndImage;
import com.sun.syndication.feed.synd.SyndImageImpl;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;

import java.util.ArrayList;
import java.util.List;

/**
 */
public class ConverterForMerlot implements Converter {
    private String _type;

    public ConverterForMerlot() {
        this("sqi_merlot_lom");
    }

    protected ConverterForMerlot(String type) {
        _type = type;
    }

    public String getType() {
        return _type;
    }

    public void copyInto(WireFeed feed,SyndFeed syndFeed) {
        syndFeed.setModules(ModuleUtils.cloneModules(feed.getModules()));
        if (((List)feed.getForeignMarkup()).size() > 0) {
            syndFeed.setForeignMarkup(feed.getForeignMarkup());
        }
        syndFeed.setEncoding(feed.getEncoding());
        Channel channel = (Channel) feed;
        syndFeed.setTitle(channel.getTitle());
        syndFeed.setLink(channel.getLink());
        syndFeed.setDescription(channel.getDescription());

        Image image = channel.getImage();
        if (image!=null) {
            syndFeed.setImage(createSyndImage(image));
        }

        List items = channel.getItems();
        if (items!=null) {
            syndFeed.setEntries(createSyndEntries(items));
        }
    }

    protected SyndImage createSyndImage(Image rssImage) {
        SyndImage syndImage = new SyndImageImpl();
        syndImage.setTitle(rssImage.getTitle());
        syndImage.setUrl(rssImage.getUrl());
        syndImage.setLink(rssImage.getLink());
        return syndImage;
    }

    protected List createSyndEntries(List rssItems) {
        List<SyndEntry> syndEntries = new ArrayList<SyndEntry>();
        for (int i=0;i<rssItems.size();i++) {
            syndEntries.add(createSyndEntry((Item) rssItems.get(i)));
        }
        return syndEntries;
    }

    protected SyndEntry createSyndEntry(Item item) {
        SyndEntry syndEntry = new SyndEntryImpl();

        syndEntry.setModules(ModuleUtils.cloneModules(item.getModules()));
        
        if (((List)item.getForeignMarkup()).size() > 0) {
            syndEntry.setForeignMarkup(item.getForeignMarkup());
        }
        
        syndEntry.setUri(item.getUri());
        syndEntry.setLink(item.getLink());
        syndEntry.setTitle(item.getTitle());
        if (item.getDescription() != null)
        {
	        SyndContent description = new SyndContentImpl();
	        description.setValue(item.getDescription().getValue());
	        syndEntry.setDescription(description);
        }
        if (item.getAuthor() != null) syndEntry.setAuthor(item.getAuthor());
        if (item.getPubDate() != null) syndEntry.setPublishedDate(item.getPubDate());
        syndEntry.setCategories(item.getCategories());
        return syndEntry;
    }

    public WireFeed createRealFeed(SyndFeed syndFeed) {
        return createRealFeed(getType(),syndFeed);
    }

    protected WireFeed createRealFeed(String type,SyndFeed syndFeed) {
        Channel channel = new Channel(type);
        channel.setModules(ModuleUtils.cloneModules(syndFeed.getModules()));

        channel.setEncoding(syndFeed.getEncoding());

        channel.setTitle(syndFeed.getTitle());
        channel.setLink(syndFeed.getLink());
        channel.setDescription(syndFeed.getDescription());
        SyndImage sImage = syndFeed.getImage();
        if (sImage!=null) {
            channel.setImage(createRSSImage(sImage));
        }

        List sEntries = syndFeed.getEntries();
        if (sEntries!=null) {
            channel.setItems(createRSSItems(sEntries));
        }
        return channel;
    }

    protected Image createRSSImage(SyndImage sImage) {
        Image image = new Image();
        image.setTitle(sImage.getTitle());
        image.setUrl(sImage.getUrl());
        image.setLink(sImage.getLink());
        return image;
    }

    protected List createRSSItems(List sEntries) {
        List<Item> list = new ArrayList<Item>();
        for (int i=0;i<sEntries.size();i++) {
            list.add(createRSSItem((SyndEntry)sEntries.get(i)));
        }
        return list;
    }

    protected Item createRSSItem(SyndEntry sEntry) {
        Item item = new Item();
        item.setModules(ModuleUtils.cloneModules(sEntry.getModules()));
        item.setTitle(sEntry.getTitle());
        item.setLink(sEntry.getLink());
        item.setUri(sEntry.getUri());
        if (sEntry.getDescription() != null)
        {
	        Description description = new Description();
	        description.setValue(sEntry.getDescription().getValue());
	        item.setDescription(description);
        }
        return item;
    }

}
