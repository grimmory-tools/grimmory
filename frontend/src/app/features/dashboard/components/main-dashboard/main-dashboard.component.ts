import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {Button} from '@openng/optimus-ui/button';
import {DashboardScrollerComponent} from '../dashboard-scroller/dashboard-scroller.component';
import {UserService} from '../../../settings/user-management/user.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {DashboardConfigService} from '../../services/dashboard-config.service';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {LibraryService} from '../../../book/service/library.service';

@Component({
  selector: 'app-main-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './main-dashboard.component.html',
  styleUrls: ['./main-dashboard.component.scss'],
  imports: [
    Button,
    DashboardScrollerComponent,
    TranslocoDirective
  ],
  standalone: true
})
export class MainDashboardComponent {

  private readonly libraryService = inject(LibraryService);
  private readonly dialogLauncher = inject(DialogLauncherService);
  protected readonly userService = inject(UserService);
  private readonly dashboardConfigService = inject(DashboardConfigService);
  private readonly pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);

  readonly isLibrariesEmpty = computed(() =>
    !this.libraryService.isLibrariesLoading() && this.libraryService.libraries().length === 0
  );

  protected readonly enabledScrollers = computed(() =>
    this.dashboardConfigService.config().scrollers.filter(scroller => scroller.enabled));

  constructor() {
    this.pageTitle.setPageTitle(this.t.translate('dashboard.main.pageTitle'));
  }

  createNewLibrary() {
    void this.dialogLauncher.openLibraryCreateDialog().catch(() => undefined);
  }
}
