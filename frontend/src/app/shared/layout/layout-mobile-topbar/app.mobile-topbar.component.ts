import { Component, inject } from '@angular/core';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { CommandPaletteService } from '../../../features/command-palette/command-palette.service';
import { LayoutService } from '../layout.service';

@Component({
  selector: 'app-mobile-topbar',
  templateUrl: './app.mobile-topbar.component.html',
  styleUrl: './app.mobile-topbar.component.scss',
  imports: [TranslocoDirective, TranslocoPipe],
})
export class AppMobileTopbarComponent {
  readonly layoutService = inject(LayoutService);
  protected readonly commandPaletteService = inject(CommandPaletteService);

  openSearch(): void {
    this.layoutService.closeMobileSidebar();
    this.commandPaletteService.open();
  }

  closeSearch(): void {
    this.commandPaletteService.hide();
  }
}
