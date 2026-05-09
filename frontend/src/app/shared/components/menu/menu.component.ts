import { ChangeDetectionStrategy, Component, ElementRef, inject, input, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { CdkConnectedOverlay, ConnectedPosition } from '@angular/cdk/overlay';
import { Menu as AriaMenu, MenuContent, MenuItem as AriaMenuItem } from '@angular/aria/menu';
import { MenuEntry, MenuItem, isMenuSeparator } from './menu-item.model';
import { SUBMENU_RIGHT } from './menu-positions';

type OriginInput = HTMLElement | ElementRef;

@Component({
  selector: 'app-menu',
  exportAs: 'appMenu',
  imports: [CdkConnectedOverlay, AriaMenu, AriaMenuItem, MenuContent],
  styles: `:host { display: contents; }`,
  templateUrl: './menu.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MenuComponent {
  protected readonly submenuPositions = SUBMENU_RIGHT;

  readonly origin = input.required<OriginInput>();
  readonly open = input.required<boolean>();
  readonly items = input.required<readonly MenuEntry[]>();
  readonly positions = input.required<ConnectedPosition[]>();

  readonly menuRef = viewChild<AriaMenu<MenuItem>>('inner');

  private readonly router = inject(Router);

  protected readonly isSeparator = isMenuSeparator;

  protected onSelected(item: MenuItem): void {
    if (item.routerLink) {
      this.router.navigate(item.routerLink as readonly unknown[]);
      return;
    }
    item.action?.();
  }
}
