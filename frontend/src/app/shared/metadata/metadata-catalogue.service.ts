import {computed, Injectable, signal} from '@angular/core';

import {METADATA_PROVIDER_LIST, type MetadataProviderDescriptor} from './metadata-providers';

@Injectable({providedIn: 'root'})
export class MetadataCatalogueService {
  readonly providers = signal<readonly MetadataProviderDescriptor[]>(METADATA_PROVIDER_LIST).asReadonly();
  readonly reviewProviders = computed(() => this.providers().filter(provider => provider.supportsReviews));

  private readonly byId = computed(
    () => new Map<string, MetadataProviderDescriptor>(this.providers().map(provider => [provider.id, provider])),
  );

  provider(id: string): MetadataProviderDescriptor | undefined {
    return this.byId().get(id);
  }
}
