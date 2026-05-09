export interface MenuItem {
  readonly label: string;
  readonly icon?: string;
  readonly routerLink?: readonly string[];
  readonly action?: () => void;
  readonly disabled?: boolean;
  readonly children?: readonly MenuEntry[];
}

export interface MenuSeparator {
  readonly separator: true;
}

export type MenuEntry = MenuItem | MenuSeparator;

export function isMenuSeparator(entry: MenuEntry): entry is MenuSeparator {
  return 'separator' in entry && entry.separator === true;
}
