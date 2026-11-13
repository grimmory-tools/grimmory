import {Component, inject, input, Input, linkedSignal, OnInit, output} from '@angular/core';
import {injectMutation} from '@tanstack/angular-query-experimental';
import {FormControl, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Button} from '@openng/optimus-ui/button';
import {InputText} from '@openng/optimus-ui/inputtext';
import {Textarea} from '@openng/optimus-ui/textarea';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {Image} from '@openng/optimus-ui/image';
import {FileUpload, FileUploadHandlerEvent} from '@openng/optimus-ui/fileupload';
import {ProgressSpinner} from '@openng/optimus-ui/progressspinner';
import {Divider} from '@openng/optimus-ui/divider';
import {MessageService} from '@openng/optimus-ui/api';
import {DialogService} from '@openng/optimus-ui/dynamicdialog';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {AuthorCommandService} from '../../data/author-command.service';
import {type AuthorDetail} from '../../data/author-query.models';
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
export class AuthorEditorComponent implements OnInit {
  private readonly urlHelper = inject(UrlHelperService);
  private readonly authorCommands = inject(AuthorCommandService);
  private readonly messageService = inject(MessageService);
  private readonly dialogService = inject(DialogService);
  private readonly t = inject(TranslocoService);

  @Input({required: true}) authorId!: number;
  @Input({required: true}) author!: AuthorDetail;
  readonly photoTimestamp = input.required<number>();
  readonly authorUpdated = output<void>();

  form!: FormGroup;
  protected readonly updateMutation = injectMutation(() => this.authorCommands.update());
  protected readonly uploadMutation = injectMutation(() => this.authorCommands.uploadPhoto());
  readonly hasPhoto = linkedSignal({source: this.photoTimestamp, computation: () => true});

  get photoUrl(): string {
    return this.urlHelper.getAuthorPhotoUrl(this.authorId, this.photoTimestamp());
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
        this.authorUpdated.emit();
      }
    });
  }

  onPhotoError(): void {
    this.hasPhoto.set(false);
  }

  onUpload({files}: FileUploadHandlerEvent): void {
    const [file] = files;
    this.uploadMutation.mutateAsync({authorId: this.authorId, file}).then(
      () => {
        this.authorUpdated.emit();
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.editor.toast.photoUploadedSummary'),
          detail: this.t.translate('authorBrowser.editor.toast.photoUploadedDetail')
        });
      },
      () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.editor.toast.errorSummary'),
          detail: this.t.translate('authorBrowser.editor.toast.photoUploadErrorDetail')
        });
      }
    );
  }

  private saveMetadata(): void {
    if (this.updateMutation.isPending()) return;

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

    this.updateMutation.mutateAsync({authorId: this.authorId, request}).then(
      () => {
        this.authorUpdated.emit();
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.editor.toast.successSummary'),
          detail: this.t.translate('authorBrowser.editor.toast.successDetail')
        });
      },
      () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.editor.toast.errorSummary'),
          detail: this.t.translate('authorBrowser.editor.toast.errorDetail')
        });
      }
    );
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
