import {Component, inject, OnInit} from '@angular/core';
import {LibraryCreatorComponent} from '../../../library-creator/library-creator.component';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {LibraryService} from '../../../book/service/library.service';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {Button} from 'primeng/button';
import {AsyncPipe} from '@angular/common';
import {DashboardScrollerComponent} from '../dashboard-scroller/dashboard-scroller.component';
import {BookService} from '../../../book/service/book.service';
import {BookState} from '../../../book/model/state/book-state.model';
import {Book, ReadStatus} from '../../../book/model/book.model';
import {UserService} from '../../../settings/user-management/user.service';
import {ProgressSpinner} from 'primeng/progressspinner';

@Component({
  selector: 'app-main-dashboard',
  templateUrl: './main-dashboard.component.html',
  styleUrls: ['./main-dashboard.component.scss'],
  imports: [
    Button,
    DashboardScrollerComponent,
    AsyncPipe,
    ProgressSpinner
  ],
  providers: [DialogService],
  standalone: true
})
export class MainDashboardComponent implements OnInit {
  ref: DynamicDialogRef | undefined | null;

  private bookService = inject(BookService);
  private dialogService = inject(DialogService);
  protected userService = inject(UserService);

  bookState$ = this.bookService.bookState$;

  lastReadBooks$: Observable<Book[]> | undefined;
  latestAddedBooks$: Observable<Book[]> | undefined;
  randomBooks$: Observable<Book[]> | undefined;

  isLibrariesEmpty$: Observable<boolean> = inject(LibraryService).libraryState$.pipe(
    map(state => !state.libraries || state.libraries.length === 0)
  );

  ngOnInit(): void {

    this.lastReadBooks$ = this.bookService.bookState$.pipe(
      map((state: BookState) => (
        (state.books || []).filter(book => book.lastReadTime && (book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING || book.readStatus === ReadStatus.PAUSED))
          .sort((a, b) => {
            const aTime = new Date(a.lastReadTime!).getTime();
            const bTime = new Date(b.lastReadTime!).getTime();
            return bTime - aTime;
          })
          .slice(0, 25)
      ))
    );

    this.latestAddedBooks$ = this.bookService.bookState$.pipe(
      map((state: BookState) => (
        (state.books || [])
          .filter(book => book.addedOn)
          .sort((a, b) => {
            const aTime = new Date(a.addedOn!).getTime();
            const bTime = new Date(b.addedOn!).getTime();
            return bTime - aTime;
          })
          .slice(0, 25)
      ))
    );

    this.randomBooks$ = this.bookService.bookState$.pipe(
      map((state: BookState) => this.getRandomBooks(state.books || [], 15))
    );
  }

  private getRandomBooks(books: Book[], count: number): Book[] {
    const shuffled = books.sort(() => 0.5 - Math.random());
    return shuffled.slice(0, count);
  }

  createNewLibrary() {
    this.ref = this.dialogService.open(LibraryCreatorComponent, {
      header: 'Create New Library',
      modal: true,
      closable: true
    });
  }
}
