export interface BrowseLink {
  rel: string[];
  href: string;
  type: string;
}

export interface BrowsePageMetadata {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  cursor: string;
}

export interface BrowsePage<T> {
  content: T[];
  page: BrowsePageMetadata;
  links: BrowseLink[];
}

export interface BrowseFacetValue {
  value: string;
  title: string;
  count?: number;
  selected: boolean;
}

export interface BrowseFacetGroup {
  rel: string;
  key: string;
  title: string;
  values: BrowseFacetValue[];
}

export type BrowseFacetLogic = 'and' | 'or' | 'not';
export type BrowseSortDirection = 'asc' | 'desc';

export interface BrowseSortTerm<Key extends string = string> {
  key: Key;
  direction: BrowseSortDirection;
}

export function findBrowsePageLink(
  page: BrowsePage<unknown>,
  rel: string,
): BrowseLink | undefined {
  return page.links.find(link => link.rel.includes(rel));
}
