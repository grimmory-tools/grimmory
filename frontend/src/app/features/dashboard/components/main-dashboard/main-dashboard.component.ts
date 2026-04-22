import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {Button} from 'primeng/button';
import {DashboardScrollerComponent} from '../dashboard-scroller/dashboard-scroller.component';
import {BookService} from '../../../book/service/book.service';
import {UserService} from '../../../settings/user-management/user.service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {DashboardConfigService} from '../../services/dashboard-config.service';
import {ScrollerConfig, ScrollerType} from '../../models/dashboard-config.model';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {LibraryService} from '../../../book/service/library.service';
import {BookCardOverlayPreferenceService} from '../../../book/components/book-browser/book-card-overlay-preference.service';
import {DashboardBookService} from '../../services/dashboard-book.service';
import {Book} from '../../../book/model/book.model';

@Component({
  selector: 'app-main-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './main-dashboard.component.html',
  styleUrls: ['./main-dashboard.component.scss'],
  imports: [
    Button,
    DashboardScrollerComponent,
    ProgressSpinner,
    Tooltip,
    TranslocoDirective
  ],
  standalone: true
})
export class MainDashboardComponent {

  private readonly bookService = inject(BookService);
  private readonly libraryService = inject(LibraryService);
  private readonly dialogLauncher = inject(DialogLauncherService);
  protected readonly userService = inject(UserService);
  private readonly dashboardConfigService = inject(DashboardConfigService);
  private readonly dashboardBookService = inject(DashboardBookService);
  private readonly pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);
  protected readonly overlayPreferenceService = inject(BookCardOverlayPreferenceService);

  readonly dashboardConfig = this.dashboardConfigService.config;
  readonly isBooksLoading = this.bookService.isBooksLoading;
  readonly isLibrariesEmpty = computed(() =>
    !this.libraryService.isLibrariesLoading() && this.libraryService.libraries().length === 0
  );

  readonly enabledScrollers = computed(() => {
    return this.dashboardConfig().scrollers.filter(s => s.enabled);
  });

  /**
   * Eager image count for the first dashboard scroller. Mobile shows far fewer
   * covers above the fold, so hinting `fetchpriority=high` on 8 covers there
   * fights the LCP image for bandwidth. We derive from the initial viewport
   * width (resize doesn't change LCP, so a one-shot read is sufficient).
   */
  private readonly eagerCountFirst = (() => {
    if (typeof window === 'undefined') return 8;
    const w = window.innerWidth;
    if (w < 480) return 3;
    if (w < 768) return 4;
    if (w < 1280) return 6;
    return 8;
  })();

  readonly getEagerCountForScroller = (index: number): number => {
    if (index === 0) return this.eagerCountFirst;
    if (index === 1) return Math.min(4, Math.max(2, Math.floor(this.eagerCountFirst / 2)));
    return 0;
  };

  ScrollerType = ScrollerType;

  constructor() {
    this.pageTitle.setPageTitle(this.t.translate('dashboard.main.pageTitle'));
  }

  getBooksForScroller(config: ScrollerConfig): Book[] {
    return this.dashboardBookService.scrollerBooksMap().get(config.id) ?? [];
  }

  openDashboardSettings(): void {
    this.dialogLauncher.openDashboardSettingsDialog();
  }

  createNewLibrary() {
    this.dialogLauncher.openLibraryCreateDialog();
  }
}
