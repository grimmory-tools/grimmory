import {ChangeDetectionStrategy, Component, computed, effect, ElementRef, inject, viewChild} from '@angular/core';
import {Router} from '@angular/router';
import {translateSignal, TranslocoPipe} from '@jsverse/transloco';
import {LucideEllipsis, LucidePlus} from '@lucide/angular';

import {BookMenuComponent} from '../../../book/components/book-menu/book-menu.component';
import {LibraryService} from '../../../book/service/library.service';
import {UserService} from '../../../settings/user-management/user.service';
import {BrowseCardSizeMenuItemsComponent} from '../../../../shared/browse/card-size-menu-items.component';
import {ArtworkRevealGroupDirective} from '../../../../shared/components/cover/artwork-reveal-group.directive';
import {AppPageHeaderComponent} from '../../../../shared/layout/page-header/app.page-header.component';
import {type PageHeader} from '../../../../shared/layout/page-header/page-header.service';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {CoverScalePreferenceService} from '../../../../shared/service/cover-scale-preference.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {AppButtonComponent} from '../../../../shared/ui/button/app-button.component';
import {AppMenuComponent} from '../../../../shared/ui/menu/app-menu.component';
import {AppMenuItemComponent} from '../../../../shared/ui/menu/app-menu-item.component';
import {AppMenuSeparatorComponent} from '../../../../shared/ui/menu/app-menu-separator.component';
import {AppMenuTriggerDirective} from '../../../../shared/ui/menu/app-menu-trigger.directive';
import {type GridDensityDirection} from '../../../../shared/util/grid-density.util';
import {scaleForGridColumns} from '../../../../shared/util/virtual-grid.util';
import {dashboardRows} from '../../dashboard-rows';
import {BOOK_ROW_CARD_GAP, BOOK_ROW_CARD_WIDTH} from '../../../book/components/book-row/book-row.component';
import {DashboardRowListComponent} from '../dashboard-row-list/dashboard-row-list.component';

@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppButtonComponent,
    AppMenuComponent,
    AppMenuItemComponent,
    AppMenuSeparatorComponent,
    AppMenuTriggerDirective,
    AppPageHeaderComponent,
    ArtworkRevealGroupDirective,
    BookMenuComponent,
    BrowseCardSizeMenuItemsComponent,
    DashboardRowListComponent,
    LucideEllipsis,
    LucidePlus,
    TranslocoPipe,
  ],
  template: `
    <div appArtworkRevealGroup class="app-page">
      <app-page-header [pageHeader]="pageHeader()">
        @if (desktop()) {
          <ng-container>
            <app-button
              variant="ghost"
              iconOnly
              [ariaLabel]="'browse.moreActions' | transloco"
              [appMenuTriggerFor]="moreMenu">
              <svg lucideEllipsis aria-hidden="true"></svg>
            </app-button>
            <app-menu #moreMenu [ariaLabel]="'browse.moreActions' | transloco">
              <app-browse-card-size-menu-items
                [smallerDisabled]="coverScale.scaleFactor() <= coverScale.MIN_SCALE"
                [largerDisabled]="coverScale.scaleFactor() >= coverScale.MAX_SCALE"
                (sizeChange)="changeCardSize($event)" />
              <app-menu-separator />
              <app-menu-item value="customise" (selected)="customise()">
                {{ 'dashboard.main.customize' | transloco }}
              </app-menu-item>
            </app-menu>
          </ng-container>
        }
      </app-page-header>

      <app-book-menu #bookMenu openInNewTab />

      @if (librariesEmpty()) {
        @if (canManageLibraries()) {
          <div class="mx-auto flex max-w-md flex-col items-center gap-2 py-24 text-center">
            <h2 class="m-0 text-xl font-semibold text-text-strong">{{ 'dashboard.main.welcomeTitle' | transloco }}</h2>
            <p class="m-0 text-sm leading-relaxed text-text-secondary">
              {{ 'dashboard.main.welcomeDescription' | transloco }}
            </p>
            <app-button
              class="mt-4"
              tone="primary"
              variant="solid"
              [label]="'dashboard.main.createLibrary' | transloco"
              (clicked)="createLibrary()">
              <svg lucidePlus aria-hidden="true"></svg>
            </app-button>
          </div>
        }
      } @else if (desktop()) {
        <app-dashboard-row-list [rows]="rows()" [bookMenu]="bookMenu" />
      } @else {
        <app-dashboard-row-list [rows]="rows()" [bookMenu]="bookMenu" />
      }
    </div>
  `,
})
export class DashboardPageComponent {
  private readonly libraryService = inject(LibraryService);
  private readonly userService = inject(UserService);
  private readonly dialogLauncher = inject(DialogLauncherService);
  private readonly pageTitle = inject(PageTitleService);
  private readonly router = inject(Router);
  protected readonly coverScale = inject(CoverScalePreferenceService);
  private readonly rowList = viewChild<DashboardRowListComponent, ElementRef<HTMLElement>>(DashboardRowListComponent, {read: ElementRef});

  protected readonly desktop = inject(LayoutService).isDesktop;
  private readonly title = translateSignal('dashboard.main.pageTitle');
  protected readonly pageHeader = computed<PageHeader>(() => ({title: this.title()}));

  protected readonly librariesEmpty = computed(() =>
    !this.libraryService.isLibrariesLoading() && this.libraryService.libraries().length === 0);
  protected readonly canManageLibraries = computed(() => {
    const permissions = this.userService.currentUser()?.permissions;
    return permissions?.admin || permissions?.canManageLibrary;
  });

  protected readonly rows = computed(() => dashboardRows(this.userService.currentUser()?.userSettings.dashboardConfig));

  constructor() {
    effect(() => this.pageTitle.setPageTitle(this.title()));
  }

  protected changeCardSize(direction: GridDensityDirection): void {
    const width = this.rowList()?.nativeElement.clientWidth;
    if (!width) {
      return;
    }
    const cardWidth = BOOK_ROW_CARD_WIDTH * this.coverScale.scaleFactor();
    const visible = Math.max(1, Math.floor((width + BOOK_ROW_CARD_GAP) / (cardWidth + BOOK_ROW_CARD_GAP)));
    const target = Math.max(1, visible + (direction === 'smaller' ? 1 : -1));
    this.coverScale.setScale(scaleForGridColumns(width, BOOK_ROW_CARD_GAP, target, BOOK_ROW_CARD_WIDTH,
      this.coverScale.MIN_SCALE, this.coverScale.MAX_SCALE));
  }

  protected customise(): void {
    void this.router.navigate(['/settings'], {queryParams: {tab: 'view'}});
  }

  protected createLibrary(): void {
    void this.dialogLauncher.openLibraryCreateDialog().catch(() => undefined);
  }
}
