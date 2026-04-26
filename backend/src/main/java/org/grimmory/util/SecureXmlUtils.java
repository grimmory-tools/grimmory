package org.grimmory.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@Slf4j
@UtilityClass
public class SecureXmlUtils {

    // DocumentBuilderFactory is thread-safe after configuration cache one per namespace-aware mode
    private static final DocumentBuilderFactory NS_AWARE_FACTORY;
    private static final DocumentBuilderFactory NON_NS_AWARE_FACTORY;

    static {
        try {
            NS_AWARE_FACTORY = buildFactory(true);
            NON_NS_AWARE_FACTORY = buildFactory(false);
        } catch (ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static DocumentBuilderFactory buildFactory(boolean namespaceAware) throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);

        // Prevent XXE attacks
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        return factory;
    }

    private static DocumentBuilderFactory getFactory(boolean namespaceAware) {
        return namespaceAware ? NS_AWARE_FACTORY : NON_NS_AWARE_FACTORY;
    }

    public static DocumentBuilder createSecureDocumentBuilder(boolean namespaceAware) 
            throws ParserConfigurationException {
        // newDocumentBuilder() is NOT thread-safe must create new builder each time
        return getFactory(namespaceAware).newDocumentBuilder();
    }
}
