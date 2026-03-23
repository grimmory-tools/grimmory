import {inject, Injectable, signal} from '@angular/core';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {LocalStorageService} from '../../../../../shared/service/local-storage.service';

@Injectable({
  providedIn: 'root'
})
export class SidebarFilterTogglePrefService {

  private readonly STORAGE_KEY = 'showSidebarFilter';
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private readonly localStorageService = inject(LocalStorageService);

  private readonly _showFilter = signal(true);
  readonly showFilter = this._showFilter.asReadonly();

  constructor() {
    const isNarrow = window.innerWidth <= 768;
    this._showFilter.set(!isNarrow);
    this.loadFromStorage();
  }

  toggle(): void {
    this.setShowFilter(!this.showFilter());
  }

  setShowFilter(value: boolean): void {
    if (this._showFilter() !== value) {
      this._showFilter.set(value);
      this.savePreference(value);
    }
  }

  private savePreference(value: boolean): void {
    try {
      this.localStorageService.set(this.STORAGE_KEY, value);
    } catch {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('book.filterPref.toast.saveFailedSummary'),
        detail: this.t.translate('book.filterPref.toast.saveFailedDetail'),
        life: 3000
      });
    }
  }

  private loadFromStorage(): void {
    const isNarrow = window.innerWidth <= 768;
    if (isNarrow) {
      this._showFilter.set(false);
    } else {
      const saved = this.localStorageService.get<boolean>(this.STORAGE_KEY);
      this._showFilter.set(saved !== null ? saved : true);
    }
  }
}
