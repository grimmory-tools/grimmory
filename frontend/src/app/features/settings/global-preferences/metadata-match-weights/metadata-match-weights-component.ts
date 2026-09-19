import {Component, computed, DestroyRef, effect, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {MessageService} from '@openng/optimus-ui/api';
import {MetadataMatchWeightsService} from '../../../../shared/service/metadata-match-weights.service';
import {Button} from '@openng/optimus-ui/button';
import {AppSettingKey, MetadataMatchWeights} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {InputNumber} from '@openng/optimus-ui/inputnumber';
import {MetadataProviderFieldsService} from '../../../../shared/metadata';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

const GENERIC_WEIGHT_KEYS = [
  'title', 'subtitle', 'authors', 'description', 'publisher', 'publishedDate',
  'categories', 'coverImage', 'seriesName', 'seriesNumber', 'seriesTotal',
  'language', 'isbn13', 'isbn10', 'pageCount',
];

const DOUBAN_WEIGHT_KEYS = ['doubanRating', 'doubanReviewCount'];

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

  private fb = inject(FormBuilder);
  private weightsService = inject(MetadataMatchWeightsService);
  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);
  private providerFields = inject(MetadataProviderFieldsService);
  private destroyRef = inject(DestroyRef);

  readonly orderedKeys = computed(() => [
    ...GENERIC_WEIGHT_KEYS,
    ...this.providerFields.scoreFields().map(field => field.name),
    ...DOUBAN_WEIGHT_KEYS,
  ]);

  form: FormGroup = this.buildForm();
  isSaving = false;
  isRecalculating = false;

  private readonly syncSettingsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (settings?.metadataMatchWeights) {
      this.patchPristineControls(settings.metadataMatchWeights);
    }
  });

  private buildForm(): FormGroup {
    return this.fb.group(Object.fromEntries(
      this.orderedKeys().map(key => [key, [0, [Validators.required, Validators.min(0)]]]),
    ));
  }

  getFieldLabel(key: string): string {
    const providerField = this.providerFields.fields().find(field => field.name === key);
    return providerField ? this.providerFields.label(providerField.name) : this.t.translate('settingsMeta.matchWeights.fields.' + key);
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
