import { DOCUMENT, NgClass } from '@angular/common';
import { computed, Component, DestroyRef, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { LayoutService, SIDEBAR_MAX_WIDTH, SIDEBAR_MIN_WIDTH } from '../layout.service';
import { AppMenuComponent } from '../layout-menu/app.menu.component';
import { AppMobileTopbarComponent } from '../layout-mobile-topbar/app.mobile-topbar.component';
import { TranslocoDirective } from '@jsverse/transloco';

const SIDEBAR_COLLAPSED_WIDTH = 'var(--sidebar-collapsed-width)';
const SIDEBAR_KEYBOARD_STEP = 16;
const SIDEBAR_KEYBOARD_PAGE_STEP = 48;

@Component({
  selector: 'app-layout',
  imports: [
    RouterOutlet,
    AppMenuComponent,
    AppMobileTopbarComponent,
    CdkTrapFocus,
    NgClass,
    TranslocoDirective
  ],
  templateUrl: './app.layout.component.html'
})
export class AppLayoutComponent {
  readonly layoutService = inject(LayoutService);
  readonly sidebarMinWidth = SIDEBAR_MIN_WIDTH;
  readonly sidebarMaxWidth = SIDEBAR_MAX_WIDTH;

  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  private resizeStartX = 0;
  private resizeStartWidth = 0;
  private isResizing = false;

  readonly containerClass = computed(() => ({
    'layout-static': true,
    'layout-static-inactive': !this.layoutService.sidebarVisible(),
    'layout-mobile-active': this.layoutService.mobileDrawerOpen(),
    'layout-collapsed': this.layoutService.sidebarCollapsed() && this.layoutService.isDesktop()
  }));

  readonly storedSidebarWidth = computed(() => `${this.layoutService.sidebarWidth()}px`);

  constructor() {
    this.destroyRef.onDestroy(() => this.cleanupResize(true));

    effect((onCleanup) => {
      const body = this.document.body;
      body.classList.toggle('blocked-scroll', this.layoutService.mobileDrawerOpen());
      onCleanup(() => {
        body.classList.remove('blocked-scroll');
      });
    });

    effect(() => {
      const collapsed = this.layoutService.sidebarCollapsed();
      const isDesktop = this.layoutService.isDesktop();
      const storedWidth = this.layoutService.sidebarWidth();
      const width = (collapsed && isDesktop) ? SIDEBAR_COLLAPSED_WIDTH : `${storedWidth}px`;
      this.document.documentElement.style.setProperty('--sidebar-width', width);
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

  closeMobileSidebar(): void {
    this.layoutService.closeMobileSidebar();
  }

  startResize(event: MouseEvent): void {
    if (this.layoutService.sidebarCollapsed() || !this.layoutService.isDesktop()) {
      return;
    }
    event.preventDefault();
    this.isResizing = true;
    this.resizeStartX = event.clientX;
    this.resizeStartWidth = this.layoutService.sidebarWidth();
    this.document.body.classList.add('layout-resizing-cursor');
    this.document.addEventListener('mousemove', this.onResizeMove);
    this.document.addEventListener('mouseup', this.onResizeEnd);
  }

  onResizeKeydown(event: KeyboardEvent): void {
    if (this.layoutService.sidebarCollapsed() || !this.layoutService.isDesktop()) {
      return;
    }

    const nextWidth = this.getKeyboardResizeWidth(event.key, this.layoutService.sidebarWidth());
    if (nextWidth === null) return;

    event.preventDefault();
    this.layoutService.setSidebarWidth(nextWidth, true);
  }

  private readonly onResizeMove = (event: MouseEvent) => {
    if (!this.isResizing) return;
    const dx = event.clientX - this.resizeStartX;
    this.layoutService.setSidebarWidth(this.resizeStartWidth + dx, false);
  };

  private readonly onResizeEnd = () => {
    this.cleanupResize(true);
  };

  private getKeyboardResizeWidth(key: string, currentWidth: number): number | null {
    switch (key) {
      case 'ArrowLeft':
        return currentWidth - SIDEBAR_KEYBOARD_STEP;
      case 'ArrowRight':
        return currentWidth + SIDEBAR_KEYBOARD_STEP;
      case 'PageDown':
        return currentWidth - SIDEBAR_KEYBOARD_PAGE_STEP;
      case 'PageUp':
        return currentWidth + SIDEBAR_KEYBOARD_PAGE_STEP;
      case 'Home':
        return SIDEBAR_MIN_WIDTH;
      case 'End':
        return SIDEBAR_MAX_WIDTH;
      default:
        return null;
    }
  }

  private cleanupResize(persistWidth: boolean): void {
    if (!this.isResizing) return;
    this.isResizing = false;
    if (persistWidth) {
      this.layoutService.setSidebarWidth(this.layoutService.sidebarWidth(), true);
    }
    this.document.body.classList.remove('layout-resizing-cursor');
    this.document.removeEventListener('mousemove', this.onResizeMove);
    this.document.removeEventListener('mouseup', this.onResizeEnd);
  }
}
