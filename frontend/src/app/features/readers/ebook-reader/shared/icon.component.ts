import {ChangeDetectionStrategy, Component, Input} from '@angular/core';

export type ReaderIconName =
  | 'menu'
  | 'close'
  | 'chevron-left'
  | 'chevron-right'
  | 'chevron-down'
  | 'chevron-first'
  | 'chevron-last'
  | 'chevron-double-left'
  | 'chevron-double-right'
  | 'dots-vertical'
  | 'bookmark'
  | 'settings'
  | 'book'
  | 'edit'
  | 'copy'
  | 'trash'
  | 'search'
  | 'note'
  | 'plus'
  | 'minus'
  | 'fit-page'
  | 'fit-width'
  | 'fit-height'
  | 'actual-size'
  | 'auto-fit'
  | 'fullscreen'
  | 'fullscreen-exit'
  | 'play'
  | 'pause'
  | 'help'
  | 'long-strip'
  | 'direction-ltr'
  | 'direction-rtl'
  | 'magnifier'
  | 'link'
  | 'sidebar'
  | 'highlight'
  | 'free-text'
  | 'draw'
  | 'pan'
  | 'spread-none'
  | 'spread-double'
  | 'scroll-horizontal'
  | 'scroll-vertical'
  | 'rotate'
  | 'sun'
  | 'moon'
  | 'dots-horizontal'
  | 'zoom-in'
  | 'zoom-out';

interface IconPath {
  d: string;
  type?: 'path' | 'polyline' | 'line' | 'rect' | 'circle';
}

