import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { LucideArrowLeft, LucideMenu, LucideSearch, LucideX } from '@lucide/angular';
import { CommandPaletteService } from '../../../features/command-palette/command-palette.service';
import { LayoutService } from '../layout.service';
import { PageHeaderService } from '../page-header/page-header.service';

@Component({
  selector: 'app-mobile-topbar',
  templateUrl: './app.mobile-topbar.component.html',
  styleUrl: './app.mobile-topbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslocoDirective, TranslocoPipe, LucideArrowLeft, LucideMenu, LucideSearch, LucideX],
})
export class AppMobileTopbarComponent {
  readonly layoutService = inject(LayoutService);
  protected readonly commandPaletteService = inject(CommandPaletteService);
  private readonly pageHeader = inject(PageHeaderService);
  private readonly router = inject(Router);

  protected readonly topbar = this.pageHeader.mobileTopbar;
  protected readonly backTarget = computed(() => this.topbar().backTarget);
  protected readonly backLabel = computed(() => this.backTarget()?.label);

  protected readonly hasBack = computed(() => !!this.backTarget());
  protected readonly pageTitle = computed(() => this.topbar().title);
  protected readonly showPageTitle = computed(() => !!this.pageTitle() && !this.pageHeader.leadVisible());

  openSearch(): void {
    this.layoutService.closeMobileSidebar();
    this.commandPaletteService.open();
  }

  closeSearch(): void {
    this.commandPaletteService.hide();
  }

  goBack(): void {
    const backTarget = this.backTarget();
    if (backTarget) {
      this.router.navigate([...backTarget.commands]);
    }
  }
}
