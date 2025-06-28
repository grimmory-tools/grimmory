import {Component} from '@angular/core';
import {Divider} from 'primeng/divider';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {ToastModule} from 'primeng/toast';
import {ViewPreferencesComponent} from './view-preferences/view-preferences.component';
import {SidebarSortingPreferencesComponent} from './sidebar-sorting-preferences/sidebar-sorting-preferences.component';
import {MetaCenterViewModeComponent} from './meta-center-view-mode-component/meta-center-view-mode-component';

@Component({
  selector: 'app-view-preferences-parent',
  standalone: true,
  imports: [
    Divider,
    FormsModule,
    TableModule,
    ToastModule,
    ViewPreferencesComponent,
    SidebarSortingPreferencesComponent,
    MetaCenterViewModeComponent
  ],
  templateUrl: './view-preferences-parent.component.html',
  styleUrl: './view-preferences-parent.component.scss'
})
export class ViewPreferencesParentComponent {

}
