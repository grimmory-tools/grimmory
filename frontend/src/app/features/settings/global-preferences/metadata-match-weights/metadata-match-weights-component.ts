import {Component, DestroyRef, effect, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {MessageService} from 'primeng/api';
import {MetadataMatchWeightsService} from '../../../../shared/service/metadata-match-weights.service';
import {Button} from 'primeng/button';
import {AppSettingKey, MetadataMatchWeights} from '../../../../shared/model/app-settings.model';
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
export class MetadataMatchWeightsComponent {

  readonly orderedFieldKeys: string[] = [
    'title', 'subtitle', 'authors', 'description', 'publisher', 'publishedDate',
    'categories', 'coverImage', 'seriesName', 'seriesNumber', 'seriesTotal',
    'language', 'isbn13', 'isbn10', 'pageCount',
    'amazonRating', 'amazonReviewCount', 'goodreadsRating', 'goodreadsReviewCount',
    'hardcoverRating', 'hardcoverReviewCount', 'audibleRating', 'audibleReviewCount',
    'doubanRating', 'doubanReviewCount', 'ranobedbRating'
  ];

  private fb = inject(FormBuilder);
  form: FormGroup = this.buildForm();
  isSaving = false;
  isRecalculating = false;

  private weightsService = inject(MetadataMatchWeightsService);
  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);
  private destroyRef = inject(DestroyRef);

  private readonly syncSettingsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (settings?.metadataMatchWeights) {
      this.patchPristineControls(settings.metadataMatchWeights);
    }
  });

  private buildForm(): FormGroup {
    return this.fb.group({
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

    this.appSettingsService.saveSettings(payload).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.form.markAsPristine();
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
    this.weightsService.recalculateAll().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
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

  private patchPristineControls(weights: MetadataMatchWeights): void {
    Object.entries(weights).forEach(([key, value]) => {
      const control = this.form.get(key);
      if (control?.pristine) {
        control.patchValue(value, {emitEvent: false});
      }
    });
  }
}
