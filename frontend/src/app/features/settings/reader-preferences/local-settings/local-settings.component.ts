import {Component, inject, OnInit, signal} from "@angular/core";
import {FormsModule} from "@angular/forms";
import {DecimalPipe} from "@angular/common";
import {Checkbox} from "@openng/optimus-ui/checkbox";
import {Button} from "@openng/optimus-ui/button";
import {ConfirmationService, MessageService} from "@openng/optimus-ui/api";
import {TranslocoDirective, TranslocoService} from "@jsverse/transloco";
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
  private readonly localSettingsService = inject(LocalSettingsService);
  private readonly cacheStorageService = inject(CacheStorageService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly t = inject(TranslocoService);

  protected settings: LocalSettings = this.localSettingsService.get();

  protected readonly isLoadingCacheStorageUsage = signal(false);
  protected readonly isClearingCacheStorage = signal(false);
  protected readonly cacheStorageUsageMb = signal(0);

  async ngOnInit(): Promise<void> {
    await this.loadCacheStorageUsage();
  }

  onSettingChange(): void {
    this.localSettingsService.commitSettings();
  }

  clearCacheStorage(): void {
    this.confirmationService.confirm({
      message:
        this.t.translate("settingsNaming.localSettings.confirm.clearMessage"),
      header: this.t.translate("settingsNaming.localSettings.confirm.clearHeader"),
      icon: "pi pi-exclamation-triangle",
      acceptLabel: this.t.translate("settingsNaming.localSettings.clear"),
      rejectLabel: this.t.translate("common.cancel"),
      acceptButtonProps: {
        label: this.t.translate("settingsNaming.localSettings.clear"),
        severity: "danger",
      },
      rejectButtonProps: {
        label: this.t.translate("common.cancel"),
        severity: "secondary",
      },
      accept: async () => {
        this.isClearingCacheStorage.set(true);
        try {
          await this.cacheStorageService.clear();
          await this.loadCacheStorageUsage();
          this.messageService.add({
            severity: "success",
            summary: this.t.translate("settingsNaming.localSettings.toast.clearSuccessSummary"),
            detail: this.t.translate("settingsNaming.localSettings.toast.clearSuccessDetail"),
          });
        } catch {
          this.messageService.add({
            severity: "error",
            summary: this.t.translate("settingsNaming.localSettings.toast.clearFailedSummary"),
            detail: this.t.translate("settingsNaming.localSettings.toast.clearFailedDetail"),
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
