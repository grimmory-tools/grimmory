import { ChangeDetectionStrategy, Component } from '@angular/core';

import { appMenuSectionClass } from './menu.styles';

@Component({
  selector: 'app-menu-section',
  standalone: true,
  host: { role: 'presentation', '[class]': 'sectionClass' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ng-content />`,
})
export class AppMenuSectionComponent {
  protected readonly sectionClass = appMenuSectionClass;
}
