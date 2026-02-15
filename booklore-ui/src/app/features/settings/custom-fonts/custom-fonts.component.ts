import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {CustomFontService} from '../../../shared/service/custom-font.service';
import {CustomFont, formatFileSize} from '../../../shared/model/custom-font.model';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {ConfirmationService} from 'primeng/api';
import {Tooltip} from 'primeng/tooltip';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {FontUploadDialogComponent} from './font-upload-dialog/font-upload-dialog.component';
import {Skeleton} from 'primeng/skeleton';
import {DialogSize, DialogStyle} from '../../../shared/services/dialog-launcher.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-custom-fonts',
  standalone: true,
  imports: [CommonModule, Button, ConfirmDialog, Tooltip, Skeleton, TranslocoDirective],
  templateUrl: './custom-fonts.component.html',
  styleUrls: ['./custom-fonts.component.scss'],
  providers: [ConfirmationService, DialogService]
})
export class CustomFontsComponent implements OnInit {
  customFonts: CustomFont[] = [];
  isLoading = true;
  fontsLoadedInBrowser = false;
  uploadDialogRef: DynamicDialogRef | null = null;

  readonly maxFonts = 10;

  private t = inject(TranslocoService);

  constructor(
    private customFontService: CustomFontService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    private dialogService: DialogService
  ) {}

  ngOnInit(): void {
    this.loadFonts();
  }

  async loadFonts(): Promise<void> {
    this.fontsLoadedInBrowser = false;

    try {
      const fonts = await new Promise<CustomFont[]>((resolve, reject) => {
        this.customFontService.getUserFonts().subscribe({
          next: resolve,
          error: reject
        });
      });

      this.customFonts = fonts;
      this.isLoading = false;

      await this.customFontService.loadAllFonts(fonts);

      this.fontsLoadedInBrowser = true;
    } catch (error) {
      console.error('Failed to load fonts:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsReader.fonts.loadError')
      });
      this.isLoading = false;
      this.fontsLoadedInBrowser = true;
    }
  }

  openUploadDialog(): void {
    if (this.customFonts.length >= this.maxFonts) {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('settingsReader.fonts.quotaExceeded'),
        detail: this.t.translate('settingsReader.fonts.quotaExceededDetail', {max: this.maxFonts})
      });
      return;
    }

    this.uploadDialogRef = this.dialogService.open(FontUploadDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      modal: true,
      dismissableMask: false,
      closable: false,
    });

    if (this.uploadDialogRef) {
      this.uploadDialogRef.onClose.subscribe((font: CustomFont | null) => {
        if (font) {
          this.customFonts.push(font);
        }
        this.uploadDialogRef = null;
      });
    }
  }

  deleteFont(font: CustomFont): void {
    this.confirmationService.confirm({
      message: this.t.translate('settingsReader.fonts.deleteFontConfirm', {name: font.fontName}),
      header: this.t.translate('settingsReader.fonts.deleteFontHeader'),
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.customFontService.deleteFont(font.id).subscribe({
          next: () => {
            this.customFonts = this.customFonts.filter(f => f.id !== font.id);
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('common.success'),
              detail: this.t.translate('settingsReader.fonts.deleteSuccess', {name: font.fontName})
            });
          },
          error: (error) => {
            console.error('Failed to delete font:', error);
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('settingsReader.fonts.deleteFailed'),
              detail: this.t.translate('settingsReader.fonts.deleteError')
            });
          }
        });
      }
    });
  }

  formatFileSize(bytes: number): string {
    return formatFileSize(bytes);
  }
}
