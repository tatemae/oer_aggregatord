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
package edu.usu.cosl.syndication.io.impl;

import edu.usu.cosl.syndication.feed.module.DCTermsModuleImpl;
import com.sun.syndication.feed.module.Module;
import edu.usu.cosl.syndication.feed.module.DCTermsModule;
import com.sun.syndication.io.ModuleParser;
import com.sun.syndication.io.WireFeedParser;
import com.sun.syndication.io.impl.DateParser;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Namespace;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Parser for the Dublin Core module.
 */
public class DCTermsModuleParser implements ModuleParser {

    private static final String RDF_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String TAXO_URI = "http://purl.org/rss/1.0/modules/taxonomy/";

    private static final Namespace DCT_NS = Namespace.getNamespace(DCTermsModule.URI);
    private static final Namespace RDF_NS = Namespace.getNamespace(RDF_URI);
    private static final Namespace TAXO_NS = Namespace.getNamespace(TAXO_URI);

    public final String getNamespaceUri() {
        return DCTermsModule.URI;
    }

    private final Namespace getDCTermsNamespace() {
        return DCT_NS;
    }

    private final Namespace getRDFNamespace() {
        return RDF_NS;
    }

    private final Namespace getTaxonomyNamespace() {
        return TAXO_NS;
    }

    /**
     * Parse an element tree and return the module found in it.
     * <p>
     * @param dcRoot the root element containing the module elements.
     * @return the module parsed from the element tree, <i>null</i> if none.
     */
    public Module parse(Element dcRoot) {
        boolean foundSomething = false;
        DCTermsModule dctm = new DCTermsModuleImpl();

        List eList = dcRoot.getChildren("created", getDCTermsNamespace());
        if (eList.size() > 0) {
            foundSomething = true;
            dctm.setCreatedDates(parseElementListDate(eList));
        }
        eList = dcRoot.getChildren("modified", getDCTermsNamespace());
        if (eList.size() > 0) {
            foundSomething = true;
            dctm.setModifiedDates(parseElementListDate(eList));
        }

        return (foundSomething) ? dctm : null;
    }

    /**
     * Utility method to parse a taxonomy from an element.
     * <p>
     * @param desc the taxonomy description element.
     * @return the string contained in the resource of the element.
     */
    protected final String getTaxonomy(Element desc) {
        String d = null;
        Element taxo = desc.getChild("topic", getTaxonomyNamespace());
        if (taxo!=null) {
            Attribute a = taxo.getAttribute("resource", getRDFNamespace());
            if (a!=null) {
                d = a.getValue();
            }
        }
        return d;
    }

    /**
     * Utility method to parse a list of subjects out of a list of elements.
     * <p>
     * @param eList the element list to parse.
     * @return a list of subjects parsed from the elements.
     */
    protected final List parseSubjects(List eList) {
        List subjects = new ArrayList();
//        for (Iterator i = eList.iterator(); i.hasNext();) {
//            Element eSubject = (Element) i.next();
//            Element eDesc = eSubject.getChild("Description", getRDFNamespace());
//            if (eDesc != null) {
//                String taxonomy = getTaxonomy(eDesc);
//                List eValues = eDesc.getChildren("value", getRDFNamespace());
//                for (Iterator v = eValues.iterator(); v.hasNext();) {
//                    Element eValue = (Element) v.next();
//                    DCSubject subject = new DCSubjectImpl();
//                    subject.setTaxonomyUri(taxonomy);
//                    subject.setValue(eValue.getText());
//                    subjects.add(subject);
//                }
//            } else {
//                DCSubject subject = new DCSubjectImpl();
//                subject.setValue(eSubject.getText());
//                subjects.add(subject);
//            }
//        }

        return subjects;
    }

    /**
     * Utility method to parse a list of strings out of a list of elements.
     * <p>
     * @param eList the list of elements to parse.
     * @return the list of strings
     */
    protected final List parseElementList(List eList) {
        List values= new ArrayList();
        for (Iterator i = eList.iterator(); i.hasNext();) {
            Element e = (Element) i.next();
            values.add(e.getText());
        }

        return values;
    }

    /**
     * Utility method to parse a list of dates out of a list of elements.
     * <p>
     * @param eList the list of elements to parse.
     * @return the list of dates.
     */
    protected final List parseElementListDate(List eList) {
        List values = new ArrayList();
        for (Iterator i = eList.iterator(); i.hasNext();) {
            Element e = (Element) i.next();
            values.add(DateParser.parseDate(e.getText()));
        }

        return values;
    }
}
