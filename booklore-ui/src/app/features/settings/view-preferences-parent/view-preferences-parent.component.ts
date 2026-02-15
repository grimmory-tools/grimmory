import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {ToastModule} from 'primeng/toast';
import {ViewPreferencesComponent} from './view-preferences/view-preferences.component';
import {SidebarSortingPreferencesComponent} from './sidebar-sorting-preferences/sidebar-sorting-preferences.component';
import {MetaCenterViewModeComponent} from './meta-center-view-mode/meta-center-view-mode-component';
import {FilterPreferencesComponent} from './filter-preferences/filter-preferences.component';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Slider} from 'primeng/slider';
import {MessageService} from 'primeng/api';
import {LocalStorageService} from '../../../shared/service/local-storage.service';

@Component({
  selector: 'app-view-preferences-parent',
  standalone: true,
  imports: [
    FormsModule,
    TableModule,
    ToastModule,
    ViewPreferencesComponent,
    SidebarSortingPreferencesComponent,
    MetaCenterViewModeComponent,
    FilterPreferencesComponent,
    TranslocoDirective,
    Slider,
  ],
  templateUrl: './view-preferences-parent.component.html',
  styleUrl: './view-preferences-parent.component.scss'
})
export class ViewPreferencesParentComponent implements OnInit {

  sidebarWidth = 225;

  private localStorageService = inject(LocalStorageService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);

  ngOnInit(): void {
    this.sidebarWidth = this.localStorageService.get<number>('sidebarWidth') ?? 225;
  }

  onSidebarWidthChange(): void {
    document.documentElement.style.setProperty('--sidebar-width', this.sidebarWidth + 'px');
  }

  saveSidebarWidth(): void {
    this.localStorageService.set('sidebarWidth', this.sidebarWidth);
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.layout.saved'),
      detail: this.t.translate('settingsView.layout.savedDetail')
    });
  }
}
