import {Component, inject, Input, OnChanges, OnInit, output, signal, SimpleChanges} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Button} from '@openng/optimus-ui/button';
import {InputText} from '@openng/optimus-ui/inputtext';
import {Textarea} from '@openng/optimus-ui/textarea';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {Image} from '@openng/optimus-ui/image';
import {FileUpload} from '@openng/optimus-ui/fileupload';
import {ProgressSpinner} from '@openng/optimus-ui/progressspinner';
import {Divider} from '@openng/optimus-ui/divider';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {DialogService} from '@openng/optimus-ui/dynamicdialog';
import {AuthorService} from '../../service/author.service';
import {AuthorDetails} from '../../model/author.model';
import {AuthorPhotoSearchComponent} from '../author-photo-search/author-photo-search.component';

@Component({
  selector: 'app-author-editor',
  standalone: true,
  templateUrl: './author-editor.component.html',
  styleUrls: ['./author-editor.component.scss'],
  imports: [
    ReactiveFormsModule,
    TranslocoDirective,
    Button,
    InputText,
    Textarea,
    Tooltip,
    Image,
    FileUpload,
    ProgressSpinner,
    Divider
  ],
  providers: [DialogService]
})
export class AuthorEditorComponent implements OnInit, OnChanges {
  private readonly authorService = inject(AuthorService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly dialogService = inject(DialogService);
  private readonly t = inject(TranslocoService);

  @Input({required: true}) authorId!: number;
  @Input({required: true}) author!: AuthorDetails;
  readonly authorUpdated = output<AuthorDetails>();

  form!: FormGroup;
  readonly isSaving = signal(false);
  readonly isUploading = signal(false);
  readonly isDeletingPhoto = signal(false);
  hasPhoto = true;
  photoTimestamp = Date.now();

  get photoUrl(): string {
    return this.authorService.getAuthorPhotoUrl(this.authorId, this.photoTimestamp);
  }

  get uploadUrl(): string {
    return this.authorService.getUploadAuthorPhotoUrl(this.authorId);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['author'] && !changes['author'].firstChange) {
      this.hasPhoto = (changes['author'].currentValue as AuthorDetails).hasPhoto ?? true;
      this.photoTimestamp = Date.now();
    }
  }

  ngOnInit(): void {
    this.hasPhoto = this.author.hasPhoto ?? true;
    this.form = new FormGroup({
      name: new FormControl(this.author.name || ''),
      nameLocked: new FormControl(this.author.nameLocked || false),
      description: new FormControl(this.author.description || ''),
      descriptionLocked: new FormControl(this.author.descriptionLocked || false),
      asin: new FormControl(this.author.asin || ''),
      asinLocked: new FormControl(this.author.asinLocked || false),
      isni: new FormControl(this.author.isni || ''),
      isniLocked: new FormControl(this.author.isniLocked || false),
      photoLocked: new FormControl(this.author.photoLocked || false)
    });

    this.applyLockStates();
  }

  toggleLock(field: string): void {
    const lockedControl = this.form.get(field + 'Locked');
    if (!lockedControl) return;
    const isLocked = !lockedControl.value;
    lockedControl.setValue(isLocked);

    const fieldControl = this.form.get(field);
    if (fieldControl) {
      if (isLocked) {
        fieldControl.disable();
      } else {
        fieldControl.enable();
      }
    }

    this.saveMetadata();
  }

  togglePhotoLock(): void {
    const lockedControl = this.form.get('photoLocked');
    if (!lockedControl) return;
    lockedControl.setValue(!lockedControl.value);
    this.saveMetadata();
  }

  onSave(): void {
    this.saveMetadata();
  }

  lockAll(): void {
    for (const field of ['name', 'description', 'asin', 'isni']) {
      this.form.get(field + 'Locked')?.setValue(true);
      this.form.get(field)?.disable();
    }
    this.form.get('photoLocked')?.setValue(true);
    this.saveMetadata();
  }

  unlockAll(): void {
    for (const field of ['name', 'description', 'asin']) {
      this.form.get(field + 'Locked')?.setValue(false);
      this.form.get(field)?.enable();
    }
    this.form.get('photoLocked')?.setValue(false);
    this.saveMetadata();
  }

