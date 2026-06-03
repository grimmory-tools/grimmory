import {computed, effect, inject, Injectable, signal} from '@angular/core';
import {BookdropFileApiService} from './bookdrop-file-api.service';
import {AuthService} from '../../../shared/service/auth.service';
import {UserService} from '../../settings/user-management/user.service';

export interface BookdropFileNotification {
  pendingCount: number;
  totalCount: number;
  lastUpdatedAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class BookdropFileService {
  private readonly summaryState = signal<BookdropFileNotification>({
    pendingCount: 0,
    totalCount: 0
  });

  readonly summary = this.summaryState.asReadonly();
  readonly hasPendingFiles = computed(() => this.summary().pendingCount > 0);

  private readonly apiService = inject(BookdropFileApiService);
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);

  constructor() {
    effect(() => {
      const user = this.userService.currentUser();
      const token = this.authService.token();

      if (!user || !token || !(user.permissions.admin || user.permissions.canAccessBookdrop)) {
        return;
      }

      this.refresh();
    });
  }

  handleIncomingFile(summary: BookdropFileNotification): void {
    this.summaryState.set(summary);
  }

  refresh(): void {
    this.apiService.getNotification().subscribe({
      next: summary => this.summaryState.set(summary),
      error: err => console.warn('Failed to refresh bookdrop file summary:', err)
    });
  }
}
