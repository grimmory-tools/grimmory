import {Component, inject} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MetadataRefreshRequest} from '../../../model/request/metadata-refresh-request.model';
import {MetadataRefreshType} from '../../../model/request/metadata-refresh-type.enum';
import {MetadataRefreshOptions} from '../../../model/request/metadata-refresh-options.model';
import {BookService} from '../../../../book/service/book.service';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {filter, take} from 'rxjs/operators';
import {MetadataAdvancedFetchOptionsComponent} from '../metadata-advanced-fetch-options/metadata-advanced-fetch-options.component';

@Component({
  selector: 'app-metadata-fetch-options',
  standalone: true,
  templateUrl: './metadata-fetch-options.component.html',
  imports: [
    MetadataAdvancedFetchOptionsComponent
  ],
  styleUrl: './metadata-fetch-options.component.scss'
})
export class MetadataFetchOptionsComponent {
  libraryId!: number;
  bookIds!: number[];
  metadataRefreshType!: MetadataRefreshType;
  currentMetadataOptions!: MetadataRefreshOptions;

  private dynamicDialogConfig = inject(DynamicDialogConfig);
  private dynamicDialogRef = inject(DynamicDialogRef);
  private bookService = inject(BookService);
  private appSettingsService = inject(AppSettingsService);

  constructor() {
    this.libraryId = this.dynamicDialogConfig.data.libraryId;
    this.bookIds = this.dynamicDialogConfig.data.bookIds;
    this.metadataRefreshType = this.dynamicDialogConfig.data.metadataRefreshType;
    this.appSettingsService.appSettings$.pipe(
      filter(settings => settings != null),
      take(1)
    ).subscribe(settings => {
      this.currentMetadataOptions = settings?.defaultMetadataRefreshOptions;
    });
  }

  onMetadataSubmit(metadataRefreshOptions: MetadataRefreshOptions) {
    const metadataRefreshRequest: MetadataRefreshRequest = {
      refreshType: this.metadataRefreshType,
      refreshOptions: metadataRefreshOptions,
      bookIds: this.bookIds,
      libraryId: this.libraryId
    };
    this.bookService.autoRefreshMetadata(metadataRefreshRequest).subscribe();
    this.dynamicDialogRef.close();
  }
}
