import type { MenuItem } from 'primeng/api';

export type NavIconType = 'PRIME_NG' | 'CUSTOM_SVG';

export interface ContextMenuAction {
  label?: string;
  icon?: string;
  separator?: boolean;
  disabled?: boolean;
  action?: () => void;
  items?: ContextMenuAction[];
}

export type NavItemType =
  | 'library' | 'shelf' | 'magicShelf'
  | 'allBooks' | 'series' | 'authors';

export interface NavItem {
  label: string;
  icon?: string;
  iconType?: NavIconType;
  routerLink?: string[];
  type?: NavItemType;
  group?: boolean;
  bookCount?: number;
  unhealthy?: boolean;
  items?: NavItem[];
  hasDropDown?: boolean;
  hasCreate?: boolean;
  contextMenuActions?: ContextMenuAction[];
}

/**
 * Convert app-owned ContextMenuActions to PrimeNG MenuItem[] for use with p-menu.
 */
export function toMenuItems(actions: ContextMenuAction[] | undefined): MenuItem[] {
  return (actions ?? []).map((action) => ({
    label: action.label,
    icon: action.icon,
    separator: action.separator,
    disabled: action.disabled,
    command: action.action ? () => action.action?.() : undefined,
    items: action.items ? toMenuItems(action.items) : undefined,
  }));
}
