import {ChangeDetectorRef, Component, EventEmitter, inject, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {NgClass} from '@angular/common';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {Tooltip} from 'primeng/tooltip';
import {CheckboxChangeEvent, CheckboxModule} from 'primeng/checkbox';
import {FormsModule} from '@angular/forms';
import {TieredMenu} from 'primeng/tieredmenu';
import {Button} from 'primeng/button';
import {MenuItem, MessageService} from 'primeng/api';
import {AuthorSummary} from '../../model/author.model';
import {AuthorService} from '../../service/author.service';

@Component({
  selector: 'app-author-card',
  standalone: true,
  templateUrl: './author-card.component.html',
  styleUrls: ['./author-card.component.scss'],
  imports: [NgClass, TranslocoPipe, Tooltip, CheckboxModule, FormsModule, TieredMenu, Button]
})
export class AuthorCardComponent implements OnChanges {

  @Input({required: true}) author!: AuthorSummary;
  @Input() canQuickMatch = false;
  @Input() canDelete = false;
  @Input() isCheckboxEnabled = false;
  @Input() isSelected = false;
  @Output() cardClick = new EventEmitter<AuthorSummary>();
  @Output() quickMatched = new EventEmitter<AuthorSummary>();
  @Output() checkboxClick = new EventEmitter<{ index: number; author: AuthorSummary; selected: boolean; shiftKey: boolean }>();
  @Output() viewAuthor = new EventEmitter<AuthorSummary>();
  @Output() editAuthor = new EventEmitter<AuthorSummary>();
  @Output() deleteAuthor = new EventEmitter<AuthorSummary>();
  @Output() menuToggled = new EventEmitter<boolean>();
  @Input() index = 0;
  @Input() cacheBuster = 0;

  private authorService = inject(AuthorService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);
  private cdr = inject(ChangeDetectorRef);
  private lastShiftKey = false;

  hasPhoto = false;
  isImageLoaded = false;
  quickMatching = false;

  items: MenuItem[] = [];
  private menuInitialized = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['author']) {
      const prev = changes['author'].previousValue as AuthorSummary | undefined;
      const curr = changes['author'].currentValue as AuthorSummary;
      if (!prev || prev.id !== curr.id || prev.hasPhoto !== curr.hasPhoto || prev.asin !== curr.asin) {
        this.hasPhoto = curr.hasPhoto;
        this.isImageLoaded = false;
      }
    }
    if (changes['cacheBuster'] && !changes['cacheBuster'].firstChange) {
      this.hasPhoto = true;
      this.isImageLoaded = false;
    }
  }

  get thumbnailUrl(): string {
    return this.authorService.getAuthorThumbnailUrl(this.author.id, this.cacheBuster || undefined);
  }

  get isMatched(): boolean {
    return !!this.author.asin;
  }

  onCardClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (target.closest('.menu-button-container')) {
      return;
    }
    if (event.ctrlKey || event.metaKey) {
      event.preventDefault();
      event.stopPropagation();
      this.toggleCardSelection(!this.isSelected);
      return;
    }
    event.stopPropagation();
    this.cardClick.emit(this.author);
  }

  onImageLoad(): void {
    this.isImageLoaded = true;
  }

  onPhotoError(): void {
    this.hasPhoto = false;
  }

  captureMouseEvent(event: MouseEvent): void {
    event.stopPropagation();
    this.lastShiftKey = event.shiftKey;
  }

  toggleSelection(event: CheckboxChangeEvent): void {
    this.checkboxClick.emit({index: this.index, author: this.author, selected: event.checked, shiftKey: this.lastShiftKey});
  }

  toggleCardSelection(selected: boolean): void {
    this.checkboxClick.emit({index: this.index, author: this.author, selected, shiftKey: false});
  }

  onMenuToggle(event: Event, menu: TieredMenu): void {
    if (!this.menuInitialized) {
      this.menuInitialized = true;
      this.initMenu();
      this.cdr.markForCheck();
    }
    menu.toggle(event);
  }

  onMenuShow(): void {
    this.menuToggled.emit(true);
  }

  onMenuHide(): void {
    this.menuToggled.emit(false);
  }

  private initMenu(): void {
    this.items = [
      {
        label: this.t.translate('authorBrowser.card.menu.viewAuthor'),
        icon: 'pi pi-user',
        command: () => this.viewAuthor.emit(this.author)
      },
      ...(this.canQuickMatch ? [
        {
          label: this.t.translate('authorBrowser.card.menu.editAuthor'),
          icon: 'pi pi-pencil',
          command: () => this.editAuthor.emit(this.author)
        },
        {
          label: this.t.translate('authorBrowser.card.menu.quickMatch'),
          icon: this.quickMatching ? 'pi pi-spin pi-spinner' : 'pi pi-bolt',
          disabled: this.quickMatching,
          command: () => this.onQuickMatch()
        }
      ] : []),
      ...(this.canDelete ? [
        {
          label: this.t.translate('authorBrowser.card.menu.deleteAuthor'),
          icon: 'pi pi-trash',
          command: () => this.deleteAuthor.emit(this.author)
        }
      ] : [])
    ];
  }

  private onQuickMatch(): void {
    if (this.quickMatching) return;

    this.quickMatching = true;
    this.authorService.quickMatchAuthor(this.author.id).subscribe({
      next: (updated) => {
        this.quickMatching = false;
        this.author = {...this.author, asin: updated.asin, hasPhoto: true};
        this.hasPhoto = true;
        this.isImageLoaded = false;
        this.menuInitialized = false;
        this.quickMatched.emit(this.author);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.toast.quickMatchSuccessSummary'),
          detail: this.t.translate('authorBrowser.toast.quickMatchSuccessDetail')
        });
      },
      error: () => {
        this.quickMatching = false;
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.toast.quickMatchFailedSummary'),
          detail: this.t.translate('authorBrowser.toast.quickMatchFailedDetail')
        });
      }
    });
  }
}
