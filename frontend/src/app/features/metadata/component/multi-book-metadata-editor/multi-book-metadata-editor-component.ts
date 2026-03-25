import {Component, computed, effect, inject, signal} from '@angular/core';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BookService} from '../../../book/service/book.service';
import {UserService} from '../../../settings/user-management/user.service';
import {MetadataEditorComponent} from '../book-metadata-center/metadata-editor/metadata-editor.component';
import {MetadataSearcherComponent} from '../book-metadata-center/metadata-searcher/metadata-searcher.component';
import {Button} from 'primeng/button';
import {injectQuery} from '@tanstack/angular-query-experimental';

@Component({
  selector: 'app-multi-book-metadata-editor-component',
  imports: [
    MetadataEditorComponent,
    MetadataSearcherComponent,
    Tab,
    TabList,
    TabPanel,
    TabPanels,
    Tabs,
    Button
  ],
  templateUrl: './multi-book-metadata-editor-component.html',
  standalone: true,
  styleUrl: './multi-book-metadata-editor-component.scss'
})
export class MultiBookMetadataEditorComponent {

  private readonly config = inject(DynamicDialogConfig);
  readonly ref = inject(DynamicDialogRef);
  private readonly bookService = inject(BookService);
  private readonly userService = inject(UserService);
  bookIds: number[] = this.config.data?.bookIds ?? [];
  loading = false;

  filteredBooks = computed(() => this.bookService.books().filter(book =>
    !!book.metadata && this.bookIds.includes(book.id)
  ));
  private currentIndex = signal(0);
  private currentBookId = computed(() => this.filteredBooks()[this.currentIndex()]?.id ?? null);
  private bookDetailQuery = injectQuery(() => ({
    ...this.bookService.bookDetailQueryOptions(this.currentBookId() ?? -1, true),
    enabled: this.currentBookId() != null,
  }));
  readonly currentBook = computed(() => this.bookDetailQuery.data() ?? null);

  canEditMetadata = false;
  admin = false;

  constructor() {
    effect(() => {
      const user = this.userService.currentUser();
      if (!user) return;
      this.canEditMetadata = user.permissions?.canEditMetadata ?? false;
      this.admin = user.permissions?.admin ?? false;
    });
  }

  handleNextBook() {
    const next = this.currentIndex() + 1;
    if (next < this.filteredBooks().length) {
      this.currentIndex.set(next);
    }
  }

  handlePreviousBook() {
    const prev = this.currentIndex() - 1;
    if (prev >= 0) {
      this.currentIndex.set(prev);
    }
  }

  handleCloseDialogButton() {
    this.ref.close();
  }

  get disableNext(): boolean {
    return this.currentIndex() >= this.filteredBooks().length - 1;
  }

  get disablePrevious(): boolean {
    return this.currentIndex() <= 0;
  }
}
