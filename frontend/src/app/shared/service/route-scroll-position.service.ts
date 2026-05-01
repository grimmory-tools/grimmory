import {DestroyRef, ElementRef, Injectable, Signal, inject} from '@angular/core';
import {ActivatedRoute, NavigationStart, Router} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {filter} from 'rxjs/operators';

interface TrackRouteOptions {
  scrollElement: Signal<ElementRef<HTMLElement> | undefined>;
  route: ActivatedRoute;
  destroyRef: DestroyRef;
  dismissOverlaysBeforeSave?: boolean;
  beforeSave?: () => void;
}

@Injectable({
  providedIn: 'root'
})
export class RouteScrollPositionService {
  private readonly MAX_SCROLL_POSITIONS = 100;
  private readonly router = inject(Router);
  private scrollPositions = new Map<string, number>();

  savePosition(key: string, position: number): void {
    this.scrollPositions.delete(key);
    this.scrollPositions.set(key, position);
    if (this.scrollPositions.size > this.MAX_SCROLL_POSITIONS) {
      const oldestKey = this.scrollPositions.keys().next().value;
      if (oldestKey !== undefined) {
        this.scrollPositions.delete(oldestKey);
      }
    }
  }

  getPosition(key: string): number | undefined {
    const position = this.scrollPositions.get(key);
    if (position !== undefined) {
      this.scrollPositions.delete(key);
      this.scrollPositions.set(key, position);
    }
    return position;
  }

  createKey(path: string, params: Record<string, string>): string {
    const paramPairs = Object.keys(params)
      .sort((a, b) => a.localeCompare(b))
      .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
      .join(';');
    return paramPairs ? `${path}:${paramPairs}` : path;
  }

  keyFor(route: ActivatedRoute): string {
    const path = route.snapshot.pathFromRoot
      .flatMap(snapshot => snapshot.url)
      .map(segment => segment.path)
      .filter(Boolean)
      .join('/');
    return this.createKey(path, route.snapshot.params);
  }

  dismissBodyOverlays(): void {
    document.querySelectorAll('.p-tieredmenu-overlay').forEach(el => el.remove());
  }

  trackRoute(options: TrackRouteOptions): void {
    this.router.events.pipe(
      filter((event): event is NavigationStart => event instanceof NavigationStart),
      takeUntilDestroyed(options.destroyRef),
    ).subscribe(() => {
      if (options.dismissOverlaysBeforeSave) {
        this.dismissBodyOverlays();
      }
      options.beforeSave?.();
      const element = options.scrollElement()?.nativeElement;
      if (element) {
        this.savePosition(this.keyFor(options.route), element.scrollTop);
      }
    });
  }
}
