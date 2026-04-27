import {DestroyRef, ElementRef, Injectable, Signal, inject} from '@angular/core';
import {ActivatedRoute, NavigationStart, Router} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {filter} from 'rxjs/operators';

interface TrackRouteOptions {
  scrollElement: Signal<ElementRef<HTMLElement> | undefined>;
  route: ActivatedRoute;
  destroyRef: DestroyRef;
  beforeSave?: () => void;
}

@Injectable({
  providedIn: 'root'
})
export class RouteScrollPositionService {
  private readonly router = inject(Router);
  private scrollPositions = new Map<string, number>();

  savePosition(key: string, position: number): void {
    this.scrollPositions.set(key, position);
  }

  getPosition(key: string): number | undefined {
    return this.scrollPositions.get(key);
  }

  createKey(path: string, params: Record<string, string>): string {
    const paramValues = Object.keys(params)
      .sort((a, b) => a.localeCompare(b))
      .map(key => params[key])
      .join('-');
    return paramValues ? `${path}:${paramValues}` : path;
  }

  keyFor(route: ActivatedRoute): string {
    const path = route.snapshot.routeConfig?.path ?? '';
    return this.createKey(path, route.snapshot.params);
  }

  trackRoute(options: TrackRouteOptions): void {
    this.router.events.pipe(
      filter(event => event instanceof NavigationStart),
      takeUntilDestroyed(options.destroyRef),
    ).subscribe(() => {
      options.beforeSave?.();
      const element = options.scrollElement()?.nativeElement;
      if (element) {
        this.savePosition(this.keyFor(options.route), element.scrollTop);
      }
    });
  }
}
