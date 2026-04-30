import {Component, inject, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MetadataRefreshRequest} from '../../../model/request/metadata-refresh-request.model';
import {MetadataRefreshType} from '../../../model/request/metadata-refresh-type.enum';
import {MetadataRefreshOptions} from '../../../model/request/metadata-refresh-options.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {MetadataAdvancedFetchOptionsComponent} from '../metadata-advanced-fetch-options/metadata-advanced-fetch-options.component';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {TranslocoDirective} from '@jsverse/transloco';

@Component({
  selector: 'app-metadata-fetch-options',
  standalone: true,
  templateUrl: './metadata-fetch-options.component.html',
  imports: [
    MetadataAdvancedFetchOptionsComponent,
    TranslocoDirective
  ],
  styleUrl: './metadata-fetch-options.component.scss'
})
export class MetadataFetchOptionsComponent implements OnInit, OnChanges {
  @Input() dialogData?: {
    libraryId?: number | null;
    bookIds?: number[];
    metadataRefreshType?: MetadataRefreshType;
  };

  libraryId?: number;
  bookIds: number[] = [];
  metadataRefreshType: MetadataRefreshType = MetadataRefreshType.BOOKS;
  currentMetadataOptions!: MetadataRefreshOptions;

  private dynamicDialogConfig = inject(DynamicDialogConfig);
  dynamicDialogRef = inject(DynamicDialogRef);
  private taskHelperService = inject(TaskHelperService);
  private appSettingsService = inject(AppSettingsService);

  constructor() {
    const settings = this.appSettingsService.appSettings();
    if (settings) {
      this.currentMetadataOptions = settings.defaultMetadataRefreshOptions;
    }
  }

  ngOnInit(): void {
    this.applyContext(this.dynamicDialogConfig.data ?? {});
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['dialogData']?.currentValue) {
      this.applyContext(changes['dialogData'].currentValue);
    }
  }

  onMetadataSubmit(metadataRefreshOptions: MetadataRefreshOptions) {
    const metadataRefreshRequest: MetadataRefreshRequest = {
      refreshType: this.metadataRefreshType,
      refreshOptions: metadataRefreshOptions,
      bookIds: this.bookIds,
      libraryId: this.libraryId
    };
    this.taskHelperService.refreshMetadataTask(metadataRefreshRequest).subscribe();
    this.dynamicDialogRef.close();
  }

  private applyContext(context: {
    libraryId?: number | null;
    bookIds?: number[];
    metadataRefreshType?: MetadataRefreshType;
  }): void {
    this.libraryId = context.libraryId ?? undefined;
    this.bookIds = context.bookIds ?? [];
    this.metadataRefreshType = context.metadataRefreshType ?? MetadataRefreshType.BOOKS;
  }
}
