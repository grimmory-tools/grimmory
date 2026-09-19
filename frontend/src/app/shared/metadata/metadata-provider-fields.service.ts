import {computed, inject, Injectable} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';

import {MetadataCatalogService} from './metadata-catalog.service';
import {providerFieldsOf, providerScoreFieldsOf} from './metadata-provider-fields';
import type {MetadataProviderFieldName} from './metadata-providers';

@Injectable({providedIn: 'root'})
export class MetadataProviderFieldsService {
  private readonly catalog = inject(MetadataCatalogService);
  private readonly t = inject(TranslocoService);

  readonly fields = computed(() => providerFieldsOf(this.catalog.providers()));

  readonly scoreFields = computed(() => providerScoreFieldsOf(this.fields()));

  private readonly byName = computed(() => new Map(this.fields().map(field => [field.name, field])));

  label(name: MetadataProviderFieldName): string {
    const field = this.byName().get(name);
    if (!field) return name;
    return this.t.translate<string>(field.labelKey, {provider: this.t.translate<string>(field.providerLabelKey)});
  }
}
