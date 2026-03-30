import {Component, ElementRef, inject} from '@angular/core';
import { LayoutService } from "../layout-main/service/app.layout.service";
import {AppMenuComponent} from '../layout-menu/app.menu.component';

@Component({
  selector: 'app-sidebar',
  imports: [
    AppMenuComponent
  ],
  templateUrl: './app.sidebar.component.html'
})
export class AppSidebarComponent {
  public layoutService = inject(LayoutService);
  public el = inject(ElementRef);
}
