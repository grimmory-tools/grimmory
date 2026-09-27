import { Library } from '../../../features/book/model/library.model';
import { Shelf } from '../../../features/book/model/shelf.model';
import { MagicShelf } from '../../../features/magic-shelf/service/magic-shelf.service';
import { LibraryHealthService } from '../../../features/book/service/library-health.service';
import { SortPref } from '../sidebar-sort-preferences';

import {
  libraryBrowseScope,
  magicShelfBrowseScope,
  shelfBrowseScope,
  UNSHELVED_BROWSE_SCOPE,
} from '../../../features/book/browse/book-browse-scope';
import { SidebarLeaf, SidebarSection } from '../navigation/nav-item.model';
import { buildHomeNavItems, findPageNavItem, ShellNavPermissions } from '../navigation/nav-catalog';

export interface HomeCounts {
  series: number;
  authors: number;
}

export interface LibrarySectionDeps {
  health: Pick<LibraryHealthService, 'isUnhealthy'>;
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
      bookScope: item.id === 'allBooks' ? null : undefined,
      bookCount: homeItemBookCount(item.id, counts),
    })),
  }];
}

export function buildToolsSection(
  translate: TranslateFn,
  permissions: ShellNavPermissions,
): SidebarSection[] {
  const items = [
    findPageNavItem('libraryStats', translate, permissions),
    findPageNavItem('metadataManager', translate, permissions),
    findPageNavItem('bookdrop', translate, permissions),
  ].filter((item): item is SidebarLeaf => !!item);

  if (items.length === 0) return [];

  return [{
    id: 'tools',
    menuKey: 'tools',
    label: translate('layout.menu.tools'),
    expandable: true,
    items,
  }];
}

function homeItemBookCount(itemId: string, counts: HomeCounts): number | undefined {
  switch (itemId) {
    case 'series': return counts.series;
    case 'authors': return counts.authors;
    default: return undefined;
  }
}

export function buildLibrarySection(
  libraries: Library[],
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
      menuTarget: {type: 'library', entity: library},
      icon: library.icon || undefined,
      iconType: library.iconType ?? undefined,
      routerLink: [`/library/${library.id}/books`],
      bookScope: libraryBrowseScope(library.id),
      unhealthy: deps.health.isUnhealthy(library.id),
    })),
  }];
}

export function buildShelfSection(
  shelves: Shelf[],
  sort: SortPref,
  translate: TranslateFn,
): SidebarSection[] {
  const sorted = sortByPref(withIds(shelves), sort);
  const pinnedIndex = sorted.findIndex((shelf) => shelf.systemKey === 'kobo');
  const pinned = pinnedIndex === -1 ? null : sorted.splice(pinnedIndex, 1)[0];

  const items: SidebarLeaf[] = [{
    id: 'shelfUnshelved',
    label: translate('layout.menu.unshelved'),
    type: 'shelf',
    icon: 'inbox',
    routerLink: ['/unshelved-books'],
    bookScope: UNSHELVED_BROWSE_SCOPE,
  }];

  if (pinned) {
    items.push(toShelfNavItem(pinned));
  }

  for (const shelf of sorted) {
    items.push(toShelfNavItem(shelf));
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

function toShelfNavItem(shelf: WithId<Shelf>): SidebarLeaf {
  return {
    id: `shelf:${shelf.id}`,
    label: shelf.name,
    type: 'shelf',
    menuTarget: {type: 'shelf', entity: shelf},
    icon: shelf.icon || undefined,
    iconType: shelf.iconType ?? undefined,
    routerLink: [`/shelf/${shelf.id}/books`],
    bookScope: shelfBrowseScope(shelf.id),
  };
}

export function buildMagicShelfSection(
  shelves: MagicShelf[],
  sort: SortPref,
  translate: TranslateFn,
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
      menuTarget: {type: 'magicShelf', entity: shelf},
      icon: shelf.icon || undefined,
      iconType: shelf.iconType ?? undefined,
      routerLink: [`/magic-shelf/${shelf.id}/books`],
      bookScope: magicShelfBrowseScope(shelf.id),
    })),
  }];
}
