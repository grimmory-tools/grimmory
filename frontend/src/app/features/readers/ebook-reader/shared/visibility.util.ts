export interface HeaderFooterVisibilityState {
  headerVisible: boolean;
  footerVisible: boolean;
}

export class ReaderHeaderFooterVisibilityManager {
  private isPinned = false;
  private isImmersive = false;
  private mouseY: number;

  private readonly HEADER_TRIGGER_ZONE = 20;
  private readonly FOOTER_TRIGGER_ZONE = 30;

  private headerVisible = false;
  private footerVisible = false;

  private headerHovered = false;
  private footerHovered = false;

  private onStateChangeCallback?: (state: HeaderFooterVisibilityState) => void;

  constructor(private windowHeight: number) {
    this.mouseY = windowHeight / 2;
  }

  onStateChange(callback: (state: HeaderFooterVisibilityState) => void): void {
    this.onStateChangeCallback = callback;
  }

  updateWindowHeight(height: number): void {
    this.windowHeight = height;
  }

  handleMouseMove(mouseY: number): void {
    this.mouseY = mouseY;
    if (!this.isImmersive) {
      this.updateVisibility();
    }
  }

  handleMouseLeave(): void {
    this.headerHovered = false;
    this.footerHovered = false;
    if (!this.isPinned && !this.isImmersive) {
      this.setHeaderVisible(false);
      this.setFooterVisible(false);
      this.notifyStateChange();
    }
  }

  handleHeaderZoneEnter(): void {
    if (!this.isPinned && !this.isImmersive) {
      this.setHeaderVisible(true);
      this.notifyStateChange();
    }
  }

  handleFooterZoneEnter(): void {
    if (!this.isPinned && !this.isImmersive) {
      this.setFooterVisible(true);
      this.notifyStateChange();
    }
  }

  setHeaderHovered(hovered: boolean): void {
    this.headerHovered = hovered;
    if (!this.isImmersive) {
      this.updateVisibility();
    }
  }

  setFooterHovered(hovered: boolean): void {
    this.footerHovered = hovered;
    if (!this.isImmersive) {
      this.updateVisibility();
    }
  }

  togglePinned(): void {
    this.isPinned = !this.isPinned;
    this.updateVisibility();
  }

  unpinIfPinned(): void {
    if (this.isPinned) {
      this.isPinned = false;
      this.updateVisibility();
    }
  }

  setImmersive(immersive: boolean): void {
    this.isImmersive = immersive;
    if (immersive) {
      this.isPinned = false;
      this.headerHovered = false;
      this.footerHovered = false;
      this.setHeaderVisible(false);
      this.setFooterVisible(false);
      this.notifyStateChange();
    }
  }

  temporaryShow(): void {
    this.setHeaderVisible(true);
    this.setFooterVisible(true);
    this.notifyStateChange();
  }

  hideTemporary(): void {
    if (this.isImmersive) {
      this.setHeaderVisible(false);
      this.setFooterVisible(false);
      this.notifyStateChange();
    }
  }

  getVisibilityState(): HeaderFooterVisibilityState {
    return {
      headerVisible: this.headerVisible,
      footerVisible: this.footerVisible
    };
  }

  private updateVisibility(): void {
    if (
      this.mouseY <= this.HEADER_TRIGGER_ZONE ||
      this.headerHovered ||
      this.isPinned
    ) {
      this.setHeaderVisible(true);
    } else {
      this.setHeaderVisible(this.isPinned);
    }

    if (
      this.mouseY >= this.windowHeight - this.FOOTER_TRIGGER_ZONE ||
      this.footerHovered ||
      this.isPinned
    ) {
      this.setFooterVisible(true);
    } else {
      this.setFooterVisible(this.isPinned);
    }

    this.notifyStateChange();
  }

  private setHeaderVisible(visible: boolean): void {
    this.headerVisible = visible;
  }

  private setFooterVisible(visible: boolean): void {
    this.footerVisible = visible;
  }

  private notifyStateChange(): void {
    if (this.onStateChangeCallback) {
      this.onStateChangeCallback(this.getVisibilityState());
    }
  }
}
