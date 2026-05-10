import type { MenuItem } from 'primeng/api';

export type NavIconType = 'PRIME_NG' | 'CUSTOM_SVG';

export type NavItemType =
  | 'library' | 'shelf' | 'magicShelf'
  | 'allBooks' | 'series' | 'authors';

export interface NavItem {
  id: string;
  label: string;
  icon?: string;
  iconType?: NavIconType;
  routerLink?: string[];
  type?: NavItemType;
  action?: () => void;
}

/** A clickable row inside a sidebar section. */
export interface SidebarLeaf extends NavItem {
  bookCount?: number;
  unhealthy?: boolean;
  contextMenuItems?: MenuItem[];
}

/** A heading that groups leaves in the sidebar. */
export interface SidebarSection {
  id: string;
  label: string;
  menuKey: string;
  type?: NavItemType;
  expandable?: boolean;
  items: SidebarLeaf[];
}
