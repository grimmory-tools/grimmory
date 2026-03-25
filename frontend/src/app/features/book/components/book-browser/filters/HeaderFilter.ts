import {Book} from '../../../model/book.model';

export function normalizeSearchTerm(str: string): string {
  if (!str) return '';
  // Normalize Unicode combining characters (e.g., é -> e)
  let s = str.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  s = s.replace(/ø/gi, 'o')
       .replace(/ł/gi, 'l')
       .replace(/æ/gi, 'ae')
       .replace(/œ/gi, 'oe')
       .replace(/ß/g, 'ss');
  s = s.replace(/[!@$%^&*_=|~`<>?/";']/g, '');
  s = s.replace(/\s+/g, ' ').trim();
  return s.toLowerCase();
}

export function filterBooksBySearchTerm(books: Book[], term: string): Book[] {
  const normalizedTerm = normalizeSearchTerm(term || '').trim();
  if (normalizedTerm.length < 2) {
    return books;
  }

  return books.filter(book => {
    const title = book.metadata?.title || '';
    const series = book.metadata?.seriesName || '';
    const authors = book.metadata?.authors || [];
    const categories = book.metadata?.categories || [];
    const isbn = book.metadata?.isbn10 || '';
    const isbn13 = book.metadata?.isbn13 || '';
    const fileName = book.primaryFile?.fileName || '';

    const matchesTitle = normalizeSearchTerm(title).includes(normalizedTerm);
    const matchesSeries = normalizeSearchTerm(series).includes(normalizedTerm);
    const matchesAuthor = authors.some(author => normalizeSearchTerm(author).includes(normalizedTerm));
    const matchesCategory = categories.some(category => normalizeSearchTerm(category).includes(normalizedTerm));
    const matchesIsbn = normalizeSearchTerm(isbn).includes(normalizedTerm) || normalizeSearchTerm(isbn13).includes(normalizedTerm);
    const matchesFileName = normalizeSearchTerm(fileName).includes(normalizedTerm);

    return matchesTitle || matchesSeries || matchesAuthor || matchesCategory || matchesIsbn || matchesFileName;
  });
}

