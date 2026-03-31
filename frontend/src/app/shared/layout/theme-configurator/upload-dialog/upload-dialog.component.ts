import {Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {DividerModule} from 'primeng/divider';
import {MessageModule} from 'primeng/message';
import {BackgroundUploadService} from '../background-upload.service';
import {take} from 'rxjs';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-upload-dialog',
  standalone: true,
  templateUrl: './upload-dialog.component.html',
  styleUrls: ['./upload-dialog.component.scss'],
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    InputTextModule,
    DividerModule,
    MessageModule,
    TranslocoDirective,
    TranslocoPipe
  ]
})
export class UploadDialogComponent {
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly backgroundUploadService = inject(BackgroundUploadService);
  private readonly t = inject(TranslocoService);

  uploadImageUrl = '';
  uploadFile: File | null = null;
  uploadError = '';

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.uploadFile = input.files[0];
      this.uploadImageUrl = '';
    }
  }

  submit() {
    this.uploadError = '';
    let upload$;
    if (this.uploadFile) {
      upload$ = this.backgroundUploadService.uploadFile(this.uploadFile);
    } else if (this.uploadImageUrl.trim()) {
      upload$ = this.backgroundUploadService.uploadUrl(this.uploadImageUrl.trim());
    } else {
      this.uploadError = this.t.translate('layout.uploadDialog.errorNoInput');
      return;
    }

    upload$.pipe(take(1)).subscribe({
      next: (imageUrl) => {
        if (imageUrl) {
          this.dialogRef.close({ imageUrl });
        } else {
          this.uploadError = 'Failed to upload image.';
        }
      },
      error: (err) => {
        console.error('Upload failed:', err);
        this.uploadError = 'Upload failed. Please try again.';
      }
    });
  }

  cancel() {
    this.dialogRef.close();
  }
}
