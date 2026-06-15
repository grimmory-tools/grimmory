import { type ConnectedPosition } from '@angular/cdk/overlay';

export type AppTooltipSide = 'top' | 'bottom' | 'left' | 'right';

const GAP = 8;

const SIDE_POSITION: Record<AppTooltipSide, ConnectedPosition> = {
  top: { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -GAP },
  bottom: { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: GAP },
  left: { originX: 'start', originY: 'center', overlayX: 'end', overlayY: 'center', offsetX: -GAP },
  right: { originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center', offsetX: GAP },
};

const FALLBACKS: Record<AppTooltipSide, AppTooltipSide[]> = {
  top: ['top', 'bottom', 'right', 'left'],
  bottom: ['bottom', 'top', 'right', 'left'],
  left: ['left', 'right', 'top', 'bottom'],
  right: ['right', 'left', 'bottom', 'top'],
};

export function tooltipPositions(preferred: AppTooltipSide): ConnectedPosition[] {
  return FALLBACKS[preferred].map((side) => SIDE_POSITION[side]);
}
