import {Component, computed, effect, inject, Input} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ToggleSwitch} from '@openng/optimus-ui/toggleswitch';
import {FormsModule} from '@angular/forms';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {AppSettingKey, MetadataProviderSpecificFields} from '../../../../shared/model/app-settings.model';
import {METADATA_PROVIDER_FIELDS, providerFieldRecord} from '../../../../shared/metadata';
import {MetadataCatalogService} from '../../../../shared/metadata/metadata-catalog.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-metadata-provider-field-selector',
  standalone: true,
  imports: [ToggleSwitch, FormsModule, TranslocoDirective],
  templateUrl: './metadata-provider-field-selector.component.html',
  styleUrl: './metadata-provider-field-selector.component.scss'
})
export class MetadataProviderFieldSelectorComponent {
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly t = inject(TranslocoService);
  private readonly catalog = inject(MetadataCatalogService);
  @Input() selectedFields: string[] = [];
  private readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});

  readonly providerGroups = computed(() => {
    const lang = this.activeLang();
    return this.catalog.providers()
      .map(provider => ({
        label: this.t.translate(provider.labelKey, {}, lang),
        fields: METADATA_PROVIDER_FIELDS
          .filter(field => field.provider === provider.id)
          .map(field => ({name: field.name, label: this.t.translate(field.labelKey, {}, lang)})),
      }))
      .filter(group => group.fields.length > 0);
  });

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
    return providerFieldRecord(METADATA_PROVIDER_FIELDS, field => selectedFields.includes(field.name));
  }
}
