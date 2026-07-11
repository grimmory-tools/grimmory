import {Component, inject, Input, OnInit, OnChanges, SimpleChanges, ChangeDetectionStrategy, ChangeDetectorRef, DestroyRef} from '@angular/core';
import {IconSelection} from '../../icons/icon-selection';
import {NgClass, NgStyle} from '@angular/common';
import {CustomSvgCacheService} from '../../services/custom-svg-cache.service';
import {CustomSvgService} from '../../services/custom-svg.service';
import {AppIconDirective} from '../icon/app-icon.directive';
import {SvgContentDirective} from '../icon/svg-content.directive';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';

const ERROR_SVG = [
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="red">',
  '<circle cx="12" cy="12" r="10"/>',
  '<line x1="15" y1="9" x2="9" y2="15"/>',
  '<line x1="9" y1="9" x2="15" y2="15"/>',
  '</svg>',
].join('');

@Component({
  selector: 'app-icon-display',
  standalone: true,
  imports: [NgClass, NgStyle, AppIconDirective, SvgContentDirective],
  template: `
    @if (icon) {
      @if (icon.type === 'CUSTOM_SVG') {
        <div
          class="svg-icon-inline"
          [appSvgContent]="getSvgContent(icon.value)"
          [ngClass]="iconClass"
          [ngStyle]="getSvgStyle()"
        ></div>
      } @else {
        <svg
          [appIcon]="icon.value"
          [ngClass]="iconClass"
          [ngStyle]="getSvgStyle()"
        ></svg>
      }
    } @else if (displayEmpty) {
      <div
          [ngStyle]="getEmptyIconStyle()"
      ></div>
    }
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: var(--icon-display-color, currentColor);
    }

    .svg-icon-inline {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: inherit;
    }

    .svg-icon-inline ::ng-deep svg {
      width: 100%;
      height: 100%;
      display: block;
    }

    /* Tint only paths without explicit paint so user-uploaded multi-colour SVGs survive. */
    .svg-icon-inline ::ng-deep svg:not([fill]),
    .svg-icon-inline ::ng-deep path:not([fill]) {
      fill: currentColor;
    }

    .svg-icon-inline ::ng-deep path[stroke]:not([stroke="none"]) {
      stroke: currentColor;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IconDisplayComponent implements OnInit, OnChanges {
  @Input() icon: IconSelection | null = null;
  @Input() iconClass: string = 'icon';
  @Input() iconStyle: Record<string, string> = {};
  @Input() size: string = '16px';
  @Input() alt: string = 'Icon';
  @Input() displayEmpty: boolean = false;

  private readonly customSvgCache = inject(CustomSvgCacheService);
  private readonly customSvgService = inject(CustomSvgService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private lastLoadedIconName: string | null = null;

  ngOnInit(): void {
    this.loadIconIfNeeded();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['icon']) {
      const currentIcon = changes['icon'].currentValue;
      const previousIcon = changes['icon'].previousValue;

      if (currentIcon?.type === 'CUSTOM_SVG' &&
        currentIcon?.value !== previousIcon?.value) {
        this.loadIconIfNeeded();
      }
    }
  }

  private loadIconIfNeeded(): void {
    if (this.icon?.type === 'CUSTOM_SVG' && this.icon.value !== this.lastLoadedIconName) {
      const requestedIconName = this.icon.value;
      this.lastLoadedIconName = requestedIconName;

      if (this.customSvgCache.getCachedSanitized(requestedIconName) === null) {
        this.customSvgService.getSanitizedSvgContent(requestedIconName)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => this.cdr.markForCheck(),
            error: () => {
              this.customSvgCache.cacheIcon(requestedIconName, ERROR_SVG, ERROR_SVG);
              this.cdr.markForCheck();
            }
          });
      }
    }
  }

  getSvgContent(iconName: string): string | null {
    return this.customSvgCache.getCachedSanitized(iconName);
  }

  getSvgStyle(): Record<string, string> {
    return {
      width: this.size,
      height: this.size,
      ...this.iconStyle
    };
  }

  getEmptyIconStyle(): Record<string, string> {
    return {
      width: this.size,
      height: this.size,
    };
  }

}
