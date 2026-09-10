import {Injectable, inject} from '@angular/core';

import {LocalStorageService} from '../../../shared/service/local-storage.service';

const STORAGE_KEY = 'browseTableColumnWidths';

@Injectable({providedIn: 'root'})
export class BookBrowseColumnWidthPreferenceService {
  private readonly localStorage = inject(LocalStorageService);

  load(): Record<string, number> {
    const saved = this.localStorage.get<Record<string, unknown>>(STORAGE_KEY);
    if (saved == null || typeof saved !== 'object' || Array.isArray(saved)) {
      return {};
    }
    return Object.fromEntries(
      Object.entries(saved).filter(
        (entry): entry is [string, number] =>
          typeof entry[1] === 'number' && Number.isFinite(entry[1]) && entry[1] > 0,
      ),
    );
  }

  save(widths: Record<string, number>): void {
    this.localStorage.set(STORAGE_KEY, widths);
  }
}
