import {Component, effect, inject} from '@angular/core';

import {MetadataRefreshType} from '../../model/request/metadata-refresh-type.enum';
import {MetadataRefreshOptions} from '../../model/request/metadata-refresh-options.model';

import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BookService} from '../../../book/service/book.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {Book} from '../../../book/model/book.model';
import {FormsModule} from '@angular/forms';
import {MetadataFetchOptionsComponent} from '../metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {Button} from 'primeng/button';

@Component({
  selector: 'app-multi-book-metadata-fetch-component',
  standalone: true,
  templateUrl: './multi-book-metadata-fetch-component.html',
  styleUrl: './multi-book-metadata-fetch-component.scss',
  imports: [MetadataFetchOptionsComponent, FormsModule, Button],
})
export class MultiBookMetadataFetchComponent {
  bookIds: number[] = [];
  libraryId: number | null = null;
  booksToShow: Book[] = [];
  metadataRefreshType: MetadataRefreshType = MetadataRefreshType.BOOKS;
  currentMetadataOptions!: MetadataRefreshOptions;

  private dynamicDialogConfig = inject(DynamicDialogConfig);
  dialogRef = inject(DynamicDialogRef);
  private bookService = inject(BookService);
  private appSettingsService = inject(AppSettingsService);
  expanded = false;

  constructor() {
    const data = this.dynamicDialogConfig.data ?? {};
    this.bookIds = data.bookIds ?? [];
    this.libraryId = data.libraryId ?? null;
    this.metadataRefreshType = data.metadataRefreshType ?? MetadataRefreshType.BOOKS;
    this.booksToShow = this.bookService.getBooksByIds(this.bookIds);

    effect(() => {
      const settings = this.appSettingsService.appSettings();
      if (settings) {
        this.currentMetadataOptions = settings.defaultMetadataRefreshOptions;
      }
    });
  }

  get isLibraryRefresh(): boolean {
    return this.metadataRefreshType === MetadataRefreshType.LIBRARY;
  }
}
