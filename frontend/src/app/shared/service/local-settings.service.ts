import { inject, Injectable } from "@angular/core";
import { LocalStorageService } from "./local-storage.service";

export interface LocalSettings {
  cacheStorageEnabled: boolean;
  offlineReadingEnabled: boolean;
  maxOfflineBooks: number;
}

@Injectable({
  providedIn: "root",
})
export class LocalSettingsService {
  private localStorage = inject(LocalStorageService);
  public readonly storageName = "local_settings";

  public readonly defaultSettings: LocalSettings = {
    cacheStorageEnabled: true,
    offlineReadingEnabled: false,
    maxOfflineBooks: 5,
  };

  protected settings: LocalSettings;

  constructor() {
    this.settings = { ...this.defaultSettings };

    // Load settings from localStorage on initialization
    this.repairSettings();
    this.loadSettings();
  }

  get(): LocalSettings {
    return this.settings;
  }

  loadSettings(): void {
    const settings = this.getFromLocalStorage();
    if (settings) {
      this.setFields(settings);
    } else {
      this.setFields(this.defaultSettings);
      this.commitToLocalStorage(this.settings);
    }
  }

  commitSettings(): void {
    this.commitToLocalStorage(this.settings);
  }

  private getFromLocalStorage(): LocalSettings | null {
    const stored = this.localStorage.get<LocalSettings>(this.storageName);
    if (stored) {
      return { ...this.defaultSettings, ...stored }; // Defaults will be overridden by stored values
    }
    return null; // Settings does not exist or some fields is missing
  }

  private commitToLocalStorage(settings: LocalSettings): void {
    this.localStorage.set(this.storageName, settings);
  }

  /**
   * Ensure that all settings fields are present.
   * If any field is missing, reset to default settings.
   *
   */
  private repairSettings(): void {
    if (!this.getFromLocalStorage()) {
      this.commitToLocalStorage(this.defaultSettings);
    }
  }

  /**
   * Set the fields of the settings object.
   */
  private setFields(settings: LocalSettings): void {
    if (settings === this.settings) return; // Referential equality
    this.settings = { ...settings };
  }
}
