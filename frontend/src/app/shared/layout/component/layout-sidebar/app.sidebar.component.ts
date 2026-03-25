import { Component, ElementRef } from '@angular/core';
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
    constructor(public layoutService: LayoutService, public el: ElementRef) { }
}

