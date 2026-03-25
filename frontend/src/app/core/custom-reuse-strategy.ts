import {inject, Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, DetachedRouteHandle, RouteReuseStrategy} from '@angular/router';
import {BookBrowserScrollService} from '../features/book/components/book-browser/book-browser-scroll.service';
import {BookSelectionService} from '../features/book/components/book-browser/book-selection.service';

@Injectable({
  providedIn: 'root',
})
export class CustomReuseStrategy implements RouteReuseStrategy {
  private storedRoutes = new Map<string, DetachedRouteHandle>();
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
      this.storedRoutes.set(key, handle);
      this.bookSelectionService.deselectAll();
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
