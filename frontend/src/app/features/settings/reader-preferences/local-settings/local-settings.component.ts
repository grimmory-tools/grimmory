import {Component, inject, OnInit, signal} from "@angular/core";
import {FormsModule} from "@angular/forms";
import {DecimalPipe} from "@angular/common";
import {Checkbox} from "primeng/checkbox";
import {Button} from "primeng/button";
import {ConfirmationService, MessageService} from "primeng/api";
import {TranslocoDirective} from "@jsverse/transloco";
import {LocalSettingsService, LocalSettings} from "../../../../shared/service/local-settings.service";
import {CacheStorageService} from "../../../../shared/service/cache-storage.service";

@Component({
  selector: "app-local-settings",
  imports: [
    FormsModule,
    DecimalPipe,
    Checkbox,
    Button,
    TranslocoDirective,
  ],
  templateUrl: "./local-settings.component.html",
  styleUrl: "./local-settings.component.scss",
})
export class LocalSettingsComponent implements OnInit {
  private localSettingsService = inject(LocalSettingsService);
  private cacheStorageService = inject(CacheStorageService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);

  protected settings: LocalSettings = this.localSettingsService.get();

  protected isLoadingCacheStorageUsage = signal(false);
  protected isClearingCacheStorage = signal(false);
  protected cacheStorageUsageMb = signal(0);

  async ngOnInit(): Promise<void> {
    await this.loadCacheStorageUsage();
  }

  onSettingChange(): void {
    this.localSettingsService.commitSettings();
  }

  clearCacheStorage(): void {
    this.confirmationService.confirm({
      message:
        "Are you sure you want to clear all Cache Storage data? This action cannot be undone.",
      header: "Clear Cache Storage",
      icon: "pi pi-exclamation-triangle",
      acceptLabel: "Clear",
      rejectLabel: "Cancel",
      acceptButtonProps: {
        label: "Clear",
        severity: "danger",
      },
      rejectButtonProps: {
        label: "Cancel",
        severity: "secondary",
      },
      accept: async () => {
        this.isClearingCacheStorage.set(true);
        try {
          await this.cacheStorageService.clear();
          await this.loadCacheStorageUsage();
          this.messageService.add({
            severity: "success",
            summary: "Storage Cache cleared",
            detail: "All Cache Storage data has been cleared successfully.",
          });
        } catch {
          this.messageService.add({
            severity: "error",
            summary: "Clear cache failed",
            detail: "Unable to clear Cache Storage. Please try again.",
          });
        } finally {
          this.isClearingCacheStorage.set(false);
        }
      },
    });
  }

  async loadCacheStorageUsage(): Promise<void> {
    this.isLoadingCacheStorageUsage.set(true);
    try {
      const totalBytes = await this.cacheStorageService.getCacheSizeInBytes();
      this.cacheStorageUsageMb.set(totalBytes / (1024 * 1024));
    } catch (error) {
      console.error("Error loading Cache Storage usage:", error);
      this.cacheStorageUsageMb.set(0);
    } finally {
      this.isLoadingCacheStorageUsage.set(false);
    }
  }
}
