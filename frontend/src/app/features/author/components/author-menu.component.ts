import {Component, computed, inject, output, signal, viewChild} from '@angular/core';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {LucideSquareArrowOutUpRight, type LucideIconData} from '@lucide/angular';
import {ConfirmationService} from '@openng/optimus-ui/api';
import {injectMutation} from '@tanstack/angular-query-experimental';

import {AppMenuItemComponent} from '../../../shared/ui/menu/app-menu-item.component';
import {AppMenuSeparatorComponent} from '../../../shared/ui/menu/app-menu-separator.component';
import {AppMenuComponent, type ContextMenuRequest} from '../../../shared/ui/menu/app-menu.component';
import {bookActionPermissions} from '../../book/data/book-actions';
import {UserService} from '../../settings/user-management/user.service';
import {AuthorCommandService} from '../data/author-command.service';
import {type AuthorSummary} from '../data/author-response.models';
import {injectAuthorQuickMatch} from './author-quick-match';

@Component({
  selector: 'app-author-menu',
  host: {class: 'contents'},
  imports: [TranslocoPipe, AppMenuComponent, AppMenuItemComponent, AppMenuSeparatorComponent],
  template: `
    <app-menu [ariaLabel]="'author.menu.label' | transloco" (closed)="openMenuAuthorId.set(null)">
      @if (menuAuthor(); as author) {
        <app-menu-item value="open-details-new-tab" [link]="['/author', author.id]" target="_blank" [trailingIcon]="newTabIcon">
          {{ 'common.openInNewTab' | transloco }}
        </app-menu-item>
        @if (permissions().canEditMetadata) {
          <app-menu-separator />
          <app-menu-item value="quick-match" (selected)="quickMatch(author)">{{ 'author.menu.quickMatch' | transloco }}</app-menu-item>
          <app-menu-item value="edit" [link]="['/author', author.id]" [queryParams]="editTab">{{ 'common.edit' | transloco }}</app-menu-item>
        }
        @if (permissions().canDeleteBook) {
          <app-menu-separator />
          <app-menu-item value="delete" variant="destructive" (selected)="delete(author)">{{ 'common.delete' | transloco }}</app-menu-item>
        }
      }
    </app-menu>
  `,
})
export class AuthorMenuComponent {
  private readonly userService = inject(UserService);
  private readonly transloco = inject(TranslocoService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly authorCommands = inject(AuthorCommandService);

  readonly deleted = output<readonly number[]>();

  private readonly menu = viewChild.required(AppMenuComponent);
  private readonly authorQuickMatch = injectAuthorQuickMatch();
  private readonly deleteMutation = injectMutation(() => this.authorCommands.deleteAuthors());

  protected readonly menuAuthor = signal<AuthorSummary | null>(null);
  protected readonly openMenuAuthorId = signal<number | null>(null);
  readonly openAuthorId = this.openMenuAuthorId.asReadonly();
  protected readonly newTabIcon: LucideIconData = LucideSquareArrowOutUpRight.icon;
  protected readonly editTab = {tab: 'edit'};
  protected readonly permissions = computed(() =>
    bookActionPermissions(this.userService.currentUser()?.permissions));

  openFor(author: AuthorSummary, request: ContextMenuRequest): void {
    if (!request.contextmenu && this.openAuthorId() === author.id) {
      this.menu().close();
      return;
    }
    this.menuAuthor.set(author);
    this.openMenuAuthorId.set(author.id);
    this.menu().openFrom(request);
  }

  protected quickMatch(author: AuthorSummary): void {
    this.authorQuickMatch.run(author.id);
  }

  protected delete(author: AuthorSummary): void {
    this.confirmationService.confirm({
      message: this.transloco.translate('author.menu.deleteMessage', {name: author.name}),
      header: this.transloco.translate('author.bulk.deleteHeader'),
      acceptLabel: this.transloco.translate('common.delete'),
      rejectLabel: this.transloco.translate('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-outlined',
      accept: () => this.deleteMutation.mutate({authorIds: [author.id]}, {
        onSuccess: () => this.deleted.emit([author.id]),
      }),
    });
  }
}
