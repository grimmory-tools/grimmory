import { NavItem, NavItemType } from './nav-item.model';

export interface ShellNavPermissions {
  admin?: boolean;
  canManageLibrary?: boolean;
  canEditMetadata?: boolean;
  canUpload?: boolean;
  canAccessLibraryStats?: boolean;
  canAccessUserStats?: boolean;
  canAccessBookdrop?: boolean;
}

export interface ShellActionHandlers {
  createLibrary: () => void;
  createShelf: () => void;
  createMagicShelf: () => void;
  uploadBook: () => void;
}

type TranslateFn = (key: string) => string;

interface PageDefinition {
  id: string;
  labelKey: string;
  icon: string;
  routerLink: string[];
  type?: NavItemType;
  isVisible?: (permissions: ShellNavPermissions) => boolean;
}

interface ActionDefinition {
  id: string;
  labelKey: string;
  icon: string;
  run: (handlers: ShellActionHandlers) => () => void;
  isVisible?: (permissions: ShellNavPermissions) => boolean;
}

function canManageLibraries(permissions: ShellNavPermissions): boolean {
  return !!permissions.admin || !!permissions.canManageLibrary;
}

function canAccessLibraryStats(permissions: ShellNavPermissions): boolean {
  return !!permissions.admin || !!permissions.canAccessLibraryStats;
}

function canAccessUserStats(permissions: ShellNavPermissions): boolean {
  return !!permissions.admin || !!permissions.canAccessUserStats;
}

function canAccessBookdrop(permissions: ShellNavPermissions): boolean {
  return !!permissions.admin || !!permissions.canAccessBookdrop;
}

function canEditMetadata(permissions: ShellNavPermissions): boolean {
  return !!permissions.admin || !!permissions.canEditMetadata;
}

function canUploadBooks(permissions: ShellNavPermissions): boolean {
  return !!permissions.admin || !!permissions.canUpload;
}

const HOME_PAGE_DEFINITIONS: readonly PageDefinition[] = [
  {
    id: 'dashboard',
    labelKey: 'layout.menu.dashboard',
    icon: 'pi-home',
    routerLink: ['/dashboard'],
  },
  {
    id: 'allBooks',
    labelKey: 'layout.menu.allBooks',
    icon: 'pi-book',
    routerLink: ['/all-books'],
    type: 'allBooks',
  },
  {
    id: 'series',
    labelKey: 'layout.menu.series',
    icon: 'pi-objects-column',
    routerLink: ['/series'],
    type: 'series',
  },
  {
    id: 'authors',
    labelKey: 'layout.menu.authors',
    icon: 'pi-users',
    routerLink: ['/authors'],
    type: 'authors',
  },
  {
    id: 'notebook',
    labelKey: 'layout.menu.notebook',
    icon: 'pi-pencil',
    routerLink: ['/notebook'],
  },
] as const;

const SECONDARY_PAGE_DEFINITIONS: readonly PageDefinition[] = [
  {
    id: 'settings',
    labelKey: 'layout.menu.settings',
    icon: 'pi-cog',
    routerLink: ['/settings'],
  },
  {
    id: 'libraryStats',
    labelKey: 'layout.menu.libraryStats',
    icon: 'pi-chart-line',
    routerLink: ['/library-stats'],
    isVisible: canAccessLibraryStats,
  },
  {
    id: 'readingStats',
    labelKey: 'layout.menu.readingStats',
    icon: 'pi-chart-bar',
    routerLink: ['/reading-stats'],
    isVisible: canAccessUserStats,
  },
  {
    id: 'metadataManager',
    labelKey: 'layout.menu.metadataManager',
    icon: 'pi-sparkles',
    routerLink: ['/metadata-manager'],
    isVisible: canEditMetadata,
  },
  {
    id: 'bookdrop',
    labelKey: 'layout.menu.bookdrop',
    icon: 'pi-inbox',
    routerLink: ['/bookdrop'],
    isVisible: canAccessBookdrop,
  },
] as const;

const ACTION_DEFINITIONS: readonly ActionDefinition[] = [
  {
    id: 'createLibrary',
    labelKey: 'layout.menu.createLibrary',
    icon: 'pi-folder',
    run: (handlers) => handlers.createLibrary,
    isVisible: canManageLibraries,
  },
  {
    id: 'createShelf',
    labelKey: 'book.shelfCreator.title',
    icon: 'pi-bookmark',
    run: (handlers) => handlers.createShelf,
  },
  {
    id: 'createMagicShelf',
    labelKey: 'layout.menu.createMagicShelf',
    icon: 'pi-sparkles',
    run: (handlers) => handlers.createMagicShelf,
  },
  {
    id: 'uploadBook',
    labelKey: 'layout.menu.uploadBook',
    icon: 'pi-upload',
    run: (handlers) => handlers.uploadBook,
    isVisible: canUploadBooks,
  },
] as const;

function isVisible<T extends { isVisible?: (permissions: ShellNavPermissions) => boolean }>(
  definition: T,
  permissions: ShellNavPermissions,
): boolean {
  return definition.isVisible ? definition.isVisible(permissions) : true;
}

function toPageNavItem(definition: PageDefinition, translate: TranslateFn): NavItem {
  return {
    id: definition.id,
    label: translate(definition.labelKey),
    icon: definition.icon,
    routerLink: definition.routerLink,
    type: definition.type,
  };
}

export function buildHomeNavItems(translate: TranslateFn): NavItem[] {
  return HOME_PAGE_DEFINITIONS.map((definition) => toPageNavItem(definition, translate));
}

export function buildAllNavPages(
  translate: TranslateFn,
  permissions: ShellNavPermissions,
): NavItem[] {
  return [...HOME_PAGE_DEFINITIONS, ...SECONDARY_PAGE_DEFINITIONS]
    .filter((definition) => isVisible(definition, permissions))
    .map((definition) => toPageNavItem(definition, translate));
}

export function buildQuickActionNavItems(
  translate: TranslateFn,
  permissions: ShellNavPermissions,
  handlers: ShellActionHandlers,
): NavItem[] {
  return ACTION_DEFINITIONS
    .filter((definition) => isVisible(definition, permissions))
    .map((definition) => ({
      id: definition.id,
      label: translate(definition.labelKey),
      icon: definition.icon,
      action: definition.run(handlers),
    }));
}

const ALL_PAGE_DEFINITIONS: readonly PageDefinition[] = [
  ...HOME_PAGE_DEFINITIONS,
  ...SECONDARY_PAGE_DEFINITIONS,
] as const;

export function findPageNavItem(
  id: string,
  translate: TranslateFn,
  permissions: ShellNavPermissions,
): NavItem | null {
  const definition = ALL_PAGE_DEFINITIONS.find((page) => page.id === id);
  if (!definition || !isVisible(definition, permissions)) {
    return null;
  }
  return toPageNavItem(definition, translate);
}
