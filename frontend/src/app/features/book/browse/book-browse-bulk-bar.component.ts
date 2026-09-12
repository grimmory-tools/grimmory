import {Component, computed, inject, input, viewChild} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {LucideBookmark, LucideCheck, LucideDatabase, LucidePenLine} from '@lucide/angular';

import {
  bookActionPermissions,
} from '../data/book-actions';
import {
  BOOK_READ_STATUS_LABEL_KEYS,
  BOOK_READ_STATUS_TARGETS,
  CLEAR_BOOK_READ_STATUS,
  CLEAR_BOOK_READ_STATUS_LABEL_KEY,
  type BookReadStatusTarget,
} from '../components/book-read-status-options';
import {
  BrowseBulkActionsBarComponent,
  BrowseBulkActionsDividerComponent,
  BrowseBulkActionsItemDirective,
} from '../../../shared/browse/bulk-actions/bulk-actions-bar.component';
import {ShelfMembershipMenuComponent} from '../../../shared/components/shelf-menu/shelf-membership-menu.component';
import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {AppMenuComponent} from '../../../shared/ui/menu/app-menu.component';
import {AppMenuItemComponent} from '../../../shared/ui/menu/app-menu-item.component';
import {AppMenuSeparatorComponent} from '../../../shared/ui/menu/app-menu-separator.component';
import {AppMenuTriggerDirective} from '../../../shared/ui/menu/app-menu-trigger.directive';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {
  injectPendingBookDeletions,
  injectPendingBookShelfMembership,
  overlayShelfIds,
} from '../data/book-command-pending-state';
import {type BookSummary} from '../data/book-response.models';
import {ShelfDefinitionQueryService} from '../data/shelf-definition-query.service';
import {UserService} from '../../settings/user-management/user.service';
import {type BrowseSelection} from '../../../shared/browse/selection';
import {createBookBrowseBulkCommands} from './book-browse-bulk-commands';

const NO_OVERFLOW: ReadonlySet<string> = new Set();

@Component({
  selector: 'app-book-browse-bulk-bar',
  host: {class: 'contents'},
  imports: [
    TranslocoPipe,
    AppButtonComponent,
    AppMenuComponent,
    AppMenuItemComponent,
    AppMenuSeparatorComponent,
    AppMenuTriggerDirective,
    BrowseBulkActionsBarComponent,
    BrowseBulkActionsDividerComponent,
    BrowseBulkActionsItemDirective,
    ShelfMembershipMenuComponent,
    LucideBookmark,
    LucideCheck,
    LucideDatabase,
    LucidePenLine,
  ],
  templateUrl: './book-browse-bulk-bar.component.html',
})
export class BookBrowseBulkBarComponent {
  readonly selection = input.required<BrowseSelection>();
  readonly books = input.required<readonly BookSummary[]>();
  readonly total = input.required<number | null>();
  readonly fetchIds = input.required<() => Promise<readonly number[]>>();

  private readonly userService = inject(UserService);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly shelfDefinitionQuery = inject(ShelfDefinitionQueryService);
  protected readonly commands = createBookBrowseBulkCommands({
    selection: this.selection,
    books: this.books,
    fetchIds: this.fetchIds,
  });

  private readonly shelfDefinitionsQuery = injectQuery(() => this.shelfDefinitionQuery.definitions());
  private readonly pendingShelfMembership = injectPendingBookShelfMembership();
  private readonly pendingDeletions = injectPendingBookDeletions();

  private readonly bulkBar = viewChild(BrowseBulkActionsBarComponent);
  protected readonly overflowed = computed(() => this.bulkBar()?.overflowed() ?? NO_OVERFLOW);
  protected readonly isResolving = this.commands.isResolving;
  protected readonly readStatusTargets = BOOK_READ_STATUS_TARGETS;
  protected readonly clearReadStatus = CLEAR_BOOK_READ_STATUS;
  protected readonly clearReadStatusLabelKey = CLEAR_BOOK_READ_STATUS_LABEL_KEY;
  protected readonly autoSeparatorClass =
    'first:hidden [app-menu-separator+&]:hidden [&:not(:has(~:not(app-menu-separator)))]:hidden';

  protected readonly actionPermissions = computed(() =>
    bookActionPermissions(this.userService.currentUser()?.permissions));
  protected readonly bulkPermissions = computed(() => {
    const permissions = this.userService.currentUser()?.permissions;
    const admin = !!permissions?.admin;
    return {
      canAutoFetchMetadata: admin || !!permissions?.canBulkAutoFetchMetadata,
      canCustomFetchMetadata: admin || !!permissions?.canBulkCustomFetchMetadata,
      canEditMetadata: admin || !!permissions?.canBulkEditMetadata,
      canRegenerateCover: admin || !!permissions?.canBulkRegenerateCover,
      canLockUnlockMetadata: admin || !!permissions?.canBulkLockUnlockMetadata,
      canOrganizeFiles: this.actionPermissions().canOrganizeFiles
        && this.appSettingsService.appSettings()?.diskType === 'LOCAL',
      canAttachFiles: admin || !!permissions?.canManageLibrary,
    };
  });

  private readonly selectedLoadedBooks = computed(() => {
    const selection = this.selection();
    return this.books().filter(book => selection.isSelected(book.id));
  });
  private readonly allSelectedLoaded = computed(() =>
    this.selectedLoadedBooks().length === this.selection().count());

  protected readonly bulkShelves = computed(() => {
    const selectedBooks = this.selectedLoadedBooks();
    const allLoaded = this.allSelectedLoaded();
    const currentUserId = this.userService.currentUser()?.id;
    const pendingShelfMembership = this.pendingShelfMembership();
    const shelfIdsByBook = selectedBooks.map(book =>
      overlayShelfIds(book, pendingShelfMembership.get(book.id)),
    );
    return (this.shelfDefinitionsQuery.data() ?? [])
      .filter(shelf => shelf.userId === currentUserId)
      .map(shelf => {
        let onCount = 0;
        for (const shelfIds of shelfIdsByBook) {
          if (shelfIds.has(shelf.id)) {
            onCount++;
          }
        }
        const checked = allLoaded && selectedBooks.length > 0 && onCount === selectedBooks.length;
        return {
          id: shelf.id,
          name: shelf.name,
          checked,
          mixed: !checked && (onCount > 0 || !allLoaded),
        };
      });
  });
  protected readonly bulkShelfIds = computed(() => this.bulkShelves().map(shelf => shelf.id));

  protected readonly bulkMetadataAvailable = computed(() => {
    const permissions = this.bulkPermissions();
    return permissions.canAutoFetchMetadata
      || permissions.canCustomFetchMetadata
      || permissions.canRegenerateCover
      || permissions.canLockUnlockMetadata;
  });
  protected readonly bulkAttachEligible = computed(() =>
    this.allSelectedLoaded()
      && new Set(this.selectedLoadedBooks().map(book => book.libraryId)).size === 1,
  );
  protected readonly moreMenuOnlyItems = computed(() => {
    const permissions = this.actionPermissions();
    return permissions.canResetGrimmoryProgress
      || permissions.canResetKoreaderProgress
      || this.bulkPermissions().canOrganizeFiles
      || this.bulkPermissions().canAttachFiles;
  });
  protected readonly bulkDeleting = computed(() => this.pendingDeletions().size > 0);

  protected statusLabelKey(status: BookReadStatusTarget): string {
    return BOOK_READ_STATUS_LABEL_KEYS[status];
  }
}
