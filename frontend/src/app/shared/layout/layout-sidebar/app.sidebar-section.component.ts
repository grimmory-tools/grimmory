import { ChangeDetectionStrategy, Component, HostBinding, computed, inject, input } from '@angular/core';

import { LayoutService } from '../layout.service';
import { AppSidebarItemRowComponent } from './app.sidebar-item-row.component';
import { SidebarLeaf, SidebarSection } from '../navigation/nav-item.model';
import { LucideChevronDown, LucideChevronUp } from '@lucide/angular';

@Component({
  // Attribute selector so the section renders into its host <li> inside a <ul>.
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: '[appSidebarSection]',
  templateUrl: './app.sidebar-section.component.html',
  styleUrls: ['./app.sidebar-section.component.scss'],
  imports: [LucideChevronDown, LucideChevronUp, AppSidebarItemRowComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppSidebarSectionComponent {
  readonly item = input.required<SidebarSection>();

  // Applied on the <li> host so global styles in assets/layout/styles/layout/_sidebar.scss
  // (e.g. `.layout-sidebar-root-item > .layout-sidebar-root-text`) target section headings.
  @HostBinding('class.layout-sidebar-root-item') readonly isRoot = true;

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
    return (this.layoutService.isDesktop() && this.layoutService.sidebarCollapsed()) || this.expanded;
  }

  toggleExpand(): void {
    if (!this.isExpandable) return;
    this.layoutService.setSidebarExpanded(this.key(), !this.expanded);
  }

  visibleChildren(): SidebarLeaf[] {
    return this.item().items;
  }
}
