package org.booklore.util.koreader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stateless parser and formatter for EPUB CFI strings. All methods are pure functions with no DOM
 * dependency.
 *
 * <p>Parsing pipeline:
 *
 * <ol>
 *   <li>Strip the {@code epubcfi(...)} wrapper
 *   <li>Split at {@code !} to isolate the spine (base) component from the content component
 *   <li>Split the content component at {@code ,} to detect range form
 *   <li>Tokenise each component by {@code /} into individual steps
 *   <li>Extract optional {@code :offset} and {@code [id]} from each token
 * </ol>
 */
public final class CfiParser {

  private static final String CFI_PREFIX = "epubcfi(";

  private CfiParser() {}

  /**
   * Parses a raw CFI string into a {@link CfiExpression}.
   *
   * @throws IllegalArgumentException if the string is not a valid CFI
   */
  public static CfiExpression parse(String cfi) {
    if (cfi == null || cfi.isBlank()) {
      throw new IllegalArgumentException("CFI string must not be null or blank");
    }

    String inner = unwrap(cfi);

    // Split at the indirection step "!" — base!content
    int bangIdx = inner.indexOf('!');
    if (bangIdx < 0) {
      throw new IllegalArgumentException("CFI is missing indirection step '!': " + cfi);
    }

    String basePart = inner.substring(0, bangIdx);
    String afterBang = inner.substring(bangIdx + 1);

    // Extract spine position from the base component (e.g. "/6/4[chap01ref]")
    int spinePos = extractSpinePosition(basePart);

    // Detect range form: contentPath , startPath , endPath
    String[] rangeParts = splitRange(afterBang);

    if (rangeParts != null) {
      // Range form: shared , start , end
      SegmentParseResult shared = parseSegment(rangeParts[0]);
      SegmentParseResult endSeg = parseSegment(rangeParts[2]);

      // Combine shared steps + start-specific steps
      SegmentParseResult startSeg = parseSegment(rangeParts[1]);
      List<CfiExpression.PathStep> fullStart = concat(shared.steps(), startSeg.steps());
      List<CfiExpression.PathStep> fullEnd = concat(shared.steps(), endSeg.steps());

      return new CfiExpression(spinePos, fullStart, startSeg.offset(), fullEnd, endSeg.offset());
    }

    // Point form
    SegmentParseResult contentSeg = parseSegment(afterBang);
    return new CfiExpression(spinePos, contentSeg.steps(), contentSeg.offset(), null, null);
  }

  /** Formats a {@link CfiExpression} back into a canonical CFI string. */
  public static String format(CfiExpression expr) {
    var sb = new StringBuilder("epubcfi(/6/");
    sb.append(expr.spinePosition()).append('!');

    appendSteps(sb, expr.contentSteps());
    if (expr.charOffset() != null) {
      sb.append(':').append(expr.charOffset());
    }

    if (expr.isRange()) {
      sb.append(',');
      appendSteps(sb, expr.rangeEndSteps());
      if (expr.rangeEndOffset() != null) {
        sb.append(':').append(expr.rangeEndOffset());
      }
    }

    sb.append(')');
    return sb.toString();
  }

  /** Formats just a content path (steps + optional offset) without the spine wrapper. */
  static String formatContentPath(List<CfiExpression.PathStep> steps, Integer offset) {
    var sb = new StringBuilder();
    appendSteps(sb, steps);
    if (offset != null) {
      sb.append(':').append(offset);
    }
    return sb.toString();
  }

  /** Remove the {@code epubcfi(…)} envelope. */
  private static String unwrap(String cfi) {
    String trimmed = cfi.strip();
    if (!trimmed.startsWith(CFI_PREFIX) || !trimmed.endsWith(")")) {
      throw new IllegalArgumentException("Not an epubcfi() expression: " + cfi);
    }
    return trimmed.substring(CFI_PREFIX.length(), trimmed.length() - 1);
  }

