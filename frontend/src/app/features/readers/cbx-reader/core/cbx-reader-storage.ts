const LEGACY_STRIP_WIDTH_BOOK_PREFIX = 'grimmory.cbx.stripWidth.book.';
const WEBTOON_NEVER = 'grimmory.cbx.webtoonHint.never';
const WEBTOON_SESSION_BOOK_PREFIX = 'grimmory.cbx.webtoonHint.sessionBook.';

function stripWidthStorageKey(bookId: number, userId: number | null | undefined): string {
  if (userId != null && userId > 0) {
    return `grimmory.cbx.stripWidth.u${userId}.book.${bookId}`;
  }
  return LEGACY_STRIP_WIDTH_BOOK_PREFIX + bookId;
}

/** Clamp comic strip width preference to 50–100 (percent). */
export function clampStripMaxWidthPercent(n: number | null | undefined): number {
  if (n == null || Number.isNaN(n)) return 100;
  return Math.min(100, Math.max(50, Math.round(n)));
}

export function readStripWidthPercent(
  bookId: number,
  usesGlobalCbxSettings: boolean,
  userStripMaxWidthPercent: number | undefined | null,
  userId?: number | null
): number {
  if (usesGlobalCbxSettings) {
    if (userStripMaxWidthPercent != null && !Number.isNaN(userStripMaxWidthPercent)) {
      return clampStripMaxWidthPercent(userStripMaxWidthPercent);
    }
    return 100;
  }
  try {
    let raw: string | null = null;
    if (userId != null && userId > 0) {
      raw = localStorage.getItem(stripWidthStorageKey(bookId, userId));
      if (raw == null) {
        raw = localStorage.getItem(LEGACY_STRIP_WIDTH_BOOK_PREFIX + bookId);
      }
    } else {
      raw = localStorage.getItem(LEGACY_STRIP_WIDTH_BOOK_PREFIX + bookId);
    }
    if (raw == null) return 100;
    return clampStripMaxWidthPercent(parseInt(raw, 10));
  } catch {
    return 100;
  }
}

export function writeStripWidthPercentPerBook(
  bookId: number,
  value: number,
  userId?: number | null
): void {
  try {
    localStorage.setItem(stripWidthStorageKey(bookId, userId), String(clampStripMaxWidthPercent(value)));
  } catch {
    /* quota / private mode */
  }
}

export function shouldOfferWebtoonHint(bookId: number): boolean {
  try {
    if (localStorage.getItem(WEBTOON_NEVER) === '1') return false;
    if (sessionStorage.getItem(WEBTOON_SESSION_BOOK_PREFIX + bookId) === '1') return false;
    return true;
  } catch {
    return true;
  }
}

/** Hide webtoon suggestion until the browser tab/session ends (middle “not now” action). */
export function dismissWebtoonHintForSession(bookId: number): void {
  try {
    sessionStorage.setItem(WEBTOON_SESSION_BOOK_PREFIX + bookId, '1');
  } catch {
    /* sessionStorage unavailable */
  }
}

export function dismissWebtoonHintForever(): void {
  try {
    localStorage.setItem(WEBTOON_NEVER, '1');
  } catch {
    /* ignore */
  }
}
