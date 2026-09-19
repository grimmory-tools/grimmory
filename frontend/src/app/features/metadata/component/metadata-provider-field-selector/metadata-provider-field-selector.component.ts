import {Component, computed, effect, inject, Input} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ToggleSwitch} from '@openng/optimus-ui/toggleswitch';
import {FormsModule} from '@angular/forms';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {AppSettingKey, MetadataProviderSpecificFields} from '../../../../shared/model/app-settings.model';
import {MetadataProviderFieldsService, providerFieldRecord} from '../../../../shared/metadata';
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
  @Input() selectedFields: string[] = [];

  private appSettingsService = inject(AppSettingsService);
  private t = inject(TranslocoService);
  private catalog = inject(MetadataCatalogService);
  private providerFields = inject(MetadataProviderFieldsService);
  private readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});

  readonly providerGroups = computed(() => {
    const lang = this.activeLang();
    return this.catalog.providers()
      .filter(provider => provider.book)
      .map(provider => ({
        label: this.t.translate(provider.labelKey, {}, lang),
        fields: this.providerFields.fields()
          .filter(field => field.provider === provider.id)
          .map(field => ({name: field.name, label: this.providerFields.label(field.name)})),
      }));
  });

  private readonly allFieldNames = computed<(keyof MetadataProviderSpecificFields)[]>(
    () => this.providerFields.fields().map(field => field.name),
  );

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
    return providerFieldRecord(this.providerFields.fields(), field => selectedFields.includes(field.name));
  }
}
