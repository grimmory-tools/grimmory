import type {
  MetadataProviderDescriptor,
  MetadataProviderFieldName,
  MetadataProviderId,
  MetadataProviderIdFieldName,
  MetadataProviderRatingFieldName,
  MetadataProviderReviewCountFieldName,
} from './metadata-providers';

export type MetadataProviderFieldRecord<T> = Record<MetadataProviderFieldName, T>;

export type MetadataProviderFieldRole = 'id' | 'rating' | 'reviewCount';

interface ProviderFieldOf<Name extends MetadataProviderFieldName, Role extends MetadataProviderFieldRole> {
  readonly name: Name;
  readonly provider: MetadataProviderId;
  readonly providerLabelKey: string;
  readonly role: Role;
  readonly lockName: `${Name}Locked`;
  readonly valueType: 'string' | 'number';
  readonly labelKey: string;
}

export type MetadataProviderScoreField =
  | ProviderFieldOf<MetadataProviderRatingFieldName, 'rating'>
  | ProviderFieldOf<MetadataProviderReviewCountFieldName, 'reviewCount'>;

export type MetadataProviderField = ProviderFieldOf<MetadataProviderIdFieldName, 'id'> | MetadataProviderScoreField;

export function providerFieldsOf(
  providers: readonly MetadataProviderDescriptor[],
): readonly MetadataProviderField[] {
  return providers.flatMap(provider => {
    const book = provider.book;
    if (!book) return [];
    const field = <Name extends MetadataProviderFieldName, Role extends MetadataProviderFieldRole>(
      name: Name,
      role: Role,
    ): ProviderFieldOf<Name, Role> => ({
      name,
      provider: provider.id,
      providerLabelKey: provider.labelKey,
      role,
      lockName: `${name}Locked`,
      valueType: role === 'id' ? 'string' : 'number',
      labelKey: book.labelKeys?.[name] ?? `metadata.providerFields.${role}`,
    });
    return [
      ...book.ids.map(name => field(name, 'id')),
      ...(book.rating ? [field(book.rating, 'rating')] : []),
      ...(book.reviewCount ? [field(book.reviewCount, 'reviewCount')] : []),
    ];
  });
}

export function providerScoreFieldsOf(
  fields: readonly MetadataProviderField[],
): readonly MetadataProviderScoreField[] {
  return fields.flatMap(field => (field.role === 'id' ? [] : [field]));
}

export function providerFieldRecord<Field extends MetadataProviderField, T>(
  fields: readonly Field[],
  value: (field: Field) => T,
): Record<Field['name'], T> {
  return Object.fromEntries(fields.map(field => [field.name, value(field)])) as Record<Field['name'], T>;
}
