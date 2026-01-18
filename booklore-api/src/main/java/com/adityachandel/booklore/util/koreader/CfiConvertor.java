package com.adityachandel.booklore.util.koreader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CfiConvertor {

    private static final Pattern CFI_PATTERN = Pattern.compile("^epubcfi\\((.+)\\)$");
    private static final Pattern CFI_SPINE_PATTERN = Pattern.compile("^/6/(\\d+)!(.*)$");
    private static final Pattern CFI_PATH_STEP_PATTERN = Pattern.compile("/(\\d+)(?:\\[(.*?)\\])?(?::(\\d+))?");
    private static final Pattern XPOINTER_DOC_FRAGMENT_PATTERN = Pattern.compile("^/body/DocFragment\\[(\\d+)\\]/body(.*)$");
    private static final Pattern XPOINTER_TEXT_OFFSET_PATTERN = Pattern.compile("/text\\(\\)\\.(\\d+)$");
    private static final Pattern XPOINTER_SEGMENT_WITH_INDEX_PATTERN = Pattern.compile("^(\\w+)\\[(\\d+)\\]$");
    private static final Pattern XPOINTER_SEGMENT_WITHOUT_INDEX_PATTERN = Pattern.compile("^(\\w+)$");
    private static final Pattern TRAILING_TEXT_OFFSET_PATTERN = Pattern.compile("/text\\(\\).*$");
    private static final Pattern SUFFIX_NODE_OFFSET_PATTERN = Pattern.compile("\\.\\d+$");

    private static final Set<String> INLINE_ELEMENTS = Set.of(
            "span", "em", "strong", "i", "b", "u", "small", "mark", "sup", "sub"
    );

    private final Document document;
    private final int spineItemIndex;

    public CfiConvertor(Document htmlDocument, int spineIndex) {
        this.document = htmlDocument;
        this.spineItemIndex = spineIndex;
    }

    public CfiConvertor(Document htmlDocument) {
        this(htmlDocument, 0);
    }

    public static int extractSpineIndex(String cfiOrXPath) {
        if (cfiOrXPath == null || cfiOrXPath.isBlank()) {
            throw new IllegalArgumentException("CFI/XPointer string cannot be null or empty");
        }

        if (cfiOrXPath.startsWith("epubcfi(")) {
            return extractSpineIndexFromCfi(cfiOrXPath);
        } else if (cfiOrXPath.startsWith("/body/DocFragment[")) {
            return extractSpineIndexFromXPointer(cfiOrXPath);
        } else {
            throw new IllegalArgumentException("Unsupported format for spine index extraction: " + cfiOrXPath);
        }
    }

    private static int extractSpineIndexFromCfi(String cfi) {
        Matcher cfiMatcher = CFI_PATTERN.matcher(cfi);
        if (!cfiMatcher.matches()) {
            throw new IllegalArgumentException("Invalid CFI format: " + cfi);
        }

        String innerCfi = cfiMatcher.group(1);
        Matcher spineMatcher = CFI_SPINE_PATTERN.matcher(innerCfi);
        if (!spineMatcher.matches()) {
            throw new IllegalArgumentException("Cannot extract spine index from CFI: " + cfi);
        }

        int spineStep = Integer.parseInt(spineMatcher.group(1));
        return (spineStep - 2) / 2;
    }

    private static int extractSpineIndexFromXPointer(String xpointer) {
        Pattern pattern = Pattern.compile("DocFragment\\[(\\d+)\\]");
        Matcher matcher = pattern.matcher(xpointer);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1)) - 1;
        }
        throw new IllegalArgumentException("Cannot extract spine index from XPointer: " + xpointer);
    }

    public String xPointerToCfi(String startXPointer, String endXPointer) {
        if (endXPointer != null && !endXPointer.isBlank()) {
            return convertRangeXPointerToCfi(startXPointer, endXPointer);
        }
        return convertPointXPointerToCfi(startXPointer);
    }

    public String xPointerToCfi(String xpointer) {
        return xPointerToCfi(xpointer, null);
    }

    public XPointerResult cfiToXPointer(String cfi) {
        Matcher cfiMatcher = CFI_PATTERN.matcher(cfi);
        if (!cfiMatcher.matches()) {
            throw new IllegalArgumentException("Invalid CFI format: " + cfi);
        }

        String innerCfi = cfiMatcher.group(1);
        Matcher spineMatcher = CFI_SPINE_PATTERN.matcher(innerCfi);
        if (!spineMatcher.matches()) {
            throw new IllegalArgumentException("Cannot parse CFI spine step: " + cfi);
        }

        int spineStep = Integer.parseInt(spineMatcher.group(1));
        int cfiSpineIndex = (spineStep - 2) / 2;

        if (cfiSpineIndex != spineItemIndex) {
            throw new IllegalArgumentException(
                    String.format("CFI spine index %d does not match converter spine index %d",
                            cfiSpineIndex, spineItemIndex));
        }

        String contentPath = spineMatcher.group(2);
        CfiPathResult pathResult = parseCfiPath(contentPath);

        Element element = resolveElementFromCfiSteps(pathResult.steps());
        if (element == null) {
            throw new IllegalArgumentException("Element not found for CFI: " + cfi);
        }

        String xpointer;
        if (pathResult.textOffset() != null) {
            xpointer = handleTextOffset(element, pathResult.textOffset());
        } else {
            xpointer = buildXPointerPath(element);
        }

        return new XPointerResult(xpointer, xpointer, xpointer);
    }

    public boolean validateCfi(String cfi) {
        try {
            cfiToXPointer(cfi);
            return true;
        } catch (Exception e) {
            log.debug("CFI validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateXPointer(String xpointer, String pos1) {
        try {
            xPointerToCfi(xpointer, pos1);
            return true;
        } catch (Exception e) {
            log.debug("XPointer validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateXPointer(String xpointer) {
        return validateXPointer(xpointer, null);
    }

    public static String normalizeProgressXPointer(String xpointer) {
        if (xpointer == null) {
            return null;
        }
        String result = TRAILING_TEXT_OFFSET_PATTERN.matcher(xpointer).replaceAll("");
        result = SUFFIX_NODE_OFFSET_PATTERN.matcher(result).replaceAll("");
        return result;
    }

    private String convertPointXPointerToCfi(String xpointer) {
        XPointerParseResult parseResult = parseXPointer(xpointer);
        String cfiPath = buildCfiPathFromElement(parseResult.element());

        if (parseResult.textOffset() != null) {
            // CFI requires /1 to indicate first text node child before the character offset
            cfiPath += "/1:" + parseResult.textOffset();
        }

        return buildFullCfi(cfiPath);
    }

    private String convertRangeXPointerToCfi(String startXPointer, String endXPointer) {
        XPointerParseResult startResult = parseXPointer(startXPointer);
        XPointerParseResult endResult = parseXPointer(endXPointer);

        String startPath = buildCfiPathFromElement(startResult.element());
        String endPath = buildCfiPathFromElement(endResult.element());

        if (startResult.textOffset() != null) {
            // CFI requires /1 to indicate first text node child before the character offset
            startPath += "/1:" + startResult.textOffset();
        }
        if (endResult.textOffset() != null) {
            // CFI requires /1 to indicate first text node child before the character offset
            endPath += "/1:" + endResult.textOffset();
        }

        if (!startPath.equals(endPath)) {
            return buildFullCfi(startPath + "," + endPath);
        }
        return buildFullCfi(startPath);
    }

    private XPointerParseResult parseXPointer(String xpointer) {
        Matcher textOffsetMatcher = XPOINTER_TEXT_OFFSET_PATTERN.matcher(xpointer);
        Integer textOffset = null;
        String elementPath = xpointer;

        if (textOffsetMatcher.find()) {
            textOffset = Integer.parseInt(textOffsetMatcher.group(1));
            elementPath = XPOINTER_TEXT_OFFSET_PATTERN.matcher(xpointer).replaceAll("");
        }

        Element element = resolveXPointerPath(elementPath);
        if (element == null) {
            throw new IllegalArgumentException("Cannot resolve XPointer path: " + elementPath);
        }

        return new XPointerParseResult(element, textOffset);
    }

    private Element resolveXPointerPath(String path) {
        Matcher pathMatcher = XPOINTER_DOC_FRAGMENT_PATTERN.matcher(path);
        if (!pathMatcher.matches()) {
            throw new IllegalArgumentException("Invalid XPointer format: " + path);
        }

        String elementPath = pathMatcher.group(2);
        Element body = document.body();

        if (body == null) {
            throw new IllegalArgumentException("Document has no body element");
        }

        if (elementPath == null || elementPath.isEmpty()) {
            return body;
        }

        // Extract the final indexed segment (e.g., p[54] from /div/div/p[54])
        // KOReader counts elements globally in the document, not hierarchically
        String[] segments = elementPath.split("/");
        String lastSegment = null;
        for (int i = segments.length - 1; i >= 0; i--) {
            if (!segments[i].isEmpty()) {
                lastSegment = segments[i];
                break;
            }
        }

        if (lastSegment == null) {
            return body;
        }

        Matcher withIndexMatcher = XPOINTER_SEGMENT_WITH_INDEX_PATTERN.matcher(lastSegment);
        if (withIndexMatcher.matches()) {
            String tagName = withIndexMatcher.group(1);
            int index = Integer.parseInt(withIndexMatcher.group(2)) - 1;

            // Find the element globally in the document body
            Elements allElements = body.getElementsByTag(tagName);
            if (index < allElements.size()) {
                log.debug("XPointer resolved: found {}[{}] among {} total in document",
                        tagName, index + 1, allElements.size());
                return allElements.get(index);
            } else {
                throw new IllegalArgumentException(
                        String.format("Element index %d out of bounds for tag %s (found %d in document)",
                                index, tagName, allElements.size()));
            }
        }

        // For non-indexed segments, fall back to hierarchical traversal
        Element current = body;
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }

            Matcher segWithIndex = XPOINTER_SEGMENT_WITH_INDEX_PATTERN.matcher(segment);
            Matcher segWithoutIndex = XPOINTER_SEGMENT_WITHOUT_INDEX_PATTERN.matcher(segment);

            String tagName;
            int index;

            if (segWithIndex.matches()) {
                tagName = segWithIndex.group(1);
                index = Integer.parseInt(segWithIndex.group(2)) - 1;
            } else if (segWithoutIndex.matches()) {
                tagName = segWithoutIndex.group(1);
                index = 0;
            } else {
                throw new IllegalArgumentException("Invalid XPointer segment: " + segment);
            }

            Elements children = current.children();
            List<Element> matchingChildren = new ArrayList<>();
            for (Element child : children) {
                if (child.tagName().equalsIgnoreCase(tagName)) {
                    matchingChildren.add(child);
                }
            }

            if (index < matchingChildren.size()) {
                current = matchingChildren.get(index);
            } else {
                throw new IllegalArgumentException(
                        String.format("Element index %d out of bounds for tag %s (found %d)",
                                index, tagName, matchingChildren.size()));
            }
        }

        return current;
    }

    private CfiPathResult parseCfiPath(String contentPath) {
        List<CfiStep> steps = new ArrayList<>();
        Integer textOffset = null;

        Matcher stepMatcher = CFI_PATH_STEP_PATTERN.matcher(contentPath);
        while (stepMatcher.find()) {
            int stepIndex = Integer.parseInt(stepMatcher.group(1));
            String assertion = stepMatcher.group(2);
            String offsetStr = stepMatcher.group(3);

            if (offsetStr != null) {
                textOffset = Integer.parseInt(offsetStr);
            }

            steps.add(new CfiStep(stepIndex, assertion));
        }

        return new CfiPathResult(steps, textOffset);
    }

    private Element resolveElementFromCfiSteps(List<CfiStep> steps) {
        Element current = document.body();
        if (current == null) {
            return null;
        }

        for (CfiStep step : steps) {
            int childIndex = (step.index() / 2) - 1;

            Elements children = current.children();
            if (childIndex < 0 || childIndex >= children.size()) {
                log.debug("CFI step {} out of bounds (children: {})", step.index(), children.size());
                return current;
            }

            current = children.get(childIndex);
        }

        return current;
    }

    private String buildCfiPathFromElement(Element element) {
        List<String> pathParts = new ArrayList<>();
        Element current = element;

        while (current != null && !current.tagName().equalsIgnoreCase("body")) {
            Element parent = current.parent();
            if (parent == null) {
                break;
            }

            int siblingIndex = 0;
            for (Element sibling : parent.children()) {
                siblingIndex++;
                if (sibling == current) {
                    break;
                }
            }

            int cfiStep = siblingIndex * 2;
            pathParts.add(0, "/" + cfiStep);

            current = parent;
        }

        pathParts.add(0, "/4");

        return String.join("", pathParts);
    }

    private String buildFullCfi(String contentPath) {
        int spineStep = (spineItemIndex + 1) * 2;
        return String.format("epubcfi(/6/%d!%s)", spineStep, contentPath);
    }

    private String buildXPointerPath(Element targetElement) {
        List<String> pathParts = new ArrayList<>();
        Element current = targetElement;

        Element root = document.body() != null ? document.body().parent() : null;
        while (current != null && current != root) {
            Element parent = current.parent();
            if (parent == null) {
                break;
            }

            String tagName = current.tagName().toLowerCase();

            int siblingIndex = 0;
            int totalSameTagSiblings = 0;
            for (Element sibling : parent.children()) {
                if (sibling.tagName().equalsIgnoreCase(tagName)) {
                    if (sibling == current) {
                        siblingIndex = totalSameTagSiblings;
                    }
                    totalSameTagSiblings++;
                }
            }

            if (totalSameTagSiblings == 1) {
                pathParts.add(0, tagName);
            } else {
                pathParts.add(0, String.format("%s[%d]", tagName, siblingIndex + 1));
            }

            current = parent;
        }

        StringBuilder xpointer = new StringBuilder("/body/DocFragment[")
                .append(spineItemIndex + 1)
                .append("]");

        if (!pathParts.isEmpty() && pathParts.get(0).startsWith("body")) {
            pathParts.remove(0);
        }

        xpointer.append("/body");

        if (!pathParts.isEmpty()) {
            xpointer.append("/").append(String.join("/", pathParts));
        }

        return xpointer.toString();
    }

    private String handleTextOffset(Element element, int cfiOffset) {
        List<TextNode> textNodes = collectTextNodes(element);

        int totalChars = 0;
        TextNode targetTextNode = null;
        int offsetInNode = 0;

        for (TextNode textNode : textNodes) {
            String nodeText = textNode.text();
            int nodeLength = nodeText.length();

            if (totalChars + nodeLength >= cfiOffset) {
                targetTextNode = textNode;
                offsetInNode = cfiOffset - totalChars;
                break;
            }

            totalChars += nodeLength;
        }

        if (targetTextNode == null) {
            return buildXPointerPath(element);
        }

        Element textParent = (Element) targetTextNode.parent();
        while (textParent != null && !isSignificantElement(textParent)) {
            textParent = textParent.parent();
        }

        if (textParent == null) {
            textParent = element;
        }

        String basePath = buildXPointerPath(textParent);
        return basePath + "/text()." + offsetInNode;
    }

    private List<TextNode> collectTextNodes(Element element) {
        List<TextNode> textNodes = new ArrayList<>();
        collectTextNodesRecursive(element, textNodes);
        return textNodes;
    }

    private void collectTextNodesRecursive(Node node, List<TextNode> textNodes) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode textNode) {
                String text = textNode.text();
                if (!text.isEmpty()) {
                    textNodes.add(textNode);
                }
            } else if (child instanceof Element) {
                collectTextNodesRecursive(child, textNodes);
            }
        }
    }

    private boolean isSignificantElement(Element element) {
        String tagName = element.tagName().toLowerCase();
        return !INLINE_ELEMENTS.contains(tagName);
    }

    @Getter
    public static class XPointerResult {
        private final String xpointer;
        private final String pos0;
        private final String pos1;

        public XPointerResult(String xpointer, String pos0, String pos1) {
            this.xpointer = xpointer;
            this.pos0 = pos0;
            this.pos1 = pos1;
        }

        public XPointerResult(String xpointer) {
            this(xpointer, null, null);
        }
    }

    private record XPointerParseResult(Element element, Integer textOffset) {}

    private record CfiStep(int index, String assertion) {}

    private record CfiPathResult(List<CfiStep> steps, Integer textOffset) {}
}
