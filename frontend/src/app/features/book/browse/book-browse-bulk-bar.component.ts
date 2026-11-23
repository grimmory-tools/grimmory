import {Component, computed, inject, input, viewChild} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {injectQuery} from '@tanstack/angular-query';
import {LucideBookmark, LucideCheck, LucideDatabase, LucidePenLine} from '@lucide/angular';

import {
  bookBulkActionPermissions,
} from '../data/book-actions';
import {
  BOOK_READ_STATUS_LABEL_KEYS,
  BOOK_READ_STATUS_TARGETS,
  CLEAR_BOOK_READ_STATUS,
  type BookReadStatusTarget,
} from '../data/book-values';
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
import {ShelfQueryService} from '../../shelf/data/shelf-query.service';
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
  private readonly userService = inject(UserService);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly shelfQuery = inject(ShelfQueryService);
  readonly selection = input.required<BrowseSelection>();
  readonly books = input.required<readonly BookSummary[]>();
  readonly total = input.required<number | null>();
  readonly fetchIds = input.required<() => Promise<readonly number[]>>();
  protected readonly commands = createBookBrowseBulkCommands({
    selection: this.selection,
    books: this.books,
    fetchIds: this.fetchIds,
  });

  private readonly shelvesQuery = injectQuery(() => this.shelfQuery.shelves());
  private readonly pendingShelfMembership = injectPendingBookShelfMembership();
  private readonly pendingDeletions = injectPendingBookDeletions();

  private readonly bulkBar = viewChild(BrowseBulkActionsBarComponent);
  protected readonly overflowed = computed(() => this.bulkBar()?.overflowed() ?? NO_OVERFLOW);
  protected readonly isResolving = this.commands.isResolving;
  protected readonly readStatusTargets = BOOK_READ_STATUS_TARGETS;
  protected readonly clearReadStatus = CLEAR_BOOK_READ_STATUS;
  protected readonly clearReadStatusLabelKey = BOOK_READ_STATUS_LABEL_KEYS[CLEAR_BOOK_READ_STATUS];
  protected readonly autoSeparatorClass =
    'first:hidden [app-menu-separator+&]:hidden [&:not(:has(~:not(app-menu-separator)))]:hidden';

  protected readonly bulkPermissions = computed(() => {
    const permissions = bookBulkActionPermissions(this.userService.currentUser()?.permissions);
    return {
      ...permissions,
      canOrganizeFiles: permissions.canOrganizeFiles
        && this.appSettingsService.appSettings()?.diskType === 'LOCAL',
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
    return (this.shelvesQuery.data() ?? [])
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
    const permissions = this.bulkPermissions();
    return permissions.canResetGrimmoryProgress
      || permissions.canResetKoreaderProgress
      || permissions.canOrganizeFiles
      || permissions.canAttachFiles;
  });
  protected readonly bulkDeleting = computed(() => this.pendingDeletions().size > 0);

  protected statusLabelKey(status: BookReadStatusTarget): string {
    return BOOK_READ_STATUS_LABEL_KEYS[status];
  }
}
