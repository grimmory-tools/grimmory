import {inject, Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';
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

  private readonly showFilterSubject = new BehaviorSubject<boolean>(true);
  readonly showFilter$ = this.showFilterSubject.asObservable();

  constructor() {
    const isNarrow = window.innerWidth <= 768;
    this.showFilterSubject = new BehaviorSubject<boolean>(!isNarrow);
    this.showFilter$ = this.showFilterSubject.asObservable();
    this.loadFromStorage();
  }

  get selectedShowFilter(): boolean {
    return this.showFilterSubject.value;
  }

  set selectedShowFilter(value: boolean) {
    if (this.showFilterSubject.value !== value) {
      this.showFilterSubject.next(value);
      this.savePreference(value);
    }
  }

  toggle(): void {
    this.selectedShowFilter = !this.selectedShowFilter;
  }

  private savePreference(value: boolean): void {
    try {
      this.localStorageService.set(this.STORAGE_KEY, value);
    } catch (e) {
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
      this.showFilterSubject.next(false);
    } else {
      const saved = this.localStorageService.get<boolean>(this.STORAGE_KEY);
      this.showFilterSubject.next(saved !== null ? saved : true);
    }
  }
}
