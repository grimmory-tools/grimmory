import {effect, inject, Injectable, signal} from '@angular/core';
import {UserService} from '../../../settings/user-management/user.service';

@Injectable({
  providedIn: 'root'
})
export class BookCardOverlayPreferenceService {
  private static readonly PERSIST_DELAY_MS = 500;

  private readonly userService = inject(UserService);

  private readonly _showBookTypePill = signal(true);
  readonly showBookTypePill = this._showBookTypePill.asReadonly();

  private currentContext: { type: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF', id: number } | null = null;
  private persistTimeout: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const user = this.userService.currentUser();
      if (user) {
        this.loadPreferencesFromUser();
      }
    });
  }

  setShowBookTypePill(show: boolean): void {
    this._showBookTypePill.set(show);
    this.schedulePersist(show);
  }

  private loadPreferencesFromUser(): void {
    const user = this.userService.getCurrentUser();
    const prefs = user?.userSettings?.entityViewPreferences;

    let show = true;
    if (prefs) {
      const globalAny = prefs.global as any;
      show = prefs.global?.overlayBookType ?? globalAny?.showBookTypePill ?? true;

      if (this.currentContext) {
        const override = prefs.overrides?.find(o =>
          o.entityType === this.currentContext?.type && o.entityId === this.currentContext?.id
        );
        if (override) {
          const prefAny = override.preferences as any;
          if (override.preferences.overlayBookType !== undefined) {
            show = override.preferences.overlayBookType;
          } else if (prefAny?.showBookTypePill !== undefined) {
            show = prefAny.showBookTypePill;
          }
        }
      }
    }

    if (this._showBookTypePill() !== show) {
      this._showBookTypePill.set(show);
    }
  }

  private schedulePersist(show: boolean): void {
    if (this.persistTimeout) {
      clearTimeout(this.persistTimeout);
    }

    this.persistTimeout = setTimeout(() => {
      this.persistPreference(show);
    }, BookCardOverlayPreferenceService.PERSIST_DELAY_MS);
  }

  private persistPreference(show: boolean): void {
    const user = this.userService.getCurrentUser();
    if (!user) return;

    const prefs = structuredClone(user.userSettings.entityViewPreferences ?? {
      global: {
        sortKey: 'addedOn',
        sortDir: 'DESC',
        view: 'GRID',
        coverSize: 1.0,
        seriesCollapsed: false,
        overlayBookType: true
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
            overlayBookType: show
          }
        };
        prefs.overrides.push(override);
      } else {
        override.preferences.overlayBookType = show;
      }
    } else {
      prefs.global.overlayBookType = show;
    }

    this.userService.updateUserSetting(user.id, 'entityViewPreferences', prefs);
  }
}
