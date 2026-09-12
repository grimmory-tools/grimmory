import { IconType } from '../../icons/icon-selection';
import type {LibraryShelfMenuTarget} from './library-shelf-menu-target.model';

export type NavItemType =
  | 'library' | 'shelf' | 'magicShelf'
  | 'allBooks' | 'series' | 'authors';

export interface NavItem {
  id: string;
  label: string;
  icon?: string;
  iconType?: IconType;
  routerLink?: string[];
  type?: NavItemType;
  action?: () => void;
}

/** A clickable row inside a sidebar section. */
export interface SidebarLeaf extends NavItem {
  menuTarget?: LibraryShelfMenuTarget;
  bookCount?: number;
  unhealthy?: boolean;
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
