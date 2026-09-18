package org.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.List;

@Slf4j
@UtilityClass
public class SecureXmlUtils {

    // DocumentBuilderFactory is thread-safe after configuration cache one per namespace-aware mode
    private static final DocumentBuilderFactory NS_AWARE_FACTORY;
    private static final DocumentBuilderFactory NON_NS_AWARE_FACTORY;

    private static final List<String> DISABLED_FEATURE_FLAGS = List.of(
        // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
        // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
        // JDK7+ - http://xml.org/sax/features/external-general-entities
        //This feature has to be used together with the following one, otherwise it will not protect you from XXE for sure
        "http://xml.org/sax/features/external-general-entities",

        // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
        // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
        // JDK7+ - http://xml.org/sax/features/external-parameter-entities
        //This feature has to be used together with the previous one, otherwise it will not protect you from XXE for sure
        "http://xml.org/sax/features/external-parameter-entities",

        // Disable external DTDs as well
        "http://apache.org/xml/features/nonvalidating/load-external-dtd"
    );

    static {
        NS_AWARE_FACTORY = buildFactory(true);
        NON_NS_AWARE_FACTORY = buildFactory(false);
    }

    private static DocumentBuilderFactory buildFactory(boolean namespaceAware) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);

        // We cannot explicitly disable DTDs but we can disable a number of feature flags that allow
        // users to exploit issues & enable feature flags to protect users.
        for (var featureFlag : DISABLED_FEATURE_FLAGS) {
            try {
                factory.setFeature(featureFlag, false);
            } catch (ParserConfigurationException e) {
                log.error("Feature {} is not supported by your XML processor.", featureFlag, e);
            }
        }

        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException e) {
            log.error("Feature FEATURE_SECURE_PROCESSING is not supported by your XML processor.", e);
        }

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
