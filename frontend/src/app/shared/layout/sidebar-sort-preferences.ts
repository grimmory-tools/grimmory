export type SortField = 'name' | 'id';
export type SortOrder = 'asc' | 'desc';

export interface SortPref {
  field: SortField;
  order: SortOrder;
}

export type SidebarSortPreferenceKey =
  | 'sidebarLibrarySorting'
  | 'sidebarShelfSorting'
  | 'sidebarMagicShelfSorting';

export const DEFAULT_LIBRARY_SORT: SortPref = { field: 'name', order: 'desc' };
export const DEFAULT_SHELF_SORT: SortPref = { field: 'name', order: 'asc' };
export const DEFAULT_MAGIC_SHELF_SORT: SortPref = { field: 'name', order: 'asc' };

export function normalizeSortPref(
  raw: { field?: string; order?: string } | null | undefined,
  fallback: SortPref,
): SortPref {
  const field = typeof raw?.field === 'string' ? raw.field.toLowerCase() : null;
  const order = typeof raw?.order === 'string' ? raw.order.toLowerCase() : null;

  return {
    field: field === 'id' || field === 'name' ? field : fallback.field,
    order: order === 'asc' || order === 'desc' ? order : fallback.order,
  };
}

export function sortPrefEqual(a: SortPref, b: SortPref): boolean {
  return a.field === b.field && a.order === b.order;
}