  openPhotoSearch(): void {
    const ref = this.dialogService.open(AuthorPhotoSearchComponent, {
      data: {authorId: this.authorId, authorName: this.author.name},
      header: this.t.translate('authorBrowser.editor.searchPhotoTitle'),
      width: '70vw',
      height: '80vh',
      modal: true,
      closable: true,
      dismissableMask: true,
      contentStyle: {'overflow': 'hidden', 'padding': '0', 'display': 'flex', 'flex-direction': 'column'}
    });
    ref?.onClose.subscribe((result: boolean) => {
      if (result) {
        this.photoTimestamp = Date.now();
        this.hasPhoto = true;
        this.authorUpdated.emit({...this.author, hasPhoto: true});
      }
    });
  }

  deletePhoto(): void {
    if (!this.hasPhoto || this.form.get('photoLocked')?.value || this.isUploading() || this.isDeletingPhoto()) return;
    this.confirmationService.confirm({
      header: this.t.translate('authorBrowser.editor.deletePhotoTitle'),
      message: this.t.translate('authorBrowser.editor.deletePhotoConfirm'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.t.translate('common.delete'),
      rejectLabel: this.t.translate('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        if (!this.hasPhoto || this.form.get('photoLocked')?.value || this.isUploading() || this.isDeletingPhoto()) return;
        this.isDeletingPhoto.set(true);
        this.authorService.deleteAuthorPhoto(this.authorId).subscribe({
          next: updated => {
            this.isDeletingPhoto.set(false);
            this.hasPhoto = false;
            this.authorUpdated.emit(updated);
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('authorBrowser.editor.toast.photoDeletedSummary'),
              detail: this.t.translate('authorBrowser.editor.toast.photoDeletedDetail')
            });
          },
          error: () => {
            this.isDeletingPhoto.set(false);
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('authorBrowser.editor.toast.errorSummary'),
              detail: this.t.translate('authorBrowser.editor.toast.photoDeleteErrorDetail')
            });
          }
        });
      }
    });
  }

  onPhotoError(): void {
    this.hasPhoto = false;
  }

  onBeforeUpload(): void {
    this.isUploading.set(true);
  }

  onUpload(): void {
    this.isUploading.set(false);
    this.photoTimestamp = Date.now();
    this.hasPhoto = true;
    this.authorUpdated.emit({...this.author, hasPhoto: true});
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('authorBrowser.editor.toast.photoUploadedSummary'),
      detail: this.t.translate('authorBrowser.editor.toast.photoUploadedDetail')
    });
  }

  onUploadError(): void {
    this.isUploading.set(false);
    this.messageService.add({
      severity: 'error',
      summary: this.t.translate('authorBrowser.editor.toast.errorSummary'),
      detail: this.t.translate('authorBrowser.editor.toast.photoUploadErrorDetail')
    });
  }

  private saveMetadata(): void {
    if (this.isSaving()) return;
    this.isSaving.set(true);

    const formValue = this.form.getRawValue();
    const request = {
      name: formValue.name?.trim() || undefined,
      description: formValue.description?.trim(),
      asin: formValue.asin?.trim(),
      isni: formValue.isni?.trim(),
      nameLocked: formValue.nameLocked,
      descriptionLocked: formValue.descriptionLocked,
      asinLocked: formValue.asinLocked,
      isniLocked: formValue.isniLocked,
      photoLocked: formValue.photoLocked
    };

    this.authorService.updateAuthor(this.authorId, request).subscribe({
      next: (updated) => {
        this.isSaving.set(false);
        this.authorUpdated.emit(updated);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.editor.toast.successSummary'),
          detail: this.t.translate('authorBrowser.editor.toast.successDetail')
        });
      },
      error: () => {
        this.isSaving.set(false);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.editor.toast.errorSummary'),
          detail: this.t.translate('authorBrowser.editor.toast.errorDetail')
        });
      }
    });
  }

  private applyLockStates(): void {
    for (const field of ['name', 'description', 'asin', 'isni']) {
      const lockedControl = this.form.get(field + 'Locked');
      const fieldControl = this.form.get(field);
      if (lockedControl?.value && fieldControl) {
        fieldControl.disable();
      }
    }
  }
}
