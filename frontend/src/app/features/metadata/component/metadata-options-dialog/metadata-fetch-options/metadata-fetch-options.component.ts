import {Component, inject, Input, OnChanges, OnInit, signal, SimpleChanges} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {FormsModule} from '@angular/forms';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {Button} from '@openng/optimus-ui/button';
import {InputText} from '@openng/optimus-ui/inputtext';
import {MetadataRefreshRequest} from '../../../model/request/metadata-refresh-request.model';
import {MetadataRefreshType} from '../../../model/request/metadata-refresh-type.enum';
import {MetadataRefreshOptions} from '../../../model/request/metadata-refresh-options.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {MetadataAdvancedFetchOptionsComponent} from '../metadata-advanced-fetch-options/metadata-advanced-fetch-options.component';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {TranslocoDirective} from '@jsverse/transloco';
import {API_CONFIG} from '../../../../../core/config/api-config';

interface MetadataScorePreview {
  total: number;
  belowThreshold: number;
  atOrAboveThreshold: number;
  unknown: number;
}

@Component({
  selector: 'app-metadata-fetch-options',
  standalone: true,
  templateUrl: './metadata-fetch-options.component.html',
  imports: [
    MetadataAdvancedFetchOptionsComponent,
    TranslocoDirective,
    FormsModule,
    Button,
    InputText
  ],
  styleUrl: './metadata-fetch-options.component.scss'
})
export class MetadataFetchOptionsComponent implements OnInit, OnChanges {
  private readonly dynamicDialogConfig = inject(DynamicDialogConfig);
  dynamicDialogRef = inject(DynamicDialogRef);
  private readonly taskHelperService = inject(TaskHelperService);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly http = inject(HttpClient);
  @Input() dialogData?: {
    libraryId?: number | null;
    bookIds?: number[];
    metadataRefreshType?: MetadataRefreshType;
  };

  libraryId?: number;
  bookIds: number[] = [];
  metadataRefreshType: MetadataRefreshType = MetadataRefreshType.BOOKS;
  currentMetadataOptions!: MetadataRefreshOptions;
  previewThreshold: number | null = 90;
  readonly applyThreshold = signal(false);
  readonly databaseOnly = signal(false);
  readonly databaseOnlyReviewConflict = signal(false);
  preview = signal<MetadataScorePreview | null>(null);
  previewLoading = signal(false);
  previewError = signal(false);
  private previewGeneration = 0;

  get isLibraryPreviewAvailable(): boolean {
    return this.metadataRefreshType === MetadataRefreshType.LIBRARY && this.libraryId !== undefined;
  }

  get isPreviewThresholdValid(): boolean {
    return this.previewThreshold !== null && Number.isFinite(this.previewThreshold)
      && this.previewThreshold >= 0 && this.previewThreshold <= 100;
  }

  constructor() {
    const settings = this.appSettingsService.appSettings();
    if (settings) {
      this.currentMetadataOptions = settings.defaultMetadataRefreshOptions;
    }
  }

  ngOnInit(): void {
    this.applyContext(this.dialogData ?? this.dynamicDialogConfig.data ?? {});
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('dialogData' in changes) {
      this.applyContext(changes['dialogData'].currentValue ?? {});
    }
  }

  onMetadataSubmit(metadataRefreshOptions: MetadataRefreshOptions) {
    this.databaseOnlyReviewConflict.set(this.databaseOnly() && !!metadataRefreshOptions.reviewBeforeApply);
    if (this.databaseOnlyReviewConflict()) {
      return;
    }
    const threshold = this.previewThreshold;
    if (this.isLibraryPreviewAvailable && this.applyThreshold() && (threshold === null || !this.isPreviewThresholdValid)) {
      return;
    }
    const metadataRefreshRequest: MetadataRefreshRequest = {
      refreshType: this.metadataRefreshType,
      refreshOptions: metadataRefreshOptions,
      bookIds: this.bookIds,
      libraryId: this.libraryId
    };
    if (this.isLibraryPreviewAvailable && this.applyThreshold() && threshold !== null) {
      metadataRefreshRequest.scoreThreshold = threshold;
    }
    if (this.databaseOnly()) {
      metadataRefreshRequest.databaseOnly = true;
    }
    this.taskHelperService.refreshMetadataTask(metadataRefreshRequest).subscribe();
    this.dynamicDialogRef.close();
  }

  onPreviewThresholdChange(value: number | null): void {
    this.previewThreshold = value;
    this.clearPreview();
  }

  loadPreview(): void {
    const threshold = this.previewThreshold;
    if (!this.isLibraryPreviewAvailable || threshold === null || !this.isPreviewThresholdValid || this.previewLoading()) {
      return;
    }
    const generation = ++this.previewGeneration;
    this.preview.set(null);
    this.previewError.set(false);
    this.previewLoading.set(true);
    this.http.get<MetadataScorePreview>(
      `${API_CONFIG.BASE_URL}/api/v1/books/metadata/refresh-preview/${this.libraryId}`,
      {params: {threshold}},
    ).subscribe({
      next: result => {
        if (generation === this.previewGeneration) {
          this.preview.set(result);
          this.previewLoading.set(false);
        }
      },
      error: () => {
        if (generation === this.previewGeneration) {
          this.previewError.set(true);
          this.previewLoading.set(false);
        }
      },
    });
  }

  private clearPreview(): void {
    this.previewGeneration++;
    this.preview.set(null);
    this.previewError.set(false);
    this.previewLoading.set(false);
  }

  private applyContext(context: {
    libraryId?: number | null;
    bookIds?: number[];
    metadataRefreshType?: MetadataRefreshType;
  }): void {
    this.clearPreview();
    this.applyThreshold.set(false);
    this.databaseOnly.set(false);
    this.databaseOnlyReviewConflict.set(false);
    this.libraryId = context.libraryId ?? undefined;
    this.bookIds = context.bookIds ?? [];
    this.metadataRefreshType = context.metadataRefreshType ?? MetadataRefreshType.BOOKS;
  }
}
