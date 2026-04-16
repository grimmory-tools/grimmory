import { Component, DestroyRef, effect, inject, OnDestroy, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LayoutService } from '../layout.service';
import { Router, RouterLink } from '@angular/router';
import { Tooltip } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';

import { BookSearcherComponent } from '../../../features/book/components/book-searcher/book-searcher.component';
import { NgClass, NgStyle } from '@angular/common';
import { NotificationEventService } from '../../websocket/notification-event.service';
import { StyleClass } from 'primeng/styleclass';
import { Divider } from 'primeng/divider';
import { ThemeConfiguratorComponent } from '../theme-configurator/theme-configurator.component';
import { AuthService } from '../../service/auth.service';
import { UserService } from '../../../features/settings/user-management/user.service';
import { Popover } from 'primeng/popover';
import { MetadataProgressService } from '../../service/metadata-progress.service';
import { MetadataBatchProgressNotification } from '../../model/metadata-batch-progress.model';
import { BookdropFileService } from '../../../features/bookdrop/service/bookdrop-file.service';
import { DialogLauncherService } from '../../services/dialog-launcher.service';
import { UnifiedNotificationBoxComponent } from '../../components/unified-notification-popover/unified-notification-popover-component';
import { Severity, LogNotification } from '../../websocket/model/log-notification.model';
import { Menu } from 'primeng/menu';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { AVAILABLE_LANGS, LANG_LABELS } from '../../../core/config/transloco-loader';
import { LANG_STORAGE_KEY } from '../../../core/config/language-initializer';
import type { MenuItem } from 'primeng/api';

@Component({
  selector: 'app-topbar',
  templateUrl: './app.topbar.component.html',
  styleUrls: ['./app.topbar.component.scss'],
  imports: [
    RouterLink,
    Tooltip,
    FormsModule,
    BookSearcherComponent,
    ThemeConfiguratorComponent,
    StyleClass,
    NgClass,
    Divider,
    Popover,
    UnifiedNotificationBoxComponent,
    NgStyle,
    Menu,
    TranslocoDirective,
  ],
})
export class AppTopBarComponent implements OnDestroy {
  public readonly layoutService = inject(LayoutService);
  protected readonly userService = inject(UserService);
  protected readonly user = this.userService.currentUser;
  private readonly notificationService = inject(NotificationEventService);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly metadataProgressService = inject(MetadataProgressService);
  private readonly bookdropFileService = inject(BookdropFileService);
  private readonly dialogLauncher = inject(DialogLauncherService);
  private readonly translocoService = inject(TranslocoService);
  statsMenuItems: MenuItem[] = [];
  @ViewChild('statsMenu') statsMenu?: Menu;

  isMenuVisible = true;
  progressHighlight = false;
  completedTaskCount = 0;
  hasActiveOrCompletedTasks = false;
  showPulse = false;
  hasAnyTasks = false;
  hasPendingBookdropFiles = false;

  private readonly destroyRef = inject(DestroyRef);
  private eventTimer: number | undefined;

  private latestTasks: Record<string, MetadataBatchProgressNotification> = {};
  private latestHasPendingFiles = false;
  private latestNotificationSeverity?: Severity;

  activeLang = this.translocoService.getActiveLang();
  langMenuItems: MenuItem[] = [];