  /**
   * Extract the spine position integer from the base component. The base is expected to start with
   * {@code /6/N} where N is the spine position.
   */
  private static int extractSpinePosition(String base) {
    // Tokenise: "/6/4[chap01ref]" → ["", "6", "4[chap01ref]"]
    String[] tokens = base.split("/");
    if (tokens.length < 3) {
      throw new IllegalArgumentException("Cannot extract spine position from: " + base);
    }
    // Second token should be "6" (package element); third is the spine position
    String spineToken = tokens[2];
    // Strip any ID assertion
    int bracket = spineToken.indexOf('[');
    String numPart = bracket >= 0 ? spineToken.substring(0, bracket) : spineToken;
    try {
      return Integer.parseInt(numPart);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Non-numeric spine position: " + spineToken);
    }
  }

  /**
   * Split a content string into range parts if it contains the {@code P,S,E} form. Returns {@code
   * null} for non-range (point) CFIs.
   */
  private static String[] splitRange(String content) {
    // A range has exactly two commas at the top level
    int first = content.indexOf(',');
    if (first < 0) return null;
    int second = content.indexOf(',', first + 1);
    if (second < 0) return null;

    return new String[] {
      content.substring(0, first),
      content.substring(first + 1, second),
      content.substring(second + 1)
    };
  }

  /** Parse a segment (series of /N steps with an optional trailing :offset). */
  private static SegmentParseResult parseSegment(String segment) {
    // Check for a character offset ":N" at the very end
    Integer offset = null;
    String pathPart = segment;

    int lastColon = segment.lastIndexOf(':');
    if (lastColon >= 0) {
      String afterColon = segment.substring(lastColon + 1);
      // Strip any trailing assertion bracket from the offset
      int bracket = afterColon.indexOf('[');
      String numStr = bracket >= 0 ? afterColon.substring(0, bracket) : afterColon;
      try {
        offset = Integer.parseInt(numStr);
        pathPart = segment.substring(0, lastColon);
      } catch (NumberFormatException ignored) {
        // Not a valid offset; treat the whole thing as a path
      }
    }

    List<CfiExpression.PathStep> steps = tokeniseSteps(pathPart);
    return new SegmentParseResult(steps, offset);
  }

  /**
   * Tokenise a path string into individual {@link CfiExpression.PathStep}s. Input looks like {@code
   * /4/2/6[id]} — split on {@code /}, parse each token.
   */
  private static List<CfiExpression.PathStep> tokeniseSteps(String path) {
    if (path == null || path.isBlank()) {
      return List.of();
    }
    String[] tokens = path.split("/");
    List<CfiExpression.PathStep> steps = new ArrayList<>(tokens.length);
    for (String token : tokens) {
      if (token.isEmpty()) continue;

      String id = null;
      String numStr = token;

      int openBracket = token.indexOf('[');
      if (openBracket >= 0) {
        int closeBracket = token.indexOf(']', openBracket);
        if (closeBracket > openBracket) {
          id = token.substring(openBracket + 1, closeBracket);
        }
        numStr = token.substring(0, openBracket);
      }

      try {
        int pos = Integer.parseInt(numStr);
        steps.add(new CfiExpression.PathStep(pos, id));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid step token: " + token);
      }
    }
    return Collections.unmodifiableList(steps);
  }

  /** Append steps as {@code /N} or {@code /N[id]} to a StringBuilder. */
  private static void appendSteps(StringBuilder sb, List<CfiExpression.PathStep> steps) {
    for (var step : steps) {
      sb.append('/').append(step.position());
      if (step.id() != null) {
        sb.append('[').append(step.id()).append(']');
      }
    }
  }

  private static List<CfiExpression.PathStep> concat(
          List<CfiExpression.PathStep> a, List<CfiExpression.PathStep> b) {
    var result = new ArrayList<CfiExpression.PathStep>(a.size() + b.size());
    result.addAll(a);
    result.addAll(b);
    return Collections.unmodifiableList(result);
  }

  private record SegmentParseResult(List<CfiExpression.PathStep> steps, Integer offset) {}
}
