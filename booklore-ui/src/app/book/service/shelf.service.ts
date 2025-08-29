import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {tap, catchError, map, shareReplay, finalize} from 'rxjs/operators';

import {Shelf} from '../model/shelf.model';
import {ShelfState} from '../model/state/shelf-state.model';
import {BookService} from './book.service';
import {API_CONFIG} from '../../config/api-config';

@Injectable({providedIn: 'root'})
export class ShelfService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/shelves`;
  private http = inject(HttpClient);
  private bookService = inject(BookService);

  private shelfStateSubject = new BehaviorSubject<ShelfState>({
    shelves: null,
    loaded: false,
    error: null,
  });

  private loading$: Observable<Shelf[]> | null = null;

  shelfState$ = this.shelfStateSubject.asObservable().pipe(
    tap(state => {
      if (!state.loaded && !state.error && !this.loading$) {
        this.loading$ = this.fetchShelves().pipe(
          shareReplay(1),
          finalize(() => (this.loading$ = null))
        );
        this.loading$.subscribe();
      }
    })
  );

  private fetchShelves(): Observable<Shelf[]> {
    return this.http.get<Shelf[]>(this.url).pipe(
      tap(shelves => this.shelfStateSubject.next({shelves, loaded: true, error: null})),
      catchError(err => {
        const curr = this.shelfStateSubject.value;
        this.shelfStateSubject.next({shelves: curr.shelves, loaded: true, error: err.message});
        throw err;
      })
    );
  }

  public reloadShelves(): void {
    this.fetchShelves().subscribe({
      next: () => {
      },
      error: () => {
      }
    });
  }

  createShelf(shelf: Shelf): Observable<Shelf> {
    return this.http.post<Shelf>(this.url, shelf).pipe(
      map(newShelf => {
        const curr = this.shelfStateSubject.value;
        const updated = curr.shelves ? [...curr.shelves, newShelf] : [newShelf];
        this.shelfStateSubject.next({...curr, shelves: updated});
        return newShelf;
      })
    );
  }

  updateShelf(shelf: Shelf, id?: number): Observable<Shelf> {
    return this.http.put<Shelf>(`${this.url}/${id}`, shelf).pipe(
      map(updated => {
        const curr = this.shelfStateSubject.value;
        const list = curr.shelves?.map(s => (s.id === updated.id ? updated : s)) || [updated];
        this.shelfStateSubject.next({...curr, shelves: list});
        return updated;
      })
    );
  }

  deleteShelf(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`).pipe(
      tap(() => {
        this.bookService.removeBooksFromShelf(id);
        const curr = this.shelfStateSubject.value;
        const filtered = curr.shelves?.filter(s => s.id !== id) || [];
        this.shelfStateSubject.next({...curr, shelves: filtered});
      }),
      catchError(err => {
        const curr = this.shelfStateSubject.value;
        this.shelfStateSubject.next({...curr, error: err.message});
        return of();
      })
    );
  }

  getShelfById(id: number): Shelf | undefined {
    return this.shelfStateSubject.value.shelves?.find(s => s.id === id);
  }

  getBookCount(shelfId: number): Observable<number> {
    return this.bookService.bookState$.pipe(
      map(state =>
        (state.books || []).filter(b => b.shelves?.some(s => s.id === shelfId)).length
      )
    );
  }

  getUnshelvedBookCount(): Observable<number> {
    return this.bookService.bookState$.pipe(
      map(state =>
        (state.books || []).filter(b => !b.shelves || b.shelves.length === 0).length
      )
    );
  }
}
