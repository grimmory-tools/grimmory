import {effect, inject, Injectable, signal} from '@angular/core';
import {DashboardConfig, DEFAULT_DASHBOARD_CONFIG, ScrollerType} from '../models/dashboard-config.model';
import {UserService} from '../../settings/user-management/user.service';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';

@Injectable({
  providedIn: 'root'
})
export class DashboardConfigService {
  private readonly userService = inject(UserService);
  private readonly magicShelfService = inject(MagicShelfService);

  private readonly _config = signal<DashboardConfig>(this.cloneConfig(DEFAULT_DASHBOARD_CONFIG));
  readonly config = this._config.asReadonly();

  constructor() {
    effect(() => {
      const user = this.userService.currentUser();
      if (user) {
        const dashboardConfig = user.userSettings?.dashboardConfig as DashboardConfig;
        this._config.set(this.normalizeConfig(dashboardConfig));
      }
    });

    effect(() => {
      const shelves = this.magicShelfService.shelves();
      const currentConfig = this._config();
      const syncedConfig = this.syncMagicShelfTitles(currentConfig, shelves);

      if (syncedConfig) {
        this._config.set(syncedConfig);
        const user = this.userService.getCurrentUser();
        if (user) {
          this.userService.updateUserSetting(user.id, 'dashboardConfig', syncedConfig);
        }
      }
    });
  }

  saveConfig(config: DashboardConfig): void {
    const nextConfig = this.normalizeConfig(config);
    this._config.set(nextConfig);

    const user = this.userService.getCurrentUser();
    if (user) {
      this.userService.updateUserSetting(user.id, 'dashboardConfig', nextConfig);
    }
  }

  resetToDefault(): void {
    this.saveConfig(DEFAULT_DASHBOARD_CONFIG);
  }

  private normalizeConfig(config: DashboardConfig | null | undefined): DashboardConfig {
    if (!config?.scrollers?.length) {
      return this.cloneConfig(DEFAULT_DASHBOARD_CONFIG);
    }

    return this.cloneConfig(config);
  }

  private cloneConfig(config: DashboardConfig): DashboardConfig {
    return {
      scrollers: config.scrollers.map(scroller => ({...scroller}))
    };
  }

  private syncMagicShelfTitles(
    config: DashboardConfig,
    shelves: { id?: number | null; name: string }[]
  ): DashboardConfig | null {
    let updated = false;

    const scrollers = config.scrollers.map(scroller => {
      if (scroller.type !== ScrollerType.MAGIC_SHELF || !scroller.magicShelfId) {
        return scroller;
      }

      const shelf = shelves.find(currentShelf => currentShelf.id === scroller.magicShelfId);
      if (!shelf || scroller.title === shelf.name) {
        return scroller;
      }

      updated = true;
      return {
        ...scroller,
        title: shelf.name
      };
    });

    if (!updated) {
      return null;
    }

    return {scrollers};
  }
}
