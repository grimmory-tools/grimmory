import {Component, ElementRef, OnDestroy, ViewChild} from '@angular/core';
import {MenuItem} from 'primeng/api';
import {LayoutService} from '../layout-main/service/app.layout.service';
import {Router, RouterLink} from '@angular/router';
import {DialogService as PrimeDialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {LibraryCreatorComponent} from '../../../book/components/library-creator/library-creator.component';
import {TooltipModule} from 'primeng/tooltip';
import {FormsModule} from '@angular/forms';
import {InputTextModule} from 'primeng/inputtext';
import {BookSearcherComponent} from '../../../book/components/book-searcher/book-searcher.component';
import {AsyncPipe, NgClass, NgStyle} from '@angular/common';
import {NotificationEventService} from '../../../shared/websocket/notification-event.service';
import {Button} from 'primeng/button';
import {StyleClass} from 'primeng/styleclass';
import {Divider} from 'primeng/divider';
import {ThemeConfiguratorComponent} from '../theme-configurator/theme-configurator.component';
import {BookUploaderComponent} from '../../../utilities/component/book-uploader/book-uploader.component';
import {AuthService} from '../../../core/service/auth.service';
import {UserService} from '../../../settings/user-management/user.service';
import {UserProfileDialogComponent} from '../../../settings/global-preferences/user-profile-dialog/user-profile-dialog.component';
import {GithubSupportDialog} from '../../../utilities/component/github-support-dialog/github-support-dialog';
import {Popover} from 'primeng/popover';
import {MetadataProgressService} from '../../../core/service/metadata-progress-service';
import {takeUntil} from 'rxjs/operators';
import {Subject} from 'rxjs';
import {MetadataBatchProgressNotification} from '../../../core/model/metadata-batch-progress.model';
import {UnifiedNotificationBoxComponent} from '../../../core/component/unified-notification-popover-component/unified-notification-popover-component';
import {BookdropFileService} from '../../../bookdrop/bookdrop-file.service';

@Component({
  selector: 'app-topbar',
  templateUrl: './app.topbar.component.html',
  styleUrls: ['./app.topbar.component.scss'],
  standalone: true,
  imports: [
    RouterLink,
    TooltipModule,
    FormsModule,
    InputTextModule,
    BookSearcherComponent,
    Button,
    ThemeConfiguratorComponent,
    StyleClass,
    NgClass,
    Divider,
    AsyncPipe,
    Popover,
    UnifiedNotificationBoxComponent,
    NgStyle,
  ],
})
export class AppTopBarComponent implements OnDestroy {
  items!: MenuItem[];
  ref?: DynamicDialogRef;

  @ViewChild('menubutton') menuButton!: ElementRef;
  @ViewChild('topbarmenubutton') topbarMenuButton!: ElementRef;
  @ViewChild('topbarmenu') menu!: ElementRef;

  isMenuVisible = true;
  progressHighlight = false;
  completedTaskCount = 0;
  hasActiveOrCompletedTasks = false;
  showPulse = false;
  hasAnyTasks = false;
  hasPendingBookdropFiles = false;

  private eventTimer: any;
  private destroy$ = new Subject<void>();

  private latestTasks: { [taskId: string]: MetadataBatchProgressNotification } = {};
  private latestHasPendingFiles = false;

  constructor(
    public layoutService: LayoutService,
    public dialogService: PrimeDialogService,
    private notificationService: NotificationEventService,
    private router: Router,
    private authService: AuthService,
    protected userService: UserService,
    private metadataProgressService: MetadataProgressService,
    private bookdropFileService: BookdropFileService
  ) {
    this.subscribeToMetadataProgress();
    this.subscribeToNotifications();

    this.metadataProgressService.activeTasks$
      .pipe(takeUntil(this.destroy$))
      .subscribe((tasks) => {
        this.latestTasks = tasks;
        this.hasAnyTasks = Object.keys(tasks).length > 0;
        this.updateCompletedTaskCount();
        this.updateTaskVisibility(tasks);
      });

    this.bookdropFileService.hasPendingFiles$
      .pipe(takeUntil(this.destroy$))
      .subscribe((hasPending) => {
        this.latestHasPendingFiles = hasPending;
        this.hasPendingBookdropFiles = hasPending;
        this.updateCompletedTaskCount();
        this.updateTaskVisibilityWithBookdrop();
      });
  }

  ngOnDestroy(): void {
    if (this.ref) this.ref.close();
    clearTimeout(this.eventTimer);
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleMenu() {
    this.isMenuVisible = !this.isMenuVisible;
    this.layoutService.onMenuToggle();
  }

  openGithubSupportDialog(): void {
    this.openDialog(GithubSupportDialog, 'Support BookLore on GitHub', '25%');
  }

  openLibraryCreatorDialog(): void {
    this.openDialog(LibraryCreatorComponent, 'Create New Library', '15%');
  }

  openFileUploadDialog(): void {
    this.openDialog(BookUploaderComponent, 'Book Uploader', '10%');
  }

  openUserProfileDialog(): void {
    this.openDialog(UserProfileDialogComponent, 'User Profile Information', '10%');
  }

  navigateToSettings() {
    this.router.navigate(['/settings']);
  }

  logout() {
    this.authService.logout();
  }

  private openDialog(component: any, header: string, top: string) {
    const isMobile = window.innerWidth <= 768;
    this.ref = this.dialogService.open(component, {
      header,
      modal: true,
      closable: true,
      style: {
        position: 'absolute',
        top,
        ...(isMobile && {
          width: '90vw',
          maxWidth: '90vw',
          minWidth: '90vw',
        }),
      },
    });
  }

  private subscribeToMetadataProgress() {
    this.metadataProgressService.progressUpdates$
      .pipe(takeUntil(this.destroy$))
      .subscribe((progress) => {
        this.progressHighlight = progress.status === 'IN_PROGRESS';
      });
  }

  private subscribeToNotifications() {
    this.notificationService.latestNotification$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.triggerPulseEffect();
      });
  }

  private triggerPulseEffect() {
    this.showPulse = true;
    clearTimeout(this.eventTimer);
    this.eventTimer = setTimeout(() => {
      this.showPulse = false;
    }, 4000);
  }

  private updateCompletedTaskCount() {
    const completedMetadataTasks = Object.values(this.latestTasks).filter(task => task.status === 'COMPLETED').length;
    const bookdropFileTaskCount = this.latestHasPendingFiles ? 1 : 0;
    this.completedTaskCount = completedMetadataTasks + bookdropFileTaskCount;
  }

  private updateTaskVisibility(tasks: { [taskId: string]: MetadataBatchProgressNotification }) {
    this.hasActiveOrCompletedTasks =
      this.progressHighlight || this.completedTaskCount > 0 || Object.keys(tasks).length > 0;
    this.updateTaskVisibilityWithBookdrop();
  }

  private updateTaskVisibilityWithBookdrop() {
    this.hasActiveOrCompletedTasks = this.hasActiveOrCompletedTasks || this.hasPendingBookdropFiles;
  }

  get iconClass(): string {
    if (this.progressHighlight) return 'pi-spinner spin';
    if (this.iconPulsating) return 'pi-wave-pulse';
    if (this.completedTaskCount > 0 || this.hasPendingBookdropFiles) return 'pi-bell';
    return 'pi-wave-pulse';
  }

  get iconColor(): string {
    if (this.progressHighlight) return 'yellow';
    if (this.showPulse) return 'red';
    if (this.completedTaskCount > 0 || this.hasPendingBookdropFiles) return 'orange';
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
}
