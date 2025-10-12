import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Subject, takeUntil} from 'rxjs';

import {MetadataRefreshType} from '../../model/request/metadata-refresh-type.enum';
import {MetadataRefreshOptions} from '../../model/request/metadata-refresh-options.model';

import {DynamicDialogConfig} from 'primeng/dynamicdialog';
import {BookService} from '../../../book/service/book.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {Book} from '../../../book/model/book.model';
import {FormsModule} from '@angular/forms';
import {MetadataFetchOptionsComponent} from '../metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';

@Component({
  selector: 'app-multi-book-metadata-fetch-component',
  standalone: true,
  templateUrl: './multi-book-metadata-fetch-component.html',
  styleUrl: './multi-book-metadata-fetch-component.scss',
  imports: [MetadataFetchOptionsComponent, FormsModule],
})
export class MultiBookMetadataFetchComponent implements OnInit, OnDestroy {
  bookIds!: number[];
  booksToShow: Book[] = [];
  metadataRefreshType!: MetadataRefreshType;
  currentMetadataOptions!: MetadataRefreshOptions;

  private destroy$ = new Subject<void>();

  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private bookService = inject(BookService);
  private appSettingsService = inject(AppSettingsService);
  expanded = false;

  ngOnInit(): void {
    this.bookIds = this.dynamicDialogConfig.data.bookIds;
    this.metadataRefreshType = this.dynamicDialogConfig.data.metadataRefreshType;

    this.booksToShow = this.bookService.getBooksByIdsFromState(this.bookIds);

    this.appSettingsService.appSettings$
      .pipe(takeUntil(this.destroy$))
      .subscribe(settings => {
        this.currentMetadataOptions = settings!.defaultMetadataRefreshOptions;
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
