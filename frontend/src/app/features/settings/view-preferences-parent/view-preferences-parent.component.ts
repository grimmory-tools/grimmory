import {Component, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';

import {ViewPreferencesComponent} from './view-preferences/view-preferences.component';
import {SidebarSortingPreferencesComponent} from './sidebar-sorting-preferences/sidebar-sorting-preferences.component';
import {MetaCenterViewModeComponent} from './meta-center-view-mode/meta-center-view-mode-component';
import {FilterPreferencesComponent} from './filter-preferences/filter-preferences.component';
import {LanguagePreferencesComponent} from './language-preferences/language-preferences.component';
import {ThemePreferencesComponent} from './theme-preferences/theme-preferences.component';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Slider} from 'primeng/slider';
import {MessageService} from 'primeng/api';
import {LayoutService, SIDEBAR_MAX_WIDTH, SIDEBAR_MIN_WIDTH} from '../../../shared/layout/layout.service';

@Component({
  selector: 'app-view-preferences-parent',
  standalone: true,
  imports: [
    FormsModule,
    TableModule,
    ViewPreferencesComponent,
    SidebarSortingPreferencesComponent,
    MetaCenterViewModeComponent,
    FilterPreferencesComponent,
    LanguagePreferencesComponent,
    ThemePreferencesComponent,
    TranslocoDirective,
    Slider,
  ],
  templateUrl: './view-preferences-parent.component.html',
  styleUrl: './view-preferences-parent.component.scss'
})
export class ViewPreferencesParentComponent {

  readonly SIDEBAR_MIN_WIDTH = SIDEBAR_MIN_WIDTH;
  readonly SIDEBAR_MAX_WIDTH = SIDEBAR_MAX_WIDTH;

  private layoutService = inject(LayoutService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);

  get sidebarWidth(): number {
    return this.layoutService.sidebarWidth();
  }
  set sidebarWidth(value: number) {
    this.layoutService.setSidebarWidth(value, false);
  }

  saveSidebarWidth(): void {
    this.layoutService.setSidebarWidth(this.layoutService.sidebarWidth(), true);
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.layout.saved'),
      detail: this.t.translate('settingsView.layout.savedDetail')
    });
  }
}
