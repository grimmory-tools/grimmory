import {toIconSelection, type IconSelection, type IconType} from '../icons/icon-selection';

export interface BrowseFacetEntity {
  readonly name: string;
  readonly icon?: string | null;
  readonly iconType?: IconType | null;
}

export type BrowseFacetEntityLookup = (id: string) => BrowseFacetEntity | undefined;

export function browseFacetEntityLookup(
  entities: readonly (BrowseFacetEntity & {readonly id?: string | number})[],
): BrowseFacetEntityLookup {
  const byId = new Map(entities.map(entity => [`${entity.id}`, entity]));
  return id => byId.get(id);
}

export function browseFacetEntityIcon(entity: BrowseFacetEntity | undefined): IconSelection | null {
  return entity?.icon ? toIconSelection(entity.icon, entity.iconType) : null;
}

export const LIBRARY_FACET = {
  key: 'library',
  valueLabel: (value: string, deps: {readonly library: BrowseFacetEntityLookup}) => deps.library(value)?.name ?? null,
  valueIcon: (value: string, deps: {readonly library: BrowseFacetEntityLookup}) => browseFacetEntityIcon(deps.library(value)),
} as const;
