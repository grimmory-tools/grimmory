import {computed, inject, Service} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoService} from '@jsverse/transloco';

import {BOOK_FIELDS, type BookLockName, type MetadataFieldName} from './metadata-fields';
import {MetadataProviderFieldsService} from './metadata-provider-fields.service';
import type {MetadataProviderFieldName} from './metadata-providers';

@Service()
export class MetadataFieldLabelService {
  private readonly t = inject(TranslocoService);
  private readonly providerFields = inject(MetadataProviderFieldsService);
  private readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});
  private readonly providerFieldNames = computed(() => new Set<string>(this.providerFields.fields().map(field => field.name)));

  label(name: MetadataFieldName | BookLockName | 'ebookCover'): string {
    const lang = this.activeLang();
    if (this.isProviderField(name)) return this.providerFields.label(name);
    return this.t.translate<string>(`metadata.fields.${name}`, {}, lang);
  }

  readonly options = computed((): Partial<Record<string, {label: string; value: string | number}[]>> => {
    const lang = this.activeLang();
    return Object.fromEntries(BOOK_FIELDS.flatMap(field => field.options ? [[
      field.name,
      field.options.map(value => ({label: this.t.translate<string>(`metadata.fieldValues.${field.name}.${value}`, {}, lang), value})),
    ]] : []));
  });

  private isProviderField(name: string): name is MetadataProviderFieldName {
    return this.providerFieldNames().has(name);
  }
}
