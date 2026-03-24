import {Component, inject, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {MessageService} from 'primeng/api';
import {MetadataMatchWeightsService} from '../../../../shared/service/metadata-match-weights.service';
import {Button} from 'primeng/button';
import {AppSettingKey, AppSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {InputNumber} from 'primeng/inputnumber';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-metadata-match-weights-component',
  imports: [
    ReactiveFormsModule,
    Button,
    InputNumber,
    TranslocoDirective
  ],
  templateUrl: './metadata-match-weights-component.html',
  styleUrl: './metadata-match-weights-component.scss'
})
export class MetadataMatchWeightsComponent implements OnInit {

  readonly orderedFieldKeys: string[] = [
    'title', 'subtitle', 'authors', 'description', 'publisher', 'publishedDate',
    'categories', 'coverImage', 'seriesName', 'seriesNumber', 'seriesTotal',
    'language', 'isbn13', 'isbn10', 'pageCount',
    'amazonRating', 'amazonReviewCount', 'goodreadsRating', 'goodreadsReviewCount',
    'hardcoverRating', 'hardcoverReviewCount', 'audibleRating', 'audibleReviewCount',
    'doubanRating', 'doubanReviewCount', 'ranobedbRating'
  ];

  form!: FormGroup;
  isSaving = false;
  isRecalculating = false;

  private weightsService = inject(MetadataMatchWeightsService);
  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private fb = inject(FormBuilder);
  private t = inject(TranslocoService);

  ngOnInit(): void {
    this.form = this.fb.group({
      title: [0, [Validators.required, Validators.min(0)]],
      subtitle: [0, [Validators.required, Validators.min(0)]],
      description: [0, [Validators.required, Validators.min(0)]],
      publisher: [0, [Validators.required, Validators.min(0)]],
      publishedDate: [0, [Validators.required, Validators.min(0)]],
      authors: [0, [Validators.required, Validators.min(0)]],
      categories: [0, [Validators.required, Validators.min(0)]],
      seriesName: [0, [Validators.required, Validators.min(0)]],
      seriesNumber: [0, [Validators.required, Validators.min(0)]],
      seriesTotal: [0, [Validators.required, Validators.min(0)]],
      isbn13: [0, [Validators.required, Validators.min(0)]],
      isbn10: [0, [Validators.required, Validators.min(0)]],
      pageCount: [0, [Validators.required, Validators.min(0)]],
      language: [0, [Validators.required, Validators.min(0)]],
      amazonRating: [0, [Validators.required, Validators.min(0)]],
      amazonReviewCount: [0, [Validators.required, Validators.min(0)]],
      goodreadsRating: [0, [Validators.required, Validators.min(0)]],
      goodreadsReviewCount: [0, [Validators.required, Validators.min(0)]],
      hardcoverRating: [0, [Validators.required, Validators.min(0)]],
      hardcoverReviewCount: [0, [Validators.required, Validators.min(0)]],
      doubanRating: [0, [Validators.required, Validators.min(0)]],
      doubanReviewCount: [0, [Validators.required, Validators.min(0)]],
      ranobedbRating: [0, [Validators.required, Validators.min(0)]],
      audibleRating: [0, [Validators.required, Validators.min(0)]],
      audibleReviewCount: [0, [Validators.required, Validators.min(0)]],
      coverImage: [0, [Validators.required, Validators.min(0)]],
    });
    const settings = this.appSettingsService.appSettings();
    if (settings?.metadataMatchWeights) {
      this.form.patchValue(settings.metadataMatchWeights);
    }
  }

  get orderedKeys(): string[] {
    return this.orderedFieldKeys;
  }

  getFieldLabel(key: string): string {
    return this.t.translate('settingsMeta.matchWeights.fields.' + key);
  }

  save(): void {
    if (this.form.invalid) return;

    this.isSaving = true;

    const payload = [
      {
        key: AppSettingKey.METADATA_MATCH_WEIGHTS,
        newValue: this.form.value
      }
    ];

    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsMeta.matchWeights.saveSuccess')
        });
        this.isSaving = false;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsMeta.matchWeights.saveError')
        });
        this.isSaving = false;
      }
    });
  }

  recalculate(): void {
    this.isRecalculating = true;
    this.weightsService.recalculateAll().subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsMeta.matchWeights.recalcSuccess')
        });
        this.isRecalculating = false;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsMeta.matchWeights.recalcError')
        });
        this.isRecalculating = false;
      }
    });
  }
}
