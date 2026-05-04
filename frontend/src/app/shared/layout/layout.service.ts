import { DOCUMENT } from '@angular/common';
import { DestroyRef, effect, inject, Injectable, signal, WritableSignal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { UserService } from '../../features/settings/user-management/user.service';
import { LocalStorageService } from '../service/local-storage.service';
import {
  DEFAULT_LIBRARY_SORT,
  DEFAULT_MAGIC_SHELF_SORT,
  DEFAULT_SHELF_SORT,
  normalizeSortPref,
  SidebarSortPreferenceKey,
  SortPref,
  sortPrefEqual,
} from './sidebar-sort-preferences';

export const SIDEBAR_MIN_WIDTH = 175;
export const SIDEBAR_MAX_WIDTH = 400;
export const SIDEBAR_DEFAULT_WIDTH = 225;
const SIDEBAR_EXPANDED_STATE_KEY = 'sidebarExpandedState';
const SIDEBAR_TRANSITION_MS = 220;

function readBooleanRecord(storage: LocalStorageService, key: string): Record<string, boolean> {
  const stored = storage.get<unknown>(key);
  if (!stored || typeof stored !== 'object' || Array.isArray(stored)) {
    return {};
  }

  return Object.fromEntries(
    Object.entries(stored).flatMap(([k, v]) =>
      typeof v === 'boolean' ? [[k, v] as const] : []
    )
  );
}

@Injectable({
  providedIn: 'root',
})
export class LayoutService {
  private readonly document = inject(DOCUMENT);
  private readonly localStorage = inject(LocalStorageService);
  private readonly userService = inject(UserService, { optional: true });
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly scale = signal(14);
  readonly currentPath = signal(this.router.url.split('?')[0]);
  readonly sidebarVisible = signal(true);
  readonly mobileDrawerOpen = signal(false);
  readonly sidebarCollapsed = signal(this.localStorage.get<boolean>('sidebarCollapsed') ?? false);
  readonly sidebarWidth = signal(this.clampSidebarWidth(this.localStorage.get<number>('sidebarWidth') ?? SIDEBAR_DEFAULT_WIDTH));
  readonly isDesktop = signal(this.computeIsDesktop());
  readonly sidebarExpandedState = signal<Readonly<Record<string, boolean>>>(
    readBooleanRecord(this.localStorage, SIDEBAR_EXPANDED_STATE_KEY)
  );
  private readonly _sidebarTransitioning = signal(false);
  readonly sidebarTransitioning = this._sidebarTransitioning.asReadonly();
  private readonly _librarySort = signal<SortPref>(DEFAULT_LIBRARY_SORT, { equal: sortPrefEqual });
  readonly librarySort = this._librarySort.asReadonly();
  private readonly _shelfSort = signal<SortPref>(DEFAULT_SHELF_SORT, { equal: sortPrefEqual });
  readonly shelfSort = this._shelfSort.asReadonly();
  private readonly _magicShelfSort = signal<SortPref>(DEFAULT_MAGIC_SHELF_SORT, { equal: sortPrefEqual });
  readonly magicShelfSort = this._magicShelfSort.asReadonly();
  private sidebarTransitionTimeoutId: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect(() => {
      this.changeScale(this.scale());
    });

    effect(() => {
      const settings = this.userService?.currentUser()?.userSettings;
      this._librarySort.set(normalizeSortPref(settings?.sidebarLibrarySorting, DEFAULT_LIBRARY_SORT));
      this._shelfSort.set(normalizeSortPref(settings?.sidebarShelfSorting, DEFAULT_SHELF_SORT));
      this._magicShelfSort.set(normalizeSortPref(settings?.sidebarMagicShelfSorting, DEFAULT_MAGIC_SHELF_SORT));
    });

    this.document.defaultView?.addEventListener('resize', this.onResize);
    this.destroyRef.onDestroy(() => {
      this.document.defaultView?.removeEventListener('resize', this.onResize);
      if (this.sidebarTransitionTimeoutId) {
        clearTimeout(this.sidebarTransitionTimeoutId);
      }
    });

    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((event) => {
        this.currentPath.set(event.urlAfterRedirects.split('?')[0]);
      });
  }

  onMenuToggle(): void {
    if (this.isDesktop()) {
      this.sidebarVisible.update((value) => !value);
    } else {
      this.mobileDrawerOpen.update((value) => !value);
    }
  }

  toggleSidebarCollapsed(): void {
    this.setSidebarCollapsed(!this.sidebarCollapsed());
  }

  setSidebarWidth(width: number, persist = true): void {
    const clamped = this.clampSidebarWidth(width);
    this.sidebarWidth.set(clamped);
    if (persist) {
      this.localStorage.set('sidebarWidth', clamped);
    }
  }

  private clampSidebarWidth(width: number): number {
    const rounded = Number.isFinite(width) ? Math.round(width) : SIDEBAR_DEFAULT_WIDTH;
    return Math.max(SIDEBAR_MIN_WIDTH, Math.min(SIDEBAR_MAX_WIDTH, rounded));
  }

  closeMobileSidebar(): void {
    this.mobileDrawerOpen.set(false);
  }

  isSidebarExpanded(key: string, defaultExpanded: boolean): boolean {
    const value = this.sidebarExpandedState()[key];
    return value === undefined ? defaultExpanded : value;
  }

  setSidebarExpanded(key: string, expanded: boolean): void {
    const next = { ...this.sidebarExpandedState(), [key]: expanded };
    this.sidebarExpandedState.set(next);
    this.localStorage.set(SIDEBAR_EXPANDED_STATE_KEY, next);
  }

  setLibrarySort(sort: SortPref): void {
    this.updateSidebarSort(this._librarySort, 'sidebarLibrarySorting', sort);
  }

  setShelfSort(sort: SortPref): void {
    this.updateSidebarSort(this._shelfSort, 'sidebarShelfSorting', sort);
  }

  setMagicShelfSort(sort: SortPref): void {
    this.updateSidebarSort(this._magicShelfSort, 'sidebarMagicShelfSorting', sort);
  }

  private computeIsDesktop(): boolean {
    return (this.document.defaultView?.innerWidth ?? 992) > 991;
  }

  private readonly onResize = (): void => {
    this.isDesktop.set(this.computeIsDesktop());
  };

  private setSidebarCollapsed(collapsed: boolean): void {
    if (collapsed !== this.sidebarCollapsed() && this.isDesktop()) {
      this.startSidebarTransition();
    }

    this.sidebarCollapsed.set(collapsed);
    this.localStorage.set('sidebarCollapsed', collapsed);
  }

  private startSidebarTransition(): void {
    if (this.sidebarTransitionTimeoutId) {
      clearTimeout(this.sidebarTransitionTimeoutId);
    }

    this._sidebarTransitioning.set(true);
    this.sidebarTransitionTimeoutId = setTimeout(() => {
      this._sidebarTransitioning.set(false);
      this.sidebarTransitionTimeoutId = undefined;
    }, SIDEBAR_TRANSITION_MS);
  }

  private changeScale(value: number): void {
    this.document.documentElement.style.fontSize = `${value}px`;
  }

  private updateSidebarSort(
    target: WritableSignal<SortPref>,
    key: SidebarSortPreferenceKey,
    sort: SortPref,
  ): void {
    const normalized = normalizeSortPref(sort, target());
    target.set(normalized);

    const userService = this.userService;
    const user = userService?.currentUser();
    if (user && userService) {
      userService.updateUserSetting(user.id, key, normalized);
    }
  }
}
