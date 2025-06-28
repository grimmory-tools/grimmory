import {BookFilter} from './BookFilter';
import {BookState} from '../../../model/state/book-state.model';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';

export class HeaderFilter implements BookFilter {

  constructor(private searchTerm$: Observable<any>) {
  }

  filter(bookState: BookState): Observable<BookState> {
    return this.searchTerm$.pipe(
      map(term => {
        if (term && term.trim() !== '') {
          const filteredBooks = bookState.books?.filter(book => {
            const matchesTitle = book.metadata?.title?.toLowerCase().includes(term.toLowerCase());
            const matchesSeries = book.metadata?.seriesName?.toLowerCase().includes(term.toLowerCase());
            const matchesAuthor = book.metadata?.authors?.some(author =>
              author.toLowerCase().includes(term.toLowerCase())
            );
            return matchesTitle || matchesSeries || matchesAuthor;
          }) || null;
          return {...bookState, books: filteredBooks};
        }
        return bookState;
      })
    );
  }

}
