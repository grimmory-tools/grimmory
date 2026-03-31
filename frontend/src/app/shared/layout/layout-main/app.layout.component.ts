import { DOCUMENT, NgClass } from '@angular/common';
import { computed, Component, DestroyRef, effect, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { LayoutService } from '../layout.service';
import { AppMenuComponent } from '../layout-menu/app.menu.component';
import { AppTopBarComponent } from '../layout-topbar/app.topbar.component';
import { TranslocoDirective } from '@jsverse/transloco';
import { LocalStorageService } from '../../service/local-storage.service';

@Component({
  selector: 'app-layout',
  imports: [
    RouterOutlet,
    AppMenuComponent,
    AppTopBarComponent,
    NgClass,
    TranslocoDirective
  ],
  templateUrl: './app.layout.component.html'
})
export class AppLayoutComponent implements OnInit {
  readonly layoutService = inject(LayoutService);
  private readonly router = inject(Router);
  private readonly localStorageService = inject(LocalStorageService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  readonly containerClass = computed(() => ({
    'layout-static': true,
    'layout-static-inactive': !this.layoutService.sidebarOpen(),
    'layout-mobile-active': this.layoutService.mobileSidebarOpen()
  }));

  constructor() {
    effect((onCleanup) => {
      const body = this.document.body;
      body.classList.toggle('blocked-scroll', this.layoutService.mobileSidebarOpen());
      onCleanup(() => {
        body.classList.remove('blocked-scroll');
      });
    });

    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        this.layoutService.closeMobileSidebar();
      });
  }

  ngOnInit(): void {
    const width = this.localStorageService.get<number>('sidebarWidth') ?? 225;
    this.document.documentElement.style.setProperty('--sidebar-width', `${width}px`);
  }

  closeMobileSidebar(): void {
    this.layoutService.closeMobileSidebar();
  }
}
