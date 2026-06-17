import { booleanAttribute, ChangeDetectionStrategy, Component, computed, input, viewChild } from '@angular/core';
import { type MenuItem } from 'primeng/api';
import { ContextMenu } from 'primeng/contextmenu';
import { TieredMenu } from 'primeng/tieredmenu';
import { ptViewMerge } from '../cn';
import { type AppMenuSectionItem } from './app-menu.items';
import { APP_MENU_SECTION_CLASS, hasMenuStyleClass, menuPanelClass, menuPassThrough } from './menu.pt';

const isAppMenuSectionItem = (item: MenuItem): item is AppMenuSectionItem =>
  'appMenuSection' in item && item['appMenuSection'] === true;

const isSectionLabel = (item: MenuItem): boolean =>
  isAppMenuSectionItem(item) || hasMenuStyleClass(item.styleClass, APP_MENU_SECTION_CLASS);

const withStyleClass = (existing: string | undefined, styleClass: string): string => {
  if (!existing) return styleClass;
  return hasMenuStyleClass(existing, styleClass) ? existing : `${existing} ${styleClass}`;
};

const withoutIconFields = (item: MenuItem): MenuItem => {
  const { icon, iconClass, iconStyle, ...rest } = item;
  void icon;
  void iconClass;
  void iconStyle;
  return rest;
};

const withoutActionFields = (item: MenuItem): MenuItem => {
  const { command, routerLink, url, ...rest } = item;
  void command;
  void routerLink;
  void url;
  return rest;
};

const normalizeItem = (item: MenuItem, showItemIcons: boolean): MenuItem => {
  const itemWithChildren: MenuItem = {
    ...item,
    ...(item.items ? { items: normalizeItems(item.items, showItemIcons) } : {}),
  };
  const normalizedItem = showItemIcons ? itemWithChildren : withoutIconFields(itemWithChildren);

  if (isSectionLabel(normalizedItem)) {
    const sectionItem = withoutActionFields(normalizedItem);

    return {
      ...sectionItem,
      appMenuSection: true,
      disabled: true,
      styleClass: withStyleClass(sectionItem.styleClass, APP_MENU_SECTION_CLASS),
    };
  }

  return normalizedItem;
};

const normalizeItems = (items: readonly MenuItem[], showItemIcons: boolean): MenuItem[] =>
  items.map((item) => normalizeItem(item, showItemIcons));

@Component({
  selector: 'app-menu',
  standalone: true,
  imports: [TieredMenu, ContextMenu],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (contextTarget(); as target) {
      <p-contextMenu
        [unstyled]="true"
        [target]="target"
        [model]="primeModel()"
        [appendTo]="appendTo()"
        [styleClass]="panelClass"
        [pt]="pt"
        [ptOptions]="ptOptions" />
    } @else {
      <p-tieredMenu
        #menu
        [unstyled]="true"
        [popup]="true"
        [model]="primeModel()"
        [appendTo]="appendTo()"
        [styleClass]="panelClass"
        [pt]="pt"
        [ptOptions]="ptOptions" />
    }
  `,
})
export class AppMenuComponent {
  readonly model = input<readonly MenuItem[]>([]);
  readonly contextTarget = input<HTMLElement | string | undefined>(undefined);
  readonly appendTo = input<'body' | 'self' | HTMLElement>('body');
  readonly showItemIcons = input(false, { transform: booleanAttribute });

  private readonly menu = viewChild(TieredMenu);

  protected readonly pt = menuPassThrough;
  protected readonly ptOptions = { mergeProps: ptViewMerge };
  protected readonly panelClass = menuPanelClass;
  protected readonly primeModel = computed(() => normalizeItems(this.model(), this.showItemIcons()));

  toggle(event: Event): void {
    this.menu()?.toggle(event);
  }
}