const ICONS: Record<ReaderIconName, IconPath[]> = {
  // Navigation icons
  'menu': [
    {d: 'M3,6 L21,6', type: 'line'},
    {d: 'M3,12 L21,12', type: 'line'},
    {d: 'M3,18 L21,18', type: 'line'}
  ],
  'close': [
    {d: 'M18,6 L6,18', type: 'line'},
    {d: 'M6,6 L18,18', type: 'line'}
  ],
  'chevron-left': [
    {d: '15,18 9,12 15,6', type: 'polyline'}
  ],
  'chevron-right': [
    {d: '9,18 15,12 9,6', type: 'polyline'}
  ],
  'chevron-down': [
    {d: '6,9 12,15 18,9', type: 'polyline'}
  ],
  'chevron-first': [
    {d: '11,17 6,12 11,7', type: 'polyline'},
    {d: '18,17 13,12 18,7', type: 'polyline'}
  ],
  'chevron-last': [
    {d: '13,17 18,12 13,7', type: 'polyline'},
    {d: '6,17 11,12 6,7', type: 'polyline'}
  ],
  'chevron-double-left': [
    {d: '11,17 6,12 11,7', type: 'polyline'},
    {d: '18,17 13,12 18,7', type: 'polyline'}
  ],
  'chevron-double-right': [
    {d: '13,17 18,12 13,7', type: 'polyline'},
    {d: '6,17 11,12 6,7', type: 'polyline'}
  ],
  'dots-vertical': [
    {d: 'M12,12 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0', type: 'path'},
    {d: 'M12,5 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0', type: 'path'},
    {d: 'M12,19 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0', type: 'path'}
  ],

  // Action icons
  'bookmark': [
    {d: 'M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z'}
  ],
  'settings': [
    {d: 'M12 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0-6 0', type: 'path'},
    {d: 'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z'}
  ],
  'book': [
    {d: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20'},
    {d: 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'}
  ],
  'edit': [
    {d: 'M12 20h9'},
    {d: 'M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z'}
  ],
  'copy': [
    {d: 'M9,9 L22,9 L22,22 L9,22 Z M9,9 Q9,9 9,9', type: 'path'},
    {d: 'M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1'}
  ],
  'trash': [
    {d: 'M3 6h18'},
    {d: 'M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6'},
    {d: 'M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2'}
  ],
  'search': [
    {d: 'M11 11m-8 0a8 8 0 1 0 16 0a8 8 0 1 0-16 0', type: 'path'},
    {d: 'M21 21l-4.35-4.35'}
  ],
  'note': [
    {d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'},
    {d: '14,2 14,8 20,8', type: 'polyline'},
    {d: 'M16 13H8'},
    {d: 'M16 17H8'},
    {d: 'M10 9H8'}
  ],
  'plus': [
    {d: 'M12,5 L12,19', type: 'line'},
    {d: 'M5,12 L19,12', type: 'line'}
  ],
  'minus': [
    {d: 'M5,12 L19,12', type: 'line'}
  ],

  // Fit mode icons
  'fit-page': [
    {d: 'M3 3h18v18H3V3z'},
    {d: 'M7 7h10v10H7V7z'}
  ],
  'fit-width': [
    {d: 'M21,12 L17,8', type: 'line'},
    {d: 'M21,12 L17,16', type: 'line'},
    {d: 'M3,12 L7,8', type: 'line'},
    {d: 'M3,12 L7,16', type: 'line'},
    {d: 'M3,12 L21,12', type: 'line'}
  ],
  'fit-height': [
    {d: 'M12,3 L8,7', type: 'line'},
    {d: 'M12,3 L16,7', type: 'line'},
    {d: 'M12,21 L8,17', type: 'line'},
    {d: 'M12,21 L16,17', type: 'line'},
    {d: 'M12,3 L12,21', type: 'line'}
  ],
  'actual-size': [
    {d: 'M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z'},
    {d: 'M9,8 L9,16', type: 'line'},
    {d: 'M12,12 L12,16', type: 'line'},
    {d: 'M15,8 L15,16', type: 'line'}
  ],
  'auto-fit': [
    {d: 'M12 3v3m0 12v3M3 12h3m12 0h3'},
    {d: 'M12 12m-4 0a4 4 0 1 0 8 0a4 4 0 1 0-8 0', type: 'path'}
  ],
  'fullscreen': [
    {d: 'M8 3H5a2 2 0 0 0-2 2v3'},
    {d: 'M21 8V5a2 2 0 0 0-2-2h-3'},
    {d: 'M3 16v3a2 2 0 0 0 2 2h3'},
    {d: 'M16 21h3a2 2 0 0 0 2-2v-3'}
  ],
  'fullscreen-exit': [
    {d: 'M8 3v3a2 2 0 0 1-2 2H3'},
    {d: 'M21 8h-3a2 2 0 0 1-2-2V3'},
    {d: 'M3 16h3a2 2 0 0 1 2 2v3'},
    {d: 'M16 21v-3a2 2 0 0 1 2-2h3'}
  ],
  'play': [
    {d: 'M5 3l14 9-14 9V3z'}
  ],
  'pause': [
    {d: 'M6,4 L6,20', type: 'line'},
    {d: 'M18,4 L18,20', type: 'line'}
  ],
  'help': [
    {d: 'M12 12m-10 0a10 10 0 1 0 20 0a10 10 0 1 0-20 0', type: 'path'},
    {d: 'M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3'},
    {d: 'M12 17h.01'}
  ],
  'long-strip': [
    {d: 'M4 3h16v6H4z'},
    {d: 'M4 9h16v6H4z'},
    {d: 'M4 15h16v6H4z'}
  ],
  'direction-ltr': [
    {d: 'M5,12 L19,12', type: 'line'},
    {d: 'M15,8 L19,12 L15,16', type: 'polyline'}
  ],
  'direction-rtl': [
    {d: 'M19,12 L5,12', type: 'line'},
    {d: 'M9,8 L5,12 L9,16', type: 'polyline'}
  ],
  'magnifier': [
    {d: 'M10 10m-7 0a7 7 0 1 0 14 0a7 7 0 1 0-14 0', type: 'path'},
    {d: 'M21 21l-5-5'},
    {d: 'M7,10 L13,10', type: 'line'},
    {d: 'M10,7 L10,13', type: 'line'}
  ],
  'link': [
    {d: 'M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71'},
    {d: 'M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71'}
  ],
  'sidebar': [
    {d: 'M3 3h18v18H3z'},
    {d: 'M9 3v18'}
  ],
  'highlight': [
    {d: 'm9 11-6 6v3h9l3-3'},
    {d: 'm22 12-4.6 4.6a2 2 0 0 1-2.8 0l-5.2-5.2a2 2 0 0 1 0-2.8L14 4'}
  ],
  'free-text': [
    {d: 'M17 6.1H3'},
    {d: 'M21 12.1H3'},
    {d: 'M15.1 18H3'}
  ],
  'draw': [
    {d: 'M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z'}
  ],
  'pan': [
    {d: 'M18 11V6a2 2 0 0 0-2-2v0a2 2 0 0 0-2 2v0'},
    {d: 'M14 10V4a2 2 0 0 0-2-2v0a2 2 0 0 0-2 2v10'},
    {d: 'M10 10.5V6a2 2 0 0 0-2-2v0a2 2 0 0 0-2 2v8'},
    {d: 'M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.8-6.6-3.3l-4.7-5.3a2 2 0 0 1 0-2.8l.3-.3a2 2 0 0 1 2.8 0L8 14'}
  ],
  'spread-none': [
    {d: 'M3 3h18v18H3z'}
  ],
  'spread-double': [
    {d: 'M2 3h9v18H2z'},
    {d: 'M13 3h9v18H13z'}
  ],
  'scroll-horizontal': [
    {d: 'M4 12h16'},
    {d: 'm16 8 4 4-4 4'},
    {d: 'M2 3h3v18H2z'}
  ],
  'scroll-vertical': [
    {d: 'M3 2h18v9H3z'},
    {d: 'M3 13h18v9H3z'}
  ],
  'rotate': [
    {d: 'M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8'},
    {d: '21,3 21,8 16,8', type: 'polyline'}
  ],
  'sun': [
    {d: 'M12 12m-4 0a4 4 0 1 0 8 0a4 4 0 1 0-8 0', type: 'path'},
    {d: 'M12 2v2'},
    {d: 'M12 20v2'},
    {d: 'm4.93 4.93 1.41 1.41'},
    {d: 'm17.66 17.66 1.41 1.41'},
    {d: 'M2 12h2'},
    {d: 'M20 12h2'},
    {d: 'm6.34 17.66-1.41 1.41'},
    {d: 'm19.07 4.93-1.41 1.41'}
  ],
  'moon': [
    {d: 'M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z'}
  ],
  'dots-horizontal': [
    {d: 'M12,12 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0', type: 'path'},
    {d: 'M19,12 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0', type: 'path'},
    {d: 'M5,12 m-1,0 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0', type: 'path'}
  ],
  'zoom-in': [
    {d: 'M11 11m-8 0a8 8 0 1 0 16 0a8 8 0 1 0-16 0', type: 'path'},
    {d: 'M21 21l-4.35-4.35'},
    {d: 'M11 8v6'},
    {d: 'M8 11h6'}
  ],
  'zoom-out': [
    {d: 'M11 11m-8 0a8 8 0 1 0 16 0a8 8 0 1 0-16 0', type: 'path'},
    {d: 'M21 21l-4.35-4.35'},
    {d: 'M8 11h6'}
  ]
};

@Component({
  selector: 'app-reader-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  template: `
    <svg
      [attr.width]="size"
      [attr.height]="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth"
      stroke-linecap="round"
      stroke-linejoin="round"
      [class]="className">
      @for (path of paths; track $index) {
        @switch (path.type) {
          @case ('line') {
            <line
              [attr.x1]="getLineCoords(path.d, 0)"
              [attr.y1]="getLineCoords(path.d, 1)"
              [attr.x2]="getLineCoords(path.d, 2)"
              [attr.y2]="getLineCoords(path.d, 3)"/>
          }
          @case ('polyline') {
            <polyline [attr.points]="path.d"/>
          }
          @default {
            <path [attr.d]="path.d"/>
          }
        }
      }
    </svg>
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    svg {
      display: block;
    }
  `]
})
export class ReaderIconComponent {
  @Input() name!: ReaderIconName;
  @Input() size: number | string = 18;
  @Input() strokeWidth: number | string = 2;
  @Input() className = '';

  get paths(): IconPath[] {
    return ICONS[this.name] || [];
  }

  getLineCoords(d: string, index: number): string {
    const coords = d.replace(/[ML]/g, ' ').trim().split(/[\s,]+/);
    return coords[index] || '0';
  }
}
