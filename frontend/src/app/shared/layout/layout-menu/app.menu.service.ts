import { inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class MenuService {
  private readonly router = inject(Router);

  /** Current URL path (without query string), updated once per navigation. */
  readonly currentPath = signal(this.router.url.split('?')[0]);

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe((event) => {
        this.currentPath.set(event.urlAfterRedirects.split('?')[0]);
      });
  }
}
