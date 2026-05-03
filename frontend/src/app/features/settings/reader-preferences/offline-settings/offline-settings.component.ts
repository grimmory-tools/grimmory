import {Component, inject, OnInit, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DecimalPipe} from '@angular/common';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {InputNumber} from 'primeng/inputnumber';
import {Button} from 'primeng/button';
import {ConfirmationService, MessageService} from 'primeng/api';
import {TranslocoDirective} from '@jsverse/transloco';
import {LocalSettingsService} from '../../../../shared/service/local-settings.service';
import {OfflineStorageService} from '../../../../shared/service/offline-storage.service';
import {OfflineIndexedDBService} from '../../../../shared/service/offline-indexeddb.service';

@Component({
  selector: 'app-offline-settings',
  standalone: true,
  imports: [
    FormsModule,
    DecimalPipe,
    ToggleSwitch,
    InputNumber,
    Button,
    TranslocoDirective,
  ],
  templateUrl: './offline-settings.component.html',
  styleUrl: './offline-settings.component.scss',
})
export class OfflineSettingsComponent implements OnInit {
  private localSettingsService = inject(LocalSettingsService);
  private offlineStorage = inject(OfflineStorageService);
  private offlineIndexedDB = inject(OfflineIndexedDBService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);

  protected settings = this.localSettingsService.get();

  protected opfsAvailable = signal(false);
  protected storageUsageBytes = signal(0);
  protected storageQuotaBytes = signal(0);
  protected storageBookCount = signal(0);
  protected isLoadingStorage = signal(false);
  protected isClearing = signal(false);

  async ngOnInit(): Promise<void> {
    this.opfsAvailable.set(await this.offlineStorage.isAvailable());
    await this.loadStorageInfo();
  }

  onToggleChange(): void {
    this.localSettingsService.commitSettings();
    if (!this.settings.offlineReadingEnabled) {
      this.clearAllBooksSilently();
    }
  }

  onMaxBooksChange(): void {
    this.localSettingsService.commitSettings();
  }

  async loadStorageInfo(): Promise<void> {
    this.isLoadingStorage.set(true);
    try {
      const estimate = await navigator.storage.estimate();
      this.storageQuotaBytes.set(estimate.quota ?? 0);
      this.storageUsageBytes.set(estimate.usage ?? 0);

      const info = await this.offlineStorage.getStorageInfo();
      this.storageBookCount.set(info.bookCount);
    } catch {
      this.storageUsageBytes.set(0);
      this.storageQuotaBytes.set(0);
      this.storageBookCount.set(0);
    } finally {
      this.isLoadingStorage.set(false);
    }
  }

  clearAllBooks(): void {
    this.confirmationService.confirm({
      message: 'Are you sure you want to clear all cached books? This action cannot be undone.',
      header: 'Clear All Cached Books',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Clear',
      rejectLabel: 'Cancel',
      acceptButtonProps: {
        label: 'Clear',
        severity: 'danger',
      },
      rejectButtonProps: {
        label: 'Cancel',
        severity: 'secondary',
      },
      accept: async () => {
        this.isClearing.set(true);
        try {
          await this.offlineStorage.clearAllBooks();
          await this.loadStorageInfo();
          this.messageService.add({
            severity: 'success',
            summary: 'Cached books cleared',
            detail: 'All cached book files have been removed.',
          });
        } catch {
          this.messageService.add({
            severity: 'error',
            summary: 'Clear failed',
            detail: 'Unable to clear cached books. Please try again.',
          });
        } finally {
          this.isClearing.set(false);
        }
      },
    });
  }

  private async clearAllBooksSilently(): Promise<void> {
    try {
      await this.offlineStorage.clearAllBooks();
      this.storageBookCount.set(0);
      this.storageUsageBytes.set(0);
    } catch {
      // silent
    }
  }
}
