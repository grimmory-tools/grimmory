import {Component, EventEmitter, inject, Input, OnChanges, OnInit, Output, SimpleChanges} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {Textarea} from 'primeng/textarea';
import {Tooltip} from 'primeng/tooltip';
import {Image} from 'primeng/image';
import {FileUpload} from 'primeng/fileupload';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Divider} from 'primeng/divider';
import {MessageService} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
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

  @Input({required: true}) authorId!: number;
  @Input({required: true}) author!: AuthorDetails;
  @Output() authorUpdated = new EventEmitter<AuthorDetails>();

  private authorService = inject(AuthorService);
  private messageService = inject(MessageService);
  private dialogService = inject(DialogService);
  private t = inject(TranslocoService);

  form!: FormGroup;
  isSaving = false;
  isUploading = false;
  hasPhoto = true;
  photoTimestamp = Date.now();

  get photoUrl(): string {
    return this.authorService.getAuthorPhotoUrl(this.authorId) + '&t=' + this.photoTimestamp;
  }

  get uploadUrl(): string {
    return this.authorService.getUploadAuthorPhotoUrl(this.authorId);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['author'] && !changes['author'].firstChange) {
      this.hasPhoto = true;
      this.photoTimestamp = Date.now();
    }
  }

  ngOnInit(): void {
    this.form = new FormGroup({
      name: new FormControl(this.author.name || ''),
      nameLocked: new FormControl(this.author.nameLocked || false),
      description: new FormControl(this.author.description || ''),
      descriptionLocked: new FormControl(this.author.descriptionLocked || false),
      asin: new FormControl(this.author.asin || ''),
      asinLocked: new FormControl(this.author.asinLocked || false),
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
      isLocked ? fieldControl.disable() : fieldControl.enable();
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
    for (const field of ['name', 'description', 'asin']) {
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
        this.authorUpdated.emit(this.author);
      }
    });
  }

  onPhotoError(): void {
    this.hasPhoto = false;
  }

  onBeforeUpload(): void {
    this.isUploading = true;
  }

  onUpload(): void {
    this.isUploading = false;
    this.photoTimestamp = Date.now();
    this.hasPhoto = true;
    this.authorUpdated.emit(this.author);
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('authorBrowser.editor.toast.photoUploadedSummary'),
      detail: this.t.translate('authorBrowser.editor.toast.photoUploadedDetail')
    });
  }

  onUploadError(): void {
    this.isUploading = false;
    this.messageService.add({
      severity: 'error',
      summary: this.t.translate('authorBrowser.editor.toast.errorSummary'),
      detail: this.t.translate('authorBrowser.editor.toast.photoUploadErrorDetail')
    });
  }

  private saveMetadata(): void {
    if (this.isSaving) return;
    this.isSaving = true;

    const formValue = this.form.getRawValue();
    const request = {
      name: formValue.name?.trim() || undefined,
      description: formValue.description?.trim(),
      asin: formValue.asin?.trim(),
      nameLocked: formValue.nameLocked,
      descriptionLocked: formValue.descriptionLocked,
      asinLocked: formValue.asinLocked,
      photoLocked: formValue.photoLocked
    };

    this.authorService.updateAuthor(this.authorId, request).subscribe({
      next: (updated) => {
        this.isSaving = false;
        this.authorUpdated.emit(updated);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.editor.toast.successSummary'),
          detail: this.t.translate('authorBrowser.editor.toast.successDetail')
        });
      },
      error: () => {
        this.isSaving = false;
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.editor.toast.errorSummary'),
          detail: this.t.translate('authorBrowser.editor.toast.errorDetail')
        });
      }
    });
  }

  private applyLockStates(): void {
    for (const field of ['name', 'description', 'asin']) {
      const lockedControl = this.form.get(field + 'Locked');
      const fieldControl = this.form.get(field);
      if (lockedControl?.value && fieldControl) {
        fieldControl.disable();
      }
    }
  }
}
