import {EpubCFI} from 'epubjs';

export const FALLBACK_EPUB_SETTINGS = {
  maxFontSize: 300,
  minFontSize: 50
};

export function flatten(chapters: any) {
  return [].concat.apply([], chapters.map((chapter: any) => [].concat.apply([chapter], flatten(chapter.subitems))));
}

export function getCfiFromHref(book: any, href: string): string | null {
  const [_, id] = href.split('#');
  const section = book.spine.get(href);

  if (!section || !section.document) {
    console.warn('Section or section.document is undefined for href:', href);
    return null;
  }

  const el = id ? section.document.getElementById(id) : section.document.body;
  if (!el) {
    console.warn('Element not found in section.document for href:', href);
    return null;
  }

  return section.cfiFromElement(el);
}

export function getChapter(book: any, location: any) {
  const locationHref = location.start.href;
  return flatten(book.navigation.toc)
    .filter((chapter: any) => {
      return book.canonical(chapter.href).includes(book.canonical(locationHref));
    })
    .reduce((result: any | null, chapter: any) => {
      const chapterCfi = getCfiFromHref(book, chapter.href);
      if (!chapterCfi) {
        return result;
      }
      const locationAfterChapter = EpubCFI.prototype.compare(location.start.cfi, chapterCfi) > 0;
      return locationAfterChapter ? chapter : result;
    }, null);
}
