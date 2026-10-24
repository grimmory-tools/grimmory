import {Component, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {Password} from '@openng/optimus-ui/password';
import {Button} from '@openng/optimus-ui/button';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {LcpSettingsService} from '../../../shared/service/lcp-settings.service';

@Component({
  selector: 'app-lcp-import-settings',
  imports: [FormsModule, Password, Button, TranslocoDirective],
  templateUrl: './lcp-import-settings.component.html',
  styleUrl: './lcp-import-settings.component.scss'
})
export class LcpImportSettingsComponent {
  private readonly settings = inject(LcpSettingsService);
  private readonly t = inject(TranslocoService);
  readonly configured = signal(false);
  readonly busy = signal(true);
  readonly error = signal('');
  passphrase = '';

  constructor() {
    this.settings.getStatus().pipe(takeUntilDestroyed()).subscribe({
      next: status => { this.configured.set(status.hasDefaultPassphrase); this.busy.set(false); },
      error: () => { this.busy.set(false); this.error.set(this.t.translate('lcpImport.settingsError')); }
    });
  }

  save(): void {
    if (!this.passphrase || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.settings.save(this.passphrase).subscribe({
      next: () => { this.passphrase = ''; this.configured.set(true); this.busy.set(false); },
      error: () => { this.busy.set(false); this.error.set(this.t.translate('lcpImport.settingsError')); }
    });
  }

  clear(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.settings.clear().subscribe({
      next: () => { this.passphrase = ''; this.configured.set(false); this.busy.set(false); },
      error: () => { this.busy.set(false); this.error.set(this.t.translate('lcpImport.settingsError')); }
    });
  }
}
