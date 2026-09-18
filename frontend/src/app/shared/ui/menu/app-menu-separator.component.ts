import { ChangeDetectionStrategy, Component } from '@angular/core';

import { appMenuSeparatorClass } from './menu.styles';

@Component({
  selector: 'app-menu-separator',
  standalone: true,
  host: { role: 'separator', '[class]': 'separatorClass' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
})
export class AppMenuSeparatorComponent {
  protected readonly separatorClass = appMenuSeparatorClass;
}
