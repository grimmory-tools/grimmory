/*
 * Immutable data model for parsed EPUB CFI expressions.
 *
 * Based on the EPUB CFI 1.1 specification (IDPF / W3C public standard).
 * Architecture modelled after epub.js (BSD-2-Clause, futurepress).
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.booklore.util.koreader;

import java.util.List;

/**
 * Immutable parsed representation of an EPUB Canonical Fragment Identifier.
 *
 * <p>Grammar (from the EPUB CFI 1.1 spec, IDPF):
 *
 * <pre>
 *   fragment = "epubcfi(" path ["," local_path "," local_path] ")"
 *   path     = step local_path
 *   step     = "/" integer ["[" assertion "]"]
 * </pre>
 *
 * <p>Architecture modelled after epub.js (BSD-2-Clause) which represents a CFI as a parsed object
 * with separate base, content, and optional range components.
 *
 * @param spinePosition the even integer from the spine step (e.g. 4 in {@code /6/4!...})
 * @param contentSteps ordered list of path steps after the indirection ({@code !})
 * @param charOffset character offset at the terminal step, or {@code null}
 * @param rangeEndSteps if this is a range CFI, the steps of the end path; otherwise {@code null}
 * @param rangeEndOffset character offset of the range end, or {@code null}
 */
public record CfiExpression(
    int spinePosition,
    List<PathStep> contentSteps,
    Integer charOffset,
    List<PathStep> rangeEndSteps,
    Integer rangeEndOffset) {

  /** Canonical constructor that makes defensive copies of the step lists. */
  public CfiExpression {
    contentSteps = contentSteps != null ? List.copyOf(contentSteps) : List.of();
    rangeEndSteps = rangeEndSteps != null ? List.copyOf(rangeEndSteps) : null;
  }

  /** A single step in a CFI path: {@code /position[id]}. */
  public record PathStep(int position, String id) {

    /** Whether this step targets an element node (even position per the spec). */
    public boolean targetsElement() {
      return position % 2 == 0;
    }

    /** Zero-based child-element index derived from the spec's even-numbered position. */
    public int childElementIndex() {
      return position / 2 - 1;
    }
  }

  /** Zero-based spine index derived from the spine position integer. */
  public int spineIndex() {
    return (spinePosition - 2) / 2;
  }

  /** Whether this expression describes a range ({@code P,S,E} form). */
  public boolean isRange() {
    return rangeEndSteps != null;
  }

  /** Spine position integer for a given zero-based spine index. */
  public static int spinePositionOf(int zeroBasedIndex) {
    return (zeroBasedIndex + 1) * 2;
  }
}
