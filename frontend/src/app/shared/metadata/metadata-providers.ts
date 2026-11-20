const providers = [
  {
    id: 'OpenLibrary',
    settingsKey: 'openLibrary',
    labelKey: 'metadata.providers.openLibrary',
  },
  {
    id: 'Amazon',
    settingsKey: 'amazon',
    labelKey: 'metadata.providers.amazon',
    hasOptions: true,
    supportsReviews: true,
  },
  {
    id: 'GoodReads',
    settingsKey: 'goodReads',
    labelKey: 'metadata.providers.goodReads',
    supportsReviews: true,
  },
  {
    id: 'Google',
    settingsKey: 'google',
    labelKey: 'metadata.providers.google',
    hasOptions: true,
  },
  {
    id: 'Hardcover',
    settingsKey: 'hardcover',
    labelKey: 'metadata.providers.hardcover',
    hasOptions: true,
  },
  {
    id: 'Comicvine',
    settingsKey: 'comicvine',
    labelKey: 'metadata.providers.comicvine',
    hasOptions: true,
  },
  {
    id: 'Douban',
    settingsKey: 'douban',
    labelKey: 'metadata.providers.douban',
    supportsReviews: true,
  },
  {
    id: 'Lubimyczytac',
    settingsKey: 'lubimyczytac',
    labelKey: 'metadata.providers.lubimyczytac',
  },
  {
    id: 'Ranobedb',
    settingsKey: 'ranobedb',
    labelKey: 'metadata.providers.ranobedb',
    hasOptions: true,
  },
  {
    id: 'Audible',
    settingsKey: 'audible',
    labelKey: 'metadata.providers.audible',
    hasOptions: true,
  },
  {
    id: 'AppleBooks',
    settingsKey: 'appleBooks',
    labelKey: 'metadata.providers.appleBooks',
    hasOptions: true,
  },
] as const;

export type MetadataProviderId = typeof providers[number]['id'];

export interface MetadataProviderDescriptor {
  readonly id: MetadataProviderId;
  readonly settingsKey: Uncapitalize<MetadataProviderId>;
  readonly labelKey: string;
  readonly hasOptions?: boolean;
  readonly supportsReviews?: boolean;
}

export const METADATA_PROVIDER_LIST: readonly MetadataProviderDescriptor[] = providers;
