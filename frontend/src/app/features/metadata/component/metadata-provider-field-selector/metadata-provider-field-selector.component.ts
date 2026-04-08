import {Component, effect, inject, Input} from '@angular/core';
import {ToggleSwitchModule} from 'primeng/toggleswitch';
import {FormsModule} from '@angular/forms';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {AppSettingKey, MetadataProviderSpecificFields} from '../../../../shared/model/app-settings.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-metadata-provider-field-selector',
  standalone: true,
  imports: [ToggleSwitchModule, FormsModule, TranslocoDirective],
  templateUrl: './metadata-provider-field-selector.component.html',
  styleUrl: './metadata-provider-field-selector.component.scss'
})
export class MetadataProviderFieldSelectorComponent {
  @Input() selectedFields: string[] = [];

  private appSettingsService = inject(AppSettingsService);
  private t = inject(TranslocoService);

  providerGroups: { labelKey: string, fields: string[] }[] = [
    {labelKey: 'amazon', fields: ['asin', 'amazonRating', 'amazonReviewCount']},
    {labelKey: 'googleBooks', fields: ['googleId']},
    {labelKey: 'goodreads', fields: ['goodreadsId', 'goodreadsRating', 'goodreadsReviewCount']},
    {labelKey: 'hardcover', fields: ['hardcoverId', 'hardcoverBookId', 'hardcoverRating', 'hardcoverReviewCount']},
    {labelKey: 'audible', fields: ['audibleId', 'audibleRating', 'audibleReviewCount']},
    {labelKey: 'comicvine', fields: ['comicvineId']},
    {labelKey: 'lubimyczytac', fields: ['lubimyczytacId', 'lubimyczytacRating']},
    {labelKey: 'ranobedb', fields: ['ranobedbId', 'ranobedbRating']}
  ];

  getProviderLabel(key: string): string {
    return this.t.translate('settingsMeta.fieldSelector.providers.' + key);
  }

  getFieldLabel(field: string): string {
    return this.t.translate('settingsMeta.fieldSelector.fields.' + field);
  }

  private readonly allFieldNames: (keyof MetadataProviderSpecificFields)[] = [
    'asin', 'amazonRating', 'amazonReviewCount',
    'googleId',
    'goodreadsId', 'goodreadsRating', 'goodreadsReviewCount',
    'hardcoverId', 'hardcoverBookId', 'hardcoverRating', 'hardcoverReviewCount',
    'comicvineId',
    'lubimyczytacId', 'lubimyczytacRating',
    'ranobedbId', 'ranobedbRating',
    'audibleId', 'audibleRating', 'audibleReviewCount'
  ];

  private readonly syncSettingsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (settings?.metadataProviderSpecificFields) {
      this.selectedFields = this.toFieldArray(settings.metadataProviderSpecificFields);
    }
  });

  toggleField(field: string, checked: boolean) {
    this.selectedFields = checked
      ? [...this.selectedFields, field]
      : this.selectedFields.filter(f => f !== field);

    this.appSettingsService.saveSettings([{
      key: AppSettingKey.METADATA_PROVIDER_SPECIFIC_FIELDS ?? 'metadataProviderSpecificFields',
      newValue: this.toFieldState(this.selectedFields)
    }]).subscribe();
  }

  private toFieldArray(fieldState: MetadataProviderSpecificFields): string[] {
    const selectedFields: string[] = [];
    for (const [field, enabled] of Object.entries(fieldState)) {
      if (enabled) {
        selectedFields.push(field);
      }
    }
    return selectedFields;
  }

  private toFieldState(selectedFields: string[]): MetadataProviderSpecificFields {
    const fieldState = {} as MetadataProviderSpecificFields;
    for (const field of this.allFieldNames) {
      fieldState[field] = selectedFields.includes(field);
    }
    return fieldState;
  }
}
