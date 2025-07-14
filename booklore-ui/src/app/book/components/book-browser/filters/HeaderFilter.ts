import {BookFilter} from './BookFilter';
import {BookState} from '../../../model/state/book-state.model';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';

export class HeaderFilter implements BookFilter {

  constructor(private searchTerm$: Observable<any>) {
  }

  filter(bookState: BookState): Observable<BookState> {
    const normalize = (str: string): string =>
      str.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

    return this.searchTerm$.pipe(
      map(term => {
        const normalizedTerm = normalize(term || '');
        if (normalizedTerm && normalizedTerm.trim() !== '') {
          const filteredBooks = bookState.books?.filter(book => {
            const title = book.metadata?.title || '';
            const series = book.metadata?.seriesName || '';
            const authors = book.metadata?.authors || [];
            const matchesTitle = normalize(title).includes(normalizedTerm);
            const matchesSeries = normalize(series).includes(normalizedTerm);
            const matchesAuthor = authors.some(author => normalize(author).includes(normalizedTerm));
            return matchesTitle || matchesSeries || matchesAuthor;
          }) || null;
          return {...bookState, books: filteredBooks};
        }
        return bookState;
      })
    );
  }
}
