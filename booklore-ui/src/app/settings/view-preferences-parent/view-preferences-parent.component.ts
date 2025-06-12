import {Component} from '@angular/core';
import {Divider} from 'primeng/divider';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {ToastModule} from 'primeng/toast';
import {ViewPreferencesComponent} from './view-preferences/view-preferences.component';
import {SidebarSortingPreferencesComponent} from './sidebar-sorting-preferences/sidebar-sorting-preferences.component';
import {SidebarFilterSortingPrefsComponent} from './sidebar-filter-sorting-prefs-component/sidebar-filter-sorting-prefs-component';

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
    SidebarFilterSortingPrefsComponent
  ],
  templateUrl: './view-preferences-parent.component.html',
  styleUrl: './view-preferences-parent.component.scss'
})
export class ViewPreferencesParentComponent {

}
