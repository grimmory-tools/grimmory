import {
  type CdkConnectedOverlay,
  type ConnectedPosition,
  type Overlay,
  type OverlayRef,
  type ScrollStrategy,
} from '@angular/cdk/overlay';

const CONNECTED_OVERLAY_GAP = 4;

const OVERLAY_ABOVE_CLASS = 'app-overlay-above';

export const connectedOverlayPanelClass =
  'origin-top will-change-transform animate-in fade-in-0 zoom-in-98 slide-in-from-top-1 ' +
  '[.app-overlay-above_&]:origin-bottom [.app-overlay-above_&]:slide-in-from-bottom-1';

export const connectedOverlayPositions: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: CONNECTED_OVERLAY_GAP },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -CONNECTED_OVERLAY_GAP, panelClass: OVERLAY_ABOVE_CLASS },
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: CONNECTED_OVERLAY_GAP },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -CONNECTED_OVERLAY_GAP, panelClass: OVERLAY_ABOVE_CLASS },
];

export function connectedOverlayScrollStrategy(overlay: Overlay): ScrollStrategy {
  return overlay.scrollStrategies.reposition({ scrollThrottle: 0 });
}

export function refreshConnectedOverlayPosition(overlay: CdkConnectedOverlay | undefined): void {
  refreshOverlayRefPosition(overlay?.overlayRef);
}

export function refreshOverlayRefPosition(overlayRef: OverlayRef | null | undefined): void {
  if (!overlayRef) return;

  if (overlayRef.hasAttached()) overlayRef.updatePosition();
  queueMicrotask(() => {
    if (overlayRef.hasAttached()) overlayRef.updatePosition();
  });
}
