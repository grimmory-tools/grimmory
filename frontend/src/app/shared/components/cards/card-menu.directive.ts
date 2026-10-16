import {booleanAttribute, DestroyRef, Directive, ElementRef, inject, input, output} from '@angular/core';

import {contextMenuRequest, type ContextMenuRequest} from '../../ui/menu/app-menu.component';

const LONG_PRESS_MS = 500;
const LONG_PRESS_TOLERANCE_PX = 10;

@Directive({
  selector: '[appCardMenu]',
  host: {
    '(contextmenu)': 'requestContextMenu($event)',
    '(pointerdown)': 'startLongPress($event)',
    '(pointerup)': 'cancelLongPress()',
    '(pointercancel)': 'cancelLongPress()',
  },
})
export class CardMenuDirective {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  readonly enabled = input(true, {alias: 'appCardMenu', transform: booleanAttribute});
  readonly longPress = input(true, {alias: 'appCardMenuLongPress', transform: booleanAttribute});
  readonly cardMenuRequested = output<ContextMenuRequest>();

  private pressTimer: number | null = null;
  private pressOrigin: {x: number; y: number} | null = null;
  private longPressFired = false;
  private readonly onPressMove = (event: PointerEvent): void => this.cancelLongPressAfterMove(event);
  private readonly swallowClickAfterLongPress = (event: MouseEvent): void => {
    if (!this.longPressFired) {
      return;
    }
    this.longPressFired = false;
    event.preventDefault();
    event.stopPropagation();
  };

  constructor() {
    this.host.addEventListener('click', this.swallowClickAfterLongPress, true);
    inject(DestroyRef).onDestroy(() => {
      this.cancelLongPress();
      this.host.removeEventListener('click', this.swallowClickAfterLongPress, true);
    });
  }

  protected startLongPress(event: PointerEvent): void {
    this.cancelLongPress();
    this.longPressFired = false;
    if (event.pointerType === 'mouse' || !this.enabled() || !this.longPress()) {
      return;
    }
    const request = contextMenuRequest(event);
    this.pressOrigin = {x: event.clientX, y: event.clientY};
    this.host.addEventListener('pointermove', this.onPressMove, {passive: true});
    this.pressTimer = window.setTimeout(() => {
      this.pressTimer = null;
      this.longPressFired = true;
      this.cardMenuRequested.emit(request);
    }, LONG_PRESS_MS);
  }

  protected requestContextMenu(event: MouseEvent): void {
    this.cancelLongPress();
    if (!this.enabled()) {
      return;
    }
    event.preventDefault();
    if (this.longPressFired) {
      return;
    }
    this.cardMenuRequested.emit(contextMenuRequest(event));
  }

  protected cancelLongPress(): void {
    if (this.pressTimer !== null) {
      clearTimeout(this.pressTimer);
      this.pressTimer = null;
    }
    this.host.removeEventListener('pointermove', this.onPressMove);
    this.pressOrigin = null;
  }

  private cancelLongPressAfterMove(event: PointerEvent): void {
    const origin = this.pressOrigin;
    if (this.pressTimer === null || !origin) {
      return;
    }
    if (Math.hypot(event.clientX - origin.x, event.clientY - origin.y) > LONG_PRESS_TOLERANCE_PX) {
      this.cancelLongPress();
    }
  }
}