  constructor() {
    this.langMenuItems = this.buildLanguageActions(this.activeLang);

    this.subscribeToMetadataProgress();
    this.subscribeToNotifications();

    this.destroyRef.onDestroy(() => clearTimeout(this.eventTimer));

    this.metadataProgressService.activeTasks$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((tasks) => {
        this.latestTasks = tasks;
        this.hasAnyTasks = Object.keys(tasks).length > 0;
        this.updateCompletedTaskCount();
        this.updateTaskVisibility(tasks);
      });

    this.bookdropFileService.hasPendingFiles$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((hasPending) => {
        this.latestHasPendingFiles = hasPending;
        this.hasPendingBookdropFiles = hasPending;
        this.updateCompletedTaskCount();
        this.updateTaskVisibilityWithBookdrop();
      });

    effect(() => {
      this.user();
      this.initializeStatsMenu();
    });

    this.translocoService.langChanges$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.initializeStatsMenu();
      });
  }

  ngOnDestroy(): void {
    clearTimeout(this.eventTimer);
  }

  toggleMenu() {
    this.isMenuVisible = !this.isMenuVisible;
    this.layoutService.onMenuToggle();
  }

  openLibraryCreatorDialog(): void {
    this.dialogLauncher.openLibraryCreateDialog();
  }

  openFileUploadDialog(): void {
    this.dialogLauncher.openFileUploadDialog();
  }

  openUserProfileDialog(): void {
    this.dialogLauncher.openUserProfileDialog();
  }

  navigateToSettings() {
    this.router.navigate(['/settings']);
  }

  navigateToBookdrop() {
    this.router.navigate(['/bookdrop']);
  }

  navigateToMetadataManager() {
    this.router.navigate(['/metadata-manager']);
  }

  navigateToStats() {
    this.router.navigate(['/library-stats']);
  }

  navigateToUserStats() {
    this.router.navigate(['/reading-stats']);
  }

  switchLanguage(lang: string) {
    if (lang === this.activeLang) return;
    this.translocoService.load(lang).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.translocoService.setActiveLang(lang);
      localStorage.setItem(LANG_STORAGE_KEY, lang);
      this.activeLang = lang;
      this.langMenuItems = this.buildLanguageActions(lang);
    });
  }

  logout() {
    this.authService.logout();
  }

  handleStatsButtonClick() {
    if (this.statsMenuItems.length === 0) {
      return;
    }

    if (this.statsMenuItems.length === 1) {
      this.statsMenuItems[0].command?.({});
    }
  }

  private subscribeToMetadataProgress() {
    this.metadataProgressService.progressUpdates$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((progress) => {
        this.progressHighlight = progress.status === 'IN_PROGRESS';
      });
  }

  private subscribeToNotifications() {
    this.notificationService.latestNotification$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((notification: LogNotification) => {
        this.latestNotificationSeverity = notification.severity;
        this.triggerPulseEffect();
      });
  }

  private triggerPulseEffect() {
    this.showPulse = true;
    clearTimeout(this.eventTimer);
    this.eventTimer = setTimeout(() => {
      this.showPulse = false;
    }, 4000) as unknown as number;
  }

  private updateCompletedTaskCount() {
    const completedMetadataTasks = Object.values(this.latestTasks).length;
    const bookdropFileTaskCount = this.latestHasPendingFiles ? 1 : 0;
    this.completedTaskCount = completedMetadataTasks + bookdropFileTaskCount;
  }

  private updateTaskVisibility(tasks: Record<string, MetadataBatchProgressNotification>) {
    this.hasActiveOrCompletedTasks =
      this.progressHighlight || this.completedTaskCount > 0 || Object.keys(tasks).length > 0;
    this.updateTaskVisibilityWithBookdrop();
  }

  private updateTaskVisibilityWithBookdrop() {
    this.hasActiveOrCompletedTasks = this.hasActiveOrCompletedTasks || this.hasPendingBookdropFiles;
  }

  private initializeStatsMenu() {
    const user = this.user();
    const actions: MenuItem[] = [];

    if (user?.permissions?.canAccessLibraryStats || user?.permissions?.admin) {
      actions.push({
        label: this.translocoService.translate('layout.topbar.libraryStats'),
        icon: 'pi pi-chart-line',
        command: () => this.navigateToStats()
      });
    }

    if (user?.permissions?.canAccessUserStats || user?.permissions?.admin) {
      actions.push({
        label: this.translocoService.translate('layout.topbar.readingStats'),
        icon: 'pi pi-users',
        command: () => this.navigateToUserStats()
      });
    }

    this.statsMenuItems = actions;
  }

  get hasStatsAccess(): boolean {
    return this.statsMenuItems.length > 0;
  }

  get shouldShowStatsMenu(): boolean {
    return this.statsMenuItems.length > 1;
  }

  get statsTooltip(): string {
    if (this.statsMenuItems.length === 0) {
      return this.translocoService.translate('layout.topbar.stats');
    }
    if (this.statsMenuItems.length === 1) {
      return this.statsMenuItems[0].label || this.translocoService.translate('layout.topbar.stats');
    }
    return this.translocoService.translate('layout.topbar.stats');
  }

  get iconClass(): string {
    if (this.progressHighlight) return 'pi-spinner spin';
    if (this.iconPulsating) return 'pi-wave-pulse';
    if (this.completedTaskCount > 0 || this.hasPendingBookdropFiles) return 'pi-bell';
    return 'pi-wave-pulse';
  }

  get iconColor(): string {
    if (this.progressHighlight) return 'gold';
    if (this.showPulse) {
      switch (this.latestNotificationSeverity) {
        case Severity.ERROR:
          return 'crimson';
        case Severity.INFO:
          return 'aqua';
        case Severity.WARN:
          return 'orange';
        default:
          return 'orange';
      }
    }
    if (this.completedTaskCount > 0 || this.hasPendingBookdropFiles)
      return 'limegreen';
    return 'inherit';
  }

  get iconPulsating(): boolean {
    return !this.progressHighlight && (this.showPulse);
  }

  get shouldShowNotificationBadge(): boolean {
    return (
      (this.completedTaskCount > 0 || this.hasPendingBookdropFiles) &&
      !this.progressHighlight &&
      !this.showPulse
    );
  }

  private buildLanguageActions(activeLang: string): MenuItem[] {
    return AVAILABLE_LANGS.map((lang) => ({
      label: LANG_LABELS[lang] || lang,
      icon: lang === activeLang ? 'pi pi-check' : undefined,
      command: () => this.switchLanguage(lang),
    }));
  }
}
