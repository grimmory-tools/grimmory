import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {AppSettingsService} from '../core/service/app-settings.service';
import {Observable} from 'rxjs';
import {AppSettings} from '../core/model/app-settings.model';
import {filter, take} from 'rxjs/operators';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-file-upload-pattern',
  templateUrl: './file-upload-pattern.component.html',
  standalone: true,
  imports: [FormsModule, Button, Tooltip],
  styleUrls: ['./file-upload-pattern.component.scss']
})
export class FileUploadPatternComponent implements OnInit {
  title: string = 'The Book Thief';
  authors: string = 'Markus Zusak';
  uploadPattern: string = '';
  errorMessage: string = '';

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.appSettings$.pipe(
      filter(settings => settings != null),
      take(1)
    ).subscribe(settings => {
      this.uploadPattern = settings?.uploadPattern || '';
    });
  }

  generatePreview(): string {
    let path = this.uploadPattern
      .replace('{title}', this.title)
      .replace('{authors}', this.authors)
      .replace(/\/+$/, '')
      .trim();

    return path ? `/${path}/filename.pdf` : '/filename.pdf';
  }

  validatePattern(pattern: string): void {
    const isValid = /^[\w\s\-{}\/().]*$/.test(pattern);
    this.errorMessage = isValid ? '' : 'Pattern contains invalid characters.';
  }

  onPatternChange(pattern: string): void {
    this.uploadPattern = pattern;
    this.validatePattern(pattern);
  }

  savePattern(): void {
    this.saveSetting('upload_file_pattern', this.uploadPattern);
  }

  private saveSetting(key: string, value: unknown): void {
    this.appSettingsService.saveSettings([{key, newValue: value}]).subscribe({
      next: () => this.showMessage('success', 'Settings Saved', 'The settings were successfully saved!'),
      error: () => this.showMessage('error', 'Error', 'There was an error saving the settings.')
    });
  }

  private showMessage(severity: 'success' | 'error', summary: string, detail: string): void {
    this.messageService.add({severity, summary, detail});
  }
}
