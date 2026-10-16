import {Component, DestroyRef, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Button} from '@openng/optimus-ui/button';
import {MessageService} from '@openng/optimus-ui/api';
import {CustomFontService} from '../../../shared/service/custom-font.service';
import {CustomFont, formatFileSize} from '../../../shared/model/custom-font.model';
import {ConfirmDialog} from '@openng/optimus-ui/confirmdialog';
import {ConfirmationService} from '@openng/optimus-ui/api';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {DialogService, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {FontUploadDialogComponent} from './font-upload-dialog/font-upload-dialog.component';
import {Skeleton} from '@openng/optimus-ui/skeleton';
import {DialogSize, DialogStyle} from '../../../shared/services/dialog-launcher.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {DatePipe} from '@angular/common';

@Component({
  selector: 'app-custom-fonts',
  standalone: true,
  imports: [
    DatePipe,Button, ConfirmDialog, Tooltip, Skeleton, TranslocoDirective],
  templateUrl: './custom-fonts.component.html',
  styleUrls: ['./custom-fonts.component.scss'],
  providers: [ConfirmationService, DialogService]
})
export class CustomFontsComponent implements OnInit {
  fontsLoadedInBrowser = false;
  uploadDialogRef: DynamicDialogRef | null = null;

  readonly maxFonts = 10;

  private readonly customFontService = inject(CustomFontService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly dialogService = inject(DialogService);
  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  get customFonts(): CustomFont[] { return this.customFontService.fonts(); }
  get isLoading(): boolean { return this.customFontService.isFontsLoading(); }

  ngOnInit(): void {
    this.loadFonts();
  }

  async loadFonts(): Promise<void> {
    this.fontsLoadedInBrowser = false;

    try {
      const fonts = await this.customFontService.ensureFonts();

      await this.customFontService.loadAllFonts(fonts);

      this.fontsLoadedInBrowser = true;
    } catch (error) {
      console.error('Failed to load fonts:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsReader.fonts.loadError')
      });
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
      this.uploadDialogRef.onClose.pipe(
        takeUntilDestroyed(this.destroyRef)
      ).subscribe(() => {
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
        this.customFontService.deleteFont(font.id).pipe(
          takeUntilDestroyed(this.destroyRef)
        ).subscribe({
          next: () => {
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
