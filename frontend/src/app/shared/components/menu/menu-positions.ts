import type { ConnectedPosition } from '@angular/cdk/overlay';

export const ABOVE_ALIGN_LEFT: ConnectedPosition[] = [
  { originX: 'start', originY: 'top',    overlayX: 'start', overlayY: 'bottom', offsetY: -8 },
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top',    offsetY: 8  },
];

export const BELOW_ALIGN_LEFT: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top',    offsetY: 8  },
  { originX: 'start', originY: 'top',    overlayX: 'start', overlayY: 'bottom', offsetY: -8 },
];

export const BESIDE_RIGHT_BOTTOM: ConnectedPosition[] = [
  { originX: 'end', originY: 'bottom', overlayX: 'start', overlayY: 'bottom', offsetX: 8 },
];

export const SUBMENU_RIGHT: ConnectedPosition[] = [
  { originX: 'end',   originY: 'top', overlayX: 'start', overlayY: 'top', offsetX: 0 },
  { originX: 'start', originY: 'top', overlayX: 'end',   overlayY: 'top', offsetX: 0 },
];
