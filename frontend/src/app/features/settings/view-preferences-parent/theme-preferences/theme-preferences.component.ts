import {Component} from '@angular/core';
import {TranslocoDirective} from '@jsverse/transloco';
import {ThemeConfiguratorComponent} from '../../../../shared/layout/theme-configurator/theme-configurator.component';

@Component({
  selector: 'app-theme-preferences',
  standalone: true,
  imports: [TranslocoDirective, ThemeConfiguratorComponent],
  templateUrl: './theme-preferences.component.html',
  styleUrl: './theme-preferences.component.scss',
})
export class ThemePreferencesComponent {}
