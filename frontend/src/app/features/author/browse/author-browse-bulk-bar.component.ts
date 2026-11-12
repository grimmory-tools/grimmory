import {Component, computed, inject, input, viewChild} from '@angular/core';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {LucideZap} from '@lucide/angular';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {injectMutation} from '@tanstack/angular-query-experimental';

import {
  BrowseBulkActionsBarComponent,
  BrowseBulkActionsDividerComponent,
  BrowseBulkActionsItemDirective,
} from '../../../shared/browse/bulk-actions/bulk-actions-bar.component';
import {injectSelectedIds} from '../../../shared/browse/selected-ids';
import {type BrowseSelection} from '../../../shared/browse/selection';
import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {AppMenuItemComponent} from '../../../shared/ui/menu/app-menu-item.component';
import {AppMenuComponent} from '../../../shared/ui/menu/app-menu.component';
import {bookActionPermissions} from '../../book/data/book-actions';
import {UserService} from '../../settings/user-management/user.service';
import {AuthorCommandService} from '../data/author-command.service';

const NO_OVERFLOW: ReadonlySet<string> = new Set();

@Component({
  selector: 'app-author-browse-bulk-bar',
  host: {class: 'contents'},
  imports: [
    TranslocoPipe,
    LucideZap,
    AppButtonComponent,
    AppMenuComponent,
    AppMenuItemComponent,
    BrowseBulkActionsBarComponent,
    BrowseBulkActionsDividerComponent,
    BrowseBulkActionsItemDirective,
  ],
  template: `
    @if (selection().active()) {
      <app-browse-bulk-actions-bar
        [count]="selection().count()"
        [total]="total()"
        [moreMenu]="bulkMoreMenu"
        [moreDisabled]="isResolving()"
        (clearSelection)="selection().clear()"
        (selectAll)="selection().selectAll()">
        @if (permissions().canEditMetadata) {
          <app-button
            appBrowseBulkActionsItem="autoMatch"
            variant="ghost"
            size="md"
            [label]="'author.bulk.autoMatch' | transloco"
            [loading]="autoMatchMutation.isPending()"
            [disabled]="isResolving()"
            (clicked)="autoMatch()">
            <svg lucideZap aria-hidden="true"></svg>
          </app-button>
        }
        @if (permissions().canDeleteBook) {
          <span appBrowseBulkActionsItem="delete" appBrowseBulkActionsTrailing class="flex items-center gap-1">
            <app-browse-bulk-actions-divider />
            <app-button
              variant="ghost"
              tone="danger"
              size="md"
              [label]="'common.delete' | transloco"
              [loading]="deleteMutation.isPending()"
              [disabled]="isResolving()"
              (clicked)="delete()" />
          </span>
        }
      </app-browse-bulk-actions-bar>

      <app-menu #bulkMoreMenu [ariaLabel]="'browse.moreActions' | transloco">
        @if (overflowed().has('autoMatch')) {
          <app-menu-item value="auto-match" [loading]="autoMatchMutation.isPending()" [disabled]="isResolving()" (selected)="autoMatch()">
            {{ 'author.bulk.autoMatch' | transloco }}
          </app-menu-item>
        }
        @if (overflowed().has('delete')) {
          <app-menu-item value="delete" variant="destructive" [loading]="deleteMutation.isPending()" [disabled]="isResolving()" (selected)="delete()">
            {{ 'common.delete' | transloco }}
          </app-menu-item>
        }
      </app-menu>
    }
  `,
})
export class AuthorBrowseBulkBarComponent {
  private readonly userService = inject(UserService);
  private readonly transloco = inject(TranslocoService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly authorCommands = inject(AuthorCommandService);

  readonly selection = input.required<BrowseSelection>();
  readonly total = input.required<number | null>();
  readonly fetchIds = input.required<() => Promise<readonly number[]>>();

  private readonly selectedIds = injectSelectedIds(this.selection, this.fetchIds);
  private readonly bulkBar = viewChild(BrowseBulkActionsBarComponent);

  protected readonly autoMatchMutation = injectMutation(() => this.authorCommands.autoMatch());
  protected readonly deleteMutation = injectMutation(() => this.authorCommands.deleteAuthors());
  protected readonly isResolving = this.selectedIds.isResolving;
  protected readonly overflowed = computed(() => this.bulkBar()?.overflowed() ?? NO_OVERFLOW);
  protected readonly permissions = computed(() =>
    bookActionPermissions(this.userService.currentUser()?.permissions));

  protected autoMatch(): void {
    void this.selectedIds.withSelectedIds(authorIds =>
      this.autoMatchMutation.mutate({authorIds}, {
        onSuccess: matched => this.messageService.add({
          severity: 'success',
          summary: this.transloco.translate('author.bulk.autoMatchResult', {
            matched: matched.length.toLocaleString(),
            total: authorIds.length.toLocaleString(),
          }),
        }),
        onError: () => this.messageService.add({
          severity: 'error',
          summary: this.transloco.translate('author.bulk.autoMatchFailed'),
        }),
      }));
  }

  protected delete(): void {
    this.confirmationService.confirm({
      message: this.transloco.translate('author.bulk.deleteMessage', {count: this.selection().count().toLocaleString()}),
      header: this.transloco.translate('author.bulk.deleteHeader'),
      acceptLabel: this.transloco.translate('common.delete'),
      rejectLabel: this.transloco.translate('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-outlined',
      accept: () => {
        void this.selectedIds.withSelectedIds(authorIds =>
          this.deleteMutation.mutate({authorIds}, {
            onSuccess: () => this.selection().pruneDeleted(authorIds),
          }));
      },
    });
  }
}
