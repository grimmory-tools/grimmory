import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from '../../../core/config/api-config';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable, of} from 'rxjs';
import {catchError, distinctUntilChanged, finalize, map, shareReplay, switchMap, tap} from 'rxjs/operators';
import {BookService} from '../../book/service/book.service';
import {BookRuleEvaluatorService} from './book-rule-evaluator.service';
import {AuthService} from '../../../shared/service/auth.service';
import {GroupRule} from '../component/magic-shelf-component';

export interface MagicShelf {
  id?: number | null;
  name: string;
  icon?: string | null;
  iconType?: 'PRIME_NG' | 'CUSTOM_SVG' | null;
  filterJson: string;
  isPublic?: boolean;
}

export interface MagicShelfState {
  shelves: MagicShelf[] | null;
  loaded: boolean;
  error: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class MagicShelfService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/magic-shelves`;

  private readonly http = inject(HttpClient);
  private readonly bookService = inject(BookService);
  private readonly ruleEvaluatorService = inject(BookRuleEvaluatorService);
  private readonly authService = inject(AuthService);

  private readonly shelvesStateSubject = new BehaviorSubject<MagicShelfState>({
    shelves: null,
    loaded: false,
    error: null,
  });

  private loading$: Observable<MagicShelf[]> | null = null;

  constructor() {
    this.authService.token$.pipe(
      distinctUntilChanged()
    ).subscribe(token => {
      if (token === null) {
        this.shelvesStateSubject.next({
          shelves: null,
          loaded: true,
          error: null,
        });
        this.loading$ = null;
      } else {
        const current = this.shelvesStateSubject.value;
        if (current.loaded && !current.shelves) {
          this.shelvesStateSubject.next({
            shelves: null,
            loaded: false,
            error: null,
          });
          this.loading$ = null;
        }
      }
    });
  }

  public readonly shelvesState$ = this.shelvesStateSubject.asObservable().pipe(
    tap(state => {
      if (!state.loaded && !state.error && !this.loading$) {
        this.loading$ = this.fetchMagicShelves().pipe(
          shareReplay(1),
          finalize(() => (this.loading$ = null))
        );
        this.loading$.subscribe();
      }
    })
  );

  private get state(): MagicShelfState {
    return this.shelvesStateSubject.value;
  }

  private fetchMagicShelves(): Observable<MagicShelf[]> {
    return this.http.get<MagicShelf[]>(this.url).pipe(
      tap(shelves => this.shelvesStateSubject.next({shelves, loaded: true, error: null})),
      catchError(err => {
        const curr = this.shelvesStateSubject.value;
        this.shelvesStateSubject.next({shelves: curr.shelves, loaded: true, error: err.message});
        throw err;
      })
    );
  }

  saveShelf(data: { id?: number; name: string | null; icon: string | null; iconType?: 'PRIME_NG' | 'CUSTOM_SVG'; group: unknown, isPublic?: boolean | null }): Observable<MagicShelf> {
    const payload: MagicShelf = {
      id: data.id,
      name: data.name ?? '',
      icon: data.icon,
      iconType: data.iconType,
      filterJson: JSON.stringify(data.group),
      isPublic: data.isPublic ?? false
    };

    return this.http.post<MagicShelf>(this.url, payload).pipe(
      tap((newShelf) => {
        const curr = this.state;
        const shelves = curr.shelves || [];
        const updated = shelves.some((s) => s.id === newShelf.id)
          ? shelves.map((s) => (s.id === newShelf.id ? newShelf : s))
          : [...shelves, newShelf];

        this.shelvesStateSubject.next({...curr, shelves: updated});
      }),
      catchError((error) => {
        this.updateError(error.message);
        throw error;
      })
    );
  }

  getShelf(id: number): Observable<MagicShelf | undefined> {
    return this.shelvesState$.pipe(
      map(state => (state.shelves || []).find(shelf => shelf.id === id))
    );
  }

  deleteShelf(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`).pipe(
      tap(() => {
        const curr = this.state;
        const updated = (curr.shelves || []).filter((s) => s.id !== id);
        this.shelvesStateSubject.next({...curr, shelves: updated});
      }),
      catchError((error) => {
        this.updateError(error.message);
        throw error;
      })
    );
  }

  private updateError(error: string): void {
    this.shelvesStateSubject.next({
      ...this.state,
      error,
      loaded: true,
    });
  }

  getBookCount(shelfId: number): Observable<number> {
    return this.getShelf(shelfId).pipe(
      switchMap((shelf) => {
        if (!shelf) return of(0);
        let group: GroupRule;
        try {
          group = JSON.parse(shelf.filterJson);
        } catch (e) {
          console.error('Invalid filter JSON', e);
          return of(0);
        }

        return this.bookService.bookState$.pipe(
          map((state) => {
            const allBooks = state.books ?? [];
            return allBooks.filter((book) =>
              this.ruleEvaluatorService.evaluateGroup(book, group, allBooks)
            ).length;
          })
        );
      })
    );
  }
}
