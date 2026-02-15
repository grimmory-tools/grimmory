import {Component, inject, OnInit} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Book} from '../../model/book.model';
import {MessageService} from 'primeng/api';
import {ShelfService} from '../../service/shelf.service';
import {combineLatest, finalize, Observable} from 'rxjs';
import {BookService} from '../../service/book.service';
import {map, tap} from 'rxjs/operators';
import {Shelf} from '../../model/shelf.model';
import {ShelfState} from '../../model/state/shelf-state.model';
import {Button} from 'primeng/button';
import {AsyncPipe} from '@angular/common';
import {Checkbox} from 'primeng/checkbox';
import {FormsModule} from '@angular/forms';
import {BookDialogHelperService} from '../book-browser/book-dialog-helper.service';
import {LoadingService} from '../../../../core/services/loading.service';
import {UserService} from '../../../settings/user-management/user.service';
import {IconDisplayComponent} from '../../../../shared/components/icon-display/icon-display.component';
import {IconSelection} from '../../../../shared/service/icon-picker.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-shelf-assigner',
  standalone: true,
  templateUrl: './shelf-assigner.component.html',
  styleUrl: './shelf-assigner.component.scss',
  imports: [
    Button,
    Checkbox,
    AsyncPipe,
    FormsModule,
    IconDisplayComponent,
    TranslocoDirective
  ]
})
export class ShelfAssignerComponent implements OnInit {

  private shelfService = inject(ShelfService);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  private bookDialogHelper = inject(BookDialogHelperService);
  private loadingService = inject(LoadingService);
  private userService = inject(UserService);
  private readonly t = inject(TranslocoService);

  shelfState$: Observable<ShelfState> = combineLatest([
    this.shelfService.shelfState$,
    this.userService.userState$
  ]).pipe(
    map(([state, userState]) => ({
      ...state,
      shelves: state.shelves?.filter(s => s.userId === userState.user?.id) || []
    }))
  );

  book: Book = this.dynamicDialogConfig.data.book;
  selectedShelves: Shelf[] = [];
  bookIds: Set<number> = this.dynamicDialogConfig.data.bookIds;
  isMultiBooks: boolean = this.dynamicDialogConfig.data.isMultiBooks;

  ngOnInit(): void {
    if (!this.isMultiBooks && this.book.shelves) {
      this.shelfState$.pipe(
        map(state => state.shelves || []),
        tap(shelves => {
          this.selectedShelves = shelves.filter(shelf =>
            this.book.shelves?.some(bShelf => bShelf.id === shelf.id)
          );
        })
      ).subscribe();
    }
  }

  updateBooksShelves(): void {
    const idsToAssign = new Set<number | undefined>(this.selectedShelves.map(shelf => shelf.id));
    const idsToUnassign: Set<number> = this.isMultiBooks ? new Set() : this.getIdsToUnAssign(this.book, idsToAssign);
    const bookIds = this.isMultiBooks ? this.bookIds : new Set([this.book.id]);
    this.updateBookShelves(bookIds, idsToAssign, idsToUnassign);
  }

  private updateBookShelves(bookIds: Set<number>, idsToAssign: Set<number | undefined>, idsToUnassign: Set<number>): void {
    const loader = this.loadingService.show(this.t.translate('book.shelfAssigner.loading.updatingShelves', { count: bookIds.size }));

    this.bookService.updateBookShelves(bookIds, idsToAssign, idsToUnassign)
      .pipe(finalize(() => this.loadingService.hide(loader)))
      .subscribe({
        next: () => {
          this.messageService.add({severity: 'info', summary: this.t.translate('common.success'), detail: this.t.translate('book.shelfAssigner.toast.updateSuccessDetail')});
          this.dynamicDialogRef.close({assigned: true});
        },
        error: () => {
          this.messageService.add({severity: 'error', summary: this.t.translate('common.error'), detail: this.t.translate('book.shelfAssigner.toast.updateFailedDetail')});
          this.dynamicDialogRef.close({assigned: false});
        }
      });
  }

  private getIdsToUnAssign(book: Book, idsToAssign: Set<number | undefined>): Set<number> {
    const idsToUnassign = new Set<number>();
    book.shelves?.forEach(shelf => {
      if (!idsToAssign.has(shelf.id)) {
        idsToUnassign.add(shelf.id!);
      }
    });
    return idsToUnassign;
  }

  createShelfDialog(): void {
    const dialogRef = this.bookDialogHelper.openShelfCreatorDialog();

    dialogRef.onClose.subscribe((created: boolean) => {
      if (created) {
        this.shelfService.reloadShelves();
      }
    });
  }

  closeDialog(): void {
    this.dynamicDialogRef.close({assigned: false});
  }

  isShelfSelected(shelf: Shelf): boolean {
    return this.selectedShelves.some(s => s.id === shelf.id);
  }

  getShelfIcon(shelf: Shelf): IconSelection {
    if (shelf.iconType === 'CUSTOM_SVG') {
      return {type: 'CUSTOM_SVG', value: shelf.icon ?? ""};
    } else {
      return {type: 'PRIME_NG', value: `pi pi-${shelf.icon}`};
    }
  }
}
