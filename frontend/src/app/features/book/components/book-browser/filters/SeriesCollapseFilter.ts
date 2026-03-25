import {effect, inject, Injectable, signal} from '@angular/core';
import {Book} from '../../../model/book.model';
import {MessageService} from 'primeng/api';
import {UserService} from '../../../../settings/user-management/user.service';

@Injectable({providedIn: 'root'})
export class SeriesCollapseFilter {
  private static readonly PERSIST_DELAY_MS = 500;

  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);

  private readonly _seriesCollapsed = signal(false);
  readonly seriesCollapsed = this._seriesCollapsed.asReadonly();
  private currentContext: { type: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF', id: number } | null = null;
  private persistTimeout: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const user = this.userService.currentUser();
      if (user) {
        this.applyPreference();
      }
    });
  }

  setContext(type: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF' | null, id: number | null): void {
    if (type && id) {
      this.currentContext = {type, id};
    } else {
      this.currentContext = null;
    }
    this.applyPreference();
  }

  setCollapsed(value: boolean): void {
    this._seriesCollapsed.set(value);
    this.schedulePersist(value);
  }

  private applyPreference(): void {
    const user = this.userService.getCurrentUser();
    const prefs = user?.userSettings?.entityViewPreferences;

    let collapsed = false;

    if (prefs) {
      // Backward compatibility: check for old 'seriesCollapse' field
      const legacyGlobalSeriesCollapse = (prefs.global as { seriesCollapse?: boolean }).seriesCollapse;
      collapsed = prefs.global?.seriesCollapsed ?? legacyGlobalSeriesCollapse ?? false;

      if (this.currentContext) {
        const override = prefs.overrides?.find(o =>
          o.entityType === this.currentContext?.type && o.entityId === this.currentContext?.id
        );
        if (override) {
           const legacyOverrideSeriesCollapse = (override.preferences as { seriesCollapse?: boolean }).seriesCollapse;
           if (override.preferences.seriesCollapsed !== undefined) {
             collapsed = override.preferences.seriesCollapsed;
           } else if (legacyOverrideSeriesCollapse !== undefined) {
             collapsed = legacyOverrideSeriesCollapse;
           }
        }
      }
    }

    if (this._seriesCollapsed() !== collapsed) {
      this._seriesCollapsed.set(collapsed);
    }
  }

  collapseBooks(books: Book[], forceExpandSeries?: boolean, isSeriesCollapsed: boolean = this.seriesCollapsed()): Book[] {
    const shouldCollapse = forceExpandSeries ? false : isSeriesCollapsed;
    if (!shouldCollapse || books.length === 0) return books;

    const sourceBooks = [...books];
    const seriesMap = new Map<string, Book[]>();
    const collapsedBooks: Book[] = [];

    for (const book of sourceBooks) {
      const seriesName = book.metadata?.seriesName?.trim();
      if (seriesName) {
        if (!seriesMap.has(seriesName)) {
          seriesMap.set(seriesName, []);
        }
        seriesMap.get(seriesName)!.push(book);
      } else {
        collapsedBooks.push(book);
      }
    }

    for (const group of seriesMap.values()) {
      const sortedGroup = group.slice().sort((a, b) => {
        const aNum = a.metadata?.seriesNumber ?? Number.MAX_VALUE;
        const bNum = b.metadata?.seriesNumber ?? Number.MAX_VALUE;
        return aNum - bNum;
      });
      const firstBook = sortedGroup[0];
      collapsedBooks.push({
        ...firstBook,
        seriesBooks: group,
        seriesCount: group.length
      });
    }

    return collapsedBooks;
  }

  private schedulePersist(isCollapsed: boolean): void {
    if (this.persistTimeout) {
      clearTimeout(this.persistTimeout);
    }

    this.persistTimeout = setTimeout(() => {
      this.persistCollapsePreference(isCollapsed);
    }, SeriesCollapseFilter.PERSIST_DELAY_MS);
  }

  private persistCollapsePreference(isCollapsed: boolean): void {
    const user = this.userService.getCurrentUser();
    if (!user) return;

    const prefs = structuredClone(user.userSettings.entityViewPreferences ?? {
      global: {
        sortKey: 'addedOn',
        sortDir: 'DESC',
        view: 'GRID',
        coverSize: 1.0,
        seriesCollapsed: false
      },
      overrides: []
    });

    if (!prefs.overrides) {
      prefs.overrides = [];
    }

    if (this.currentContext) {
      let override = prefs.overrides.find(o =>
        o.entityType === this.currentContext?.type && o.entityId === this.currentContext?.id
      );

      if (!override) {
        override = {
          entityType: this.currentContext.type,
          entityId: this.currentContext.id,
          preferences: {
            ...prefs.global,
            seriesCollapsed: isCollapsed
          }
        };
        prefs.overrides.push(override);
      } else {
        override.preferences.seriesCollapsed = isCollapsed;
      }
    } else {
      prefs.global.seriesCollapsed = isCollapsed;
    }

    this.userService.updateUserSetting(user.id, 'entityViewPreferences', prefs);

    this.messageService.add({
      severity: 'success',
      summary: 'Preference Saved',
      detail: `Series collapse set to ${isCollapsed ? 'enabled' : 'disabled'}${this.currentContext ? ' for this view' : ' globally'}.`,
      life: 1500
    });
  }
}
