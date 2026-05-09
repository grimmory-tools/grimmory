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
  template: `
    <ng-template
      cdkConnectedOverlay
      [cdkConnectedOverlayOrigin]="origin()"
      [cdkConnectedOverlayOpen]="open()"
      [cdkConnectedOverlayPositions]="positions()"
      [cdkConnectedOverlayViewportMargin]="8"
      [cdkConnectedOverlayPush]="true"
      [cdkConnectedOverlayFlexibleDimensions]="true"
    >
      <div ngMenu #inner="ngMenu" class="menu-panel" (itemSelected)="onSelected($event)">
        <ng-template ngMenuContent>
          @for (entry of items(); track $index) {
            @if (isSeparator(entry)) {
              <div role="separator" class="menu-separator"></div>
            } @else if (entry.children?.length) {
              <button
                ngMenuItem
                #aria="ngMenuItem"
                #subTrigger
                type="button"
                class="menu-item"
                [value]="entry"
                [submenu]="subMenu.menuRef()"
                [disabled]="entry.disabled ?? false"
              >
                @if (entry.icon) { <i [class]="entry.icon" aria-hidden="true"></i> }
                <span>{{ entry.label }}</span>
                <i class="pi pi-chevron-right menu-chevron" aria-hidden="true"></i>
              </button>
              <app-menu
                #subMenu="appMenu"
                [origin]="subTrigger"
                [open]="aria.expanded() ?? false"
                [items]="entry.children ?? []"
                [positions]="submenuPositions"
              ></app-menu>
            } @else {
              <button
                ngMenuItem
                type="button"
                class="menu-item"
                [value]="entry"
                [disabled]="entry.disabled ?? false"
              >
                @if (entry.icon) { <i [class]="entry.icon" aria-hidden="true"></i> }
                <span>{{ entry.label }}</span>
              </button>
            }
          }
        </ng-template>
      </div>
    </ng-template>
  `,
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
