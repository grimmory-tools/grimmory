import { ChangeDetectionStrategy, Component, HostBinding, computed, inject, input } from '@angular/core';
import { NgClass } from '@angular/common';

import { LayoutService } from '../layout.service';
import { AppMenuItemRowComponent } from './app.menu-item-row.component';
import { SidebarLeaf, SidebarSection } from '../navigation/nav-item.model';

@Component({
  // Attribute selector so the section renders into its host <li> inside a <ul>.
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: '[app-menu-section]',
  templateUrl: './app.menu-section.component.html',
  styleUrls: ['./app.menu-section.component.scss'],
  imports: [NgClass, AppMenuItemRowComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppMenuSectionComponent {
  readonly item = input.required<SidebarSection>();

  // Applied on the <li> host so global styles in assets/layout/styles/layout/_menu.scss
  // (e.g. `.layout-root-menuitem > .layout-menuitem-root-text`) target section headings.
  @HostBinding('class.layout-root-menuitem') readonly isRoot = true;

  readonly key = computed(() => this.item().menuKey);

  readonly layoutService = inject(LayoutService);

  get isExpandable(): boolean {
    const item = this.item();
    return !!item.expandable && item.items.length > 0;
  }

  get expanded(): boolean {
    if (!this.isExpandable) return true;
    return this.layoutService.isSidebarExpanded(this.key(), true);
  }

  get submenuVisible(): boolean {
    return this.layoutService.sidebarCollapsed() || this.expanded;
  }

  toggleExpand(): void {
    if (!this.isExpandable) return;
    this.layoutService.setSidebarExpanded(this.key(), !this.expanded);
  }

  visibleChildren(): SidebarLeaf[] {
    return this.item().items;
  }
}
