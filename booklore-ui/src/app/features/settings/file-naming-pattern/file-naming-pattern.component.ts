import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {forkJoin, Observable, of} from 'rxjs';
import {AppSettingKey, AppSettings} from '../../../shared/model/app-settings.model';
import {catchError, filter, take} from 'rxjs/operators';
import {Library} from '../../book/model/library.model';
import {LibraryService} from '../../book/service/library.service';
import {InputText} from 'primeng/inputtext';
import {Tooltip} from 'primeng/tooltip';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {replacePlaceholders} from '../../../shared/util/pattern-resolver';

@Component({
  selector: 'app-file-naming-pattern',
  templateUrl: './file-naming-pattern.component.html',
  standalone: true,
  imports: [FormsModule, Button, InputText, Tooltip, ExternalDocLinkComponent, TranslocoDirective, TranslocoPipe],
  styleUrls: ['./file-naming-pattern.component.scss'],
})
export class FileNamingPatternComponent implements OnInit {
  readonly exampleMetadata: Record<string, string> = {
    title: "The Name of the Wind",
    subtitle: "Special Edition",
    authors: "Patrick Rothfuss",
    year: "2007",
    series: "The Kingkiller Chronicle",
    seriesIndex: "01",
    language: "English",
    publisher: "DAW Books",
    isbn: "9780756404741",
  };

  defaultPattern = '';
  libraries: Library[] = [];
  defaultErrorMessage = '';

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private libraryService = inject(LibraryService);
  private t = inject(TranslocoService);

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.appSettings$
      .pipe(filter((settings) => settings != null), take(1))
      .subscribe((settings) => {
        this.defaultPattern = settings?.uploadPattern ?? '';
      });

    this.libraryService.libraryState$
      .pipe(filter(state => state.loaded && !!state.libraries))
      .subscribe(state => {
        this.libraries = state.libraries ?? [];
      });
  }

  private resolvePattern(pattern: string, values: Record<string, string>): string {
    return replacePlaceholders(pattern, values);
  }

  private appendExtensionIfMissing(path: string, ext = '.pdf'): string {
    const lastSegment = path.split('/').pop() ?? '';
    const hasExtension = /\.[a-z0-9]{2,5}$/i.test(lastSegment);
    return hasExtension ? path : path + ext;
  }

  private generatePreview(pattern: string): string {
    let path = this.resolvePattern(pattern || '', this.exampleMetadata);

    if (!path) return '/original_filename.pdf';
    if (path.endsWith('/')) return path + 'original_filename.pdf';
    if (path.includes('{originalFilename}')) {
      path = path.replace('{originalFilename}', 'original_filename.pdf');
      return path.startsWith('/') ? path : `/${path}`;
    }
    path = this.appendExtensionIfMissing(path);
    return path.startsWith('/') ? path : `/${path}`;
  }

  generateDefaultPreview(): string {
    return this.generatePreview(this.defaultPattern);
  }

  generateLibraryPreview(library: Library): string {
    return this.generatePreview(library.fileNamingPattern || this.defaultPattern);
  }

  validatePattern(pattern: string): boolean {
    const validPatternRegex = /^[\w\s\-{}\[\]\/().<>.,:'"#|]*$/;
    return validPatternRegex.test(pattern);
  }

  onDefaultPatternChange(pattern: string): void {
    this.defaultPattern = pattern;
    this.defaultErrorMessage = this.validatePattern(pattern) ? '' : this.t.translate('settingsNaming.defaultPattern.invalidChars');
  }

  onLibraryPatternChange(library: Library): void {
    // Optionally add per-library validation here
  }

  clearLibraryPattern(library: Library): void {
    library.fileNamingPattern = '';
  }

  savePatterns(): void {
    if (this.defaultErrorMessage) {
      this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsNaming.defaultPattern.invalidError'));
      return;
    }
    this.appSettingsService
      .saveSettings([
        {key: AppSettingKey.UPLOAD_FILE_PATTERN, newValue: this.defaultPattern},
      ])
      .subscribe({
        next: () => this.showMessage('success', this.t.translate('common.success'), this.t.translate('settingsNaming.defaultPattern.saveSuccess')),
        error: () => this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsNaming.defaultPattern.saveError')),
      });
  }

  saveLibraryPatterns(): void {
    const patchRequests = this.libraries.map(library =>
      this.libraryService.updateLibraryFileNamingPattern(library.id!, library.fileNamingPattern || '').pipe(
        catchError(() => of(null))
      )
    );
    forkJoin(patchRequests).subscribe(results => {
      const failures = results.filter(result => result === null);
      if (failures.length === 0) {
        this.showMessage('success', this.t.translate('common.success'), this.t.translate('settingsNaming.libraryOverrides.saveSuccess'));
      } else {
        this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsNaming.libraryOverrides.saveError', {count: failures.length}));
      }
    });
  }

  private showMessage(severity: 'success' | 'error', summary: string, detail: string): void {
    this.messageService.add({severity, summary, detail});
  }
}
