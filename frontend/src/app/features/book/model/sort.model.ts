export enum SortDirection {
  ASCENDING = 'ASCENDING',
  DESCENDING = 'DESCENDING'
}

export interface SortOption {
  label: string;
  field: string;
  direction: SortDirection;
}
