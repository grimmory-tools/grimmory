import { Library } from '../../../features/book/model/library.model';
import { Shelf } from '../../../features/book/model/shelf.model';
import { MagicShelf } from '../../../features/magic-shelf/service/magic-shelf.service';
import { LibraryHealthService } from '../../../features/book/service/library-health.service';
import { LibraryShelfMenuService } from '../../../features/book/service/library-shelf-menu.service';
import { SortPref } from '../sidebar-sort-preferences';

import { SidebarLeaf, SidebarSection } from '../navigation/nav-item.model';
import { buildHomeNavItems } from '../navigation/nav-catalog';

export interface HomeCounts {
  allBooks: number;
  series: number;
  authors: number;
}

export interface LibrarySectionDeps {
  health: Pick<LibraryHealthService, 'isUnhealthy'>;
  menuItems: Pick<LibraryShelfMenuService, 'initializeLibraryMenuItems'>;
}

export interface ShelfSectionDeps {
  menuItems: Pick<LibraryShelfMenuService, 'initializeShelfMenuItems'>;
}

export interface MagicShelfSectionDeps {
  menuItems: Pick<LibraryShelfMenuService, 'initializeMagicShelfMenuItems'>;
}

type TranslateFn = (key: string) => string;

/**
 * The sidebar only renders persisted entities. Models keep `id` optional
 * because they double as create-form payloads, so narrow at this boundary
 * and drop anything mid-create.
 */
type WithId<T extends { id?: number | null }> = T & { id: number };

function withIds<T extends { id?: number | null }>(items: T[]): WithId<T>[] {
  return items.filter((item): item is WithId<T> => item.id != null);
}

function sortByPref<T extends { id: number; name: string }>(items: T[], pref: SortPref): T[] {
  const sorted = [...items].sort((a, b) =>
    pref.field === 'id' ? a.id - b.id : a.name.localeCompare(b.name)
  );
  return pref.order === 'desc' ? sorted.reverse() : sorted;
}

export function buildHomeSection(translate: TranslateFn, counts: HomeCounts): SidebarSection[] {
  return [{
    id: 'home',
    menuKey: 'home',
    label: translate('layout.menu.home'),
    expandable: true,
    items: buildHomeNavItems(translate).map((item) => ({
      ...item,
      bookCount: homeItemBookCount(item.id, counts),
    })),
  }];
}

function homeItemBookCount(itemId: string, counts: HomeCounts): number | undefined {
  switch (itemId) {
    case 'allBooks': return counts.allBooks;
    case 'series': return counts.series;
    case 'authors': return counts.authors;
    default: return undefined;
  }
}

export function buildLibrarySection(
  libraries: Library[],
  bookCounts: ReadonlyMap<number, number>,
  sort: SortPref,
  translate: TranslateFn,
  deps: LibrarySectionDeps,
): SidebarSection[] {
  const sorted = sortByPref(withIds(libraries), sort);
  if (sorted.length === 0) return [];

  return [{
    id: 'libraries',
    menuKey: 'library',
    label: translate('layout.menu.libraries'),
    type: 'library',
    expandable: true,
    items: sorted.map((library) => ({
      id: `library:${library.id}`,
      label: library.name,
      type: 'library',
      icon: library.icon || undefined,
      iconType: library.iconType ?? undefined,
      routerLink: [`/library/${library.id}/books`],
      bookCount: bookCounts.get(library.id) ?? 0,
      unhealthy: deps.health.isUnhealthy(library.id),
      contextMenuItems: deps.menuItems.initializeLibraryMenuItems(library),
    })),
  }];
}

export function buildShelfSection(
  shelves: Shelf[],
  bookCounts: ReadonlyMap<number, number>,
  unshelvedCount: number,
  sort: SortPref,
  translate: TranslateFn,
  deps: ShelfSectionDeps,
): SidebarSection[] {
  const sorted = sortByPref(withIds(shelves), sort);
  const pinnedIndex = sorted.findIndex((shelf) => shelf.systemKey === 'kobo');
  const pinned = pinnedIndex === -1 ? null : sorted.splice(pinnedIndex, 1)[0];

  const items: SidebarLeaf[] = [{
    id: 'shelfUnshelved',
    label: translate('layout.menu.unshelved'),
    type: 'shelf',
    icon: 'pi pi-inbox',
    iconType: 'PRIME_NG',
    routerLink: ['/unshelved-books'],
    bookCount: unshelvedCount,
  }];

  if (pinned) {
    items.push(toShelfNavItem(pinned, bookCounts, deps));
  }

  for (const shelf of sorted) {
    items.push(toShelfNavItem(shelf, bookCounts, deps));
  }

  return [{
    id: 'shelves',
    menuKey: 'shelf',
    type: 'shelf',
    label: translate('layout.menu.shelves'),
    expandable: true,
    items,
  }];
}

function toShelfNavItem(
  shelf: WithId<Shelf>,
  bookCounts: ReadonlyMap<number, number>,
  deps: ShelfSectionDeps,
): SidebarLeaf {
  return {
    id: `shelf:${shelf.id}`,
    label: shelf.name,
    type: 'shelf',
    icon: shelf.icon || undefined,
    iconType: shelf.iconType ?? undefined,
    routerLink: [`/shelf/${shelf.id}/books`],
    bookCount: bookCounts.get(shelf.id) ?? 0,
    contextMenuItems: deps.menuItems.initializeShelfMenuItems(shelf),
  };
}

export function buildMagicShelfSection(
  shelves: MagicShelf[],
  bookCounts: ReadonlyMap<number, number>,
  sort: SortPref,
  translate: TranslateFn,
  deps: MagicShelfSectionDeps,
): SidebarSection[] {
  const sorted = sortByPref(withIds(shelves), sort);
  if (sorted.length === 0) return [];

  return [{
    id: 'magicShelves',
    menuKey: 'magicShelf',
    label: translate('layout.menu.magicShelves'),
    type: 'magicShelf',
    expandable: true,
    items: sorted.map((shelf) => ({
      id: `magicShelf:${shelf.id}`,
      label: shelf.name,
      type: 'magicShelf',
      icon: shelf.icon || undefined,
      iconType: shelf.iconType ?? undefined,
      routerLink: [`/magic-shelf/${shelf.id}/books`],
      bookCount: bookCounts.get(shelf.id) ?? 0,
      contextMenuItems: deps.menuItems.initializeMagicShelfMenuItems(shelf),
    })),
  }];
}
