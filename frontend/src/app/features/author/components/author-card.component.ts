import {booleanAttribute, ChangeDetectionStrategy, Component, computed, inject, input, output} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideEllipsisVertical} from '@lucide/angular';

import {CardMenuDirective} from '../../../shared/components/cards/card-menu.directive';
import {CardSelectionComponent} from '../../../shared/components/cards/card-selection.component';
import {
  cardArtworkClass,
  CARD_META_LINE_CLASS,
  CARD_META_TITLE_CLASS,
  CARD_OVERLAY_BUTTON_CLASS,
  CARD_OVERLAY_REVEAL_CLASS,
} from '../../../shared/components/cards/card.layout';
import {CoverComponent} from '../../../shared/components/cover/cover.component';
import {UrlHelperService} from '../../../shared/service/url-helper.service';
import {cn} from '../../../shared/ui/cn';
import {contextMenuRequest, type ContextMenuRequest} from '../../../shared/ui/menu/app-menu.component';
import {isContextMenuGesture} from '../../../shared/util/pointer-gestures';
import {type AuthorSummary} from '../data/author-response.models';
import {AUTHOR_CARD_PHOTO_CLASS} from './author-card.layout';

export function authorCardPhotoSrc(author: AuthorSummary, urlHelper: UrlHelperService): string | null {
  return urlHelper.getAuthorThumbnailUrl(author.id, author.photoUpdatedOn);
}

@Component({
  selector: 'app-author-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslocoPipe, LucideEllipsisVertical, CardMenuDirective, CardSelectionComponent, CoverComponent],
  host: {class: 'block'},
  template: `
    <div
      class="group/card relative min-w-0 cursor-pointer text-center"
      [attr.data-active]="menuPinned() ? 'true' : null"
      appCardMenu
      [appCardMenuLongPress]="!selectionActive()"
      (cardMenuRequested)="menuRequested.emit($event)"
    >
      <div [class]="photoClass()">
        <app-cover
          class="absolute inset-0 h-full w-full overflow-hidden rounded-full"
          placeholder="initials"
          [src]="photoSrc()"
          [title]="author().name"
          [alt]="'author.card.photoAlt' | transloco: {name: author().name}"
        />
        @if (!selectionActive()) {
          <button
            type="button"
            [class]="menuButtonClass"
            aria-haspopup="menu"
            [attr.aria-expanded]="menuOpen()"
            [attr.aria-label]="'author.card.menu' | transloco: {name: author().name}"
            (click)="onMenu($event)"
          >
            <svg lucideEllipsisVertical class="size-3.5"></svg>
          </button>
        }
        @if (selectable()) {
          <app-card-selection
            [selected]="selected()"
            [selectionMode]="selectionActive()"
            [ariaLabel]="'author.card.select' | transloco: {name: author().name}"
            (selectionToggled)="toggleSelect.emit($event)"
          />
        }
      </div>

      <div class="px-0.5 pt-2">
        <div [class]="nameClass" [attr.title]="author().name">{{ author().name }}</div>
        <div [class]="countClass">
          {{ (author().bookCount === 1 ? 'author.card.book' : 'author.card.books') | transloco: {count: author().bookCount} }}
        </div>
      </div>

      @if (!selectionActive()) {
        <a [class]="linkClass" [routerLink]="['/author', author().id]" [attr.aria-label]="author().name"></a>
      } @else {
        <button
          type="button"
          [class]="linkClass"
          [attr.aria-pressed]="selected()"
          [attr.aria-label]="'author.card.select' | transloco: {name: author().name}"
          (click)="onSelect($event)"
        ></button>
      }
    </div>
  `,
})
export class AuthorCardComponent {
  private readonly urlHelper = inject(UrlHelperService);

  readonly author = input.required<AuthorSummary>();
  readonly selectable = input(false, {transform: booleanAttribute});
  readonly selected = input(false, {transform: booleanAttribute});
  readonly selectionActive = input(false, {transform: booleanAttribute});
  readonly menuOpen = input(false, {transform: booleanAttribute});

  readonly toggleSelect = output<{shiftKey: boolean}>();
  readonly menuRequested = output<ContextMenuRequest>();

  protected readonly menuPinned = computed(() => this.menuOpen() && !this.selectionActive());
  protected readonly photoSrc = computed(() => authorCardPhotoSrc(this.author(), this.urlHelper));

  protected readonly photoClass = computed(() => cn(cardArtworkClass(this.selected()), AUTHOR_CARD_PHOTO_CLASS));
  protected readonly menuButtonClass =
    cn(CARD_OVERLAY_BUTTON_CLASS, CARD_OVERLAY_REVEAL_CLASS, 'absolute right-[4%] bottom-[4%] z-20 size-7 rounded-full');
  protected readonly linkClass =
    'absolute inset-0 z-10 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary';
  protected readonly nameClass = CARD_META_TITLE_CLASS;
  protected readonly countClass = CARD_META_LINE_CLASS;

  protected onMenu(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.menuRequested.emit(contextMenuRequest(event));
  }

  protected onSelect(event: MouseEvent): void {
    if (isContextMenuGesture(event)) {
      event.preventDefault();
      return;
    }
    this.toggleSelect.emit({shiftKey: event.shiftKey});
  }
}
