import {inject, Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, DetachedRouteHandle, RouteReuseStrategy} from '@angular/router';
import {BookBrowserScrollService} from '../features/book/components/book-browser/book-browser-scroll.service';
import {BookSelectionService} from '../features/book/components/book-browser/book-selection.service';

/**
 * Maximum number of detached BookBrowser routes kept in memory.
 * Each cached route retains its full component tree (book cards, images,
 * event listeners, virtual-scroller instance) so an unbounded cache leads
 * to severe memory growth when the user switches between libraries.
 *
 * 3 slots is enough for productive back-navigation while keeping memory
 * usage under control.
 */
const MAX_STORED_ROUTES = 3;

@Injectable({
  providedIn: 'root',
})
export class CustomReuseStrategy implements RouteReuseStrategy {
  private storedRoutes = new Map<string, DetachedRouteHandle>();
  /** Insertion-order queue so we can evict the oldest entry (LRU). */
  private insertionOrder: string[] = [];
  private scrollService = inject(BookBrowserScrollService);
  private bookSelectionService = inject(BookSelectionService);

  private readonly BOOK_BROWSER_PATHS = [
    'all-books',
    'unshelved-books',
    'library/:libraryId/books',
    'shelf/:shelfId/books',
    'magic-shelf/:magicShelfId/books',
    'authors',
    'series'
  ];

  private readonly BOOK_DETAILS_PATH = 'book/:bookId';

  private getRouteKey(route: ActivatedRouteSnapshot): string {
    const path = route.routeConfig?.path || '';
    return this.scrollService.createKey(path, route.params);
  }

  private isBookBrowserRoute(route: ActivatedRouteSnapshot): boolean {
    const path = route.routeConfig?.path;
    return this.BOOK_BROWSER_PATHS.includes(path || '');
  }

  shouldDetach(route: ActivatedRouteSnapshot): boolean {
    return this.isBookBrowserRoute(route);
  }

  store(route: ActivatedRouteSnapshot, handle: DetachedRouteHandle | null): void {
    if (handle && this.isBookBrowserRoute(route)) {
      const key = this.getRouteKey(route);

      // If the key already exists, remove it from insertion order so it
      // can be re-appended at the end (most-recently-used).
      const existingIdx = this.insertionOrder.indexOf(key);
      if (existingIdx !== -1) {
        this.insertionOrder.splice(existingIdx, 1);
      }

      this.storedRoutes.set(key, handle);
      this.insertionOrder.push(key);
      this.bookSelectionService.deselectAll();

      // Evict oldest routes when we exceed the budget.
      while (this.insertionOrder.length > MAX_STORED_ROUTES) {
        const evictKey = this.insertionOrder.shift()!;
        this.storedRoutes.delete(evictKey);
        this.scrollService.clearPosition(evictKey);
      }
    }
  }

  shouldAttach(route: ActivatedRouteSnapshot): boolean {
    if (!this.isBookBrowserRoute(route)) {
      return false;
    }
    const key = this.getRouteKey(route);
    return this.storedRoutes.has(key);
  }

  retrieve(route: ActivatedRouteSnapshot): DetachedRouteHandle | null {
    const key = this.getRouteKey(route);
    const handle = this.storedRoutes.get(key) || null;

    if (handle) {
      const savedPosition = this.scrollService.getPosition(key);
      if (savedPosition !== undefined) {
        setTimeout(() => {
          const scrollElement = document.querySelector('.virtual-scroller');
          if (scrollElement) {
            (scrollElement as HTMLElement).scrollTop = savedPosition;
          }
        }, 0);
      }
    }

    return handle;
  }

  shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
    return future.routeConfig === curr.routeConfig &&
      JSON.stringify(future.params) === JSON.stringify(curr.params);
  }
}
