import { cn } from '../cn';
import {
  overlayListActiveItemStateClass,
  overlayListItemContentClass,
  overlayListItemRowClass,
  overlayListRootClass,
  overlayListSectionLabelClass,
  overlayListSeparatorClass,
  overlayListShortcutClass,
  overlayListSurfaceClass,
} from '../overlay-list.styles';

const ptClass = (className: string) => ({ class: className });
export const APP_MENU_SECTION_CLASS = 'app-menu-section-label';

type AppMenuItemVariant = 'destructive';

interface MenuPtOptions {
  context?: MenuPtContext;
}

interface MenuPtContext {
  item?: {
    styleClass?: string;
    appMenuSection?: boolean;
    appMenuVariant?: AppMenuItemVariant;
  };
  active?: boolean;
  focused?: boolean;
  disabled?: boolean;
}

const destructiveItemStateClass =
  'text-danger hover:bg-danger/10 hover:text-danger dark:hover:bg-danger/20';
const destructiveActiveItemStateClass = 'bg-danger/10 text-danger dark:bg-danger/20';
const menuSurfaceClass = cn('min-w-[8rem]', overlayListSurfaceClass);
const menuItemLinkClass = cn(overlayListItemRowClass, 'cursor-pointer');

export const hasMenuStyleClass = (styleClass: string | undefined, className: string): boolean =>
  (styleClass ?? '').split(/\s+/).includes(className);

const hasItemClass = (context: MenuPtContext | undefined, className: string): boolean =>
  hasMenuStyleClass(context?.item?.styleClass, className);

const isDestructiveItem = (context: MenuPtContext | undefined): boolean =>
  context?.item?.appMenuVariant === 'destructive' ||
  hasItemClass(context, 'destructive') ||
  hasItemClass(context, 'app-menu-item-destructive');

const isSectionLabel = (context: MenuPtContext | undefined): boolean =>
  context?.item?.appMenuSection === true || hasItemClass(context, APP_MENU_SECTION_CLASS);

const isActiveItem = (context: MenuPtContext | undefined): boolean =>
  context?.active === true || context?.focused === true;

export const menuPanelClass = menuSurfaceClass;

export const menuPassThrough = {
  rootList: ptClass(overlayListRootClass),
  item: ({ context }: MenuPtOptions) =>
    ptClass(cn(
      'relative',
      context?.disabled && 'pointer-events-none',
      context?.disabled && !isSectionLabel(context) && 'opacity-50',
    )),
  itemContent: ({ context }: MenuPtOptions) => {
    if (isSectionLabel(context)) {
      return ptClass('text-text-muted');
    }

    const destructive = isDestructiveItem(context);
    return ptClass(cn(
      overlayListItemContentClass,
      destructive && destructiveItemStateClass,
      isActiveItem(context) && (destructive ? destructiveActiveItemStateClass : overlayListActiveItemStateClass),
    ));
  },
  itemLink: ({ context }: MenuPtOptions) =>
    ptClass(isSectionLabel(context) ? overlayListSectionLabelClass : menuItemLinkClass),
  itemIcon: ({ context }: MenuPtOptions) =>
    ptClass(cn(
      'flex size-4 shrink-0 items-center justify-center text-base text-text-muted',
      isDestructiveItem(context) && 'text-danger',
    )),
  itemLabel: ptClass('truncate leading-5'),
  itemBadge: ptClass(cn(overlayListShortcutClass, 'leading-5')),
  submenu: ptClass(cn('absolute z-50 m-0 list-none flex-col', menuSurfaceClass)),
  submenuIcon: ptClass('ml-auto size-4 shrink-0 text-text-muted'),
  separator: ptClass(overlayListSeparatorClass),
} as const;
