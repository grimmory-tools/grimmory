import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {Button} from '@openng/optimus-ui/button';
import {InputText} from '@openng/optimus-ui/inputtext';
import {ProgressSpinner} from '@openng/optimus-ui/progressspinner';
import {Image} from '@openng/optimus-ui/image';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {injectMutation, injectQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {AuthorCommandService} from '../../data/author-command.service';
import {AuthorQueryService} from '../../data/author-query.service';
import {type AuthorPhotoResult} from '../../data/author-response.models';

@Component({
  selector: 'app-author-photo-search',
  templateUrl: './author-photo-search.component.html',
  styleUrls: ['./author-photo-search.component.scss'],
  imports: [
    ReactiveFormsModule,
    Button,
    InputText,
    ProgressSpinner,
    Image,
    Tooltip,
    TranslocoDirective
  ]
})
export class AuthorPhotoSearchComponent implements OnInit {
  private readonly authorQueries = inject(AuthorQueryService);
  private readonly authorCommands = inject(AuthorCommandService);
  private readonly queryClient = inject(QueryClient);
  searchForm: FormGroup;

  private fb = inject(FormBuilder);
  private dynamicDialogConfig = inject(DynamicDialogConfig);
  protected dynamicDialogRef = inject(DynamicDialogRef);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);

  private readonly authorId: number = this.dynamicDialogConfig.data.authorId;
  private readonly submitted = signal<string | null>(null);
  private readonly photoQuery = injectQuery(() => ({
    ...this.authorQueries.photoSearch(this.authorId, this.submitted() ?? ''),
    enabled: this.submitted() !== null,
  }));
  private readonly uploadMutation = injectMutation(() => this.authorCommands.uploadPhotoFromUrl());
  readonly photos = computed(() => [...(this.photoQuery.data() ?? [])].sort((a, b) => a.index - b.index));
  readonly searching = this.photoQuery.isFetching;
  readonly hasSearched = computed(() => this.submitted() !== null);

  constructor() {
    this.searchForm = this.fb.group({
      query: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    const authorName = this.dynamicDialogConfig.data.authorName;

    if (authorName) {
      this.searchForm.patchValue({query: authorName});
      if (this.searchForm.valid) {
        this.onSearch();
      }
    }
  }

  onSearch(): void {
    if (!this.searchForm.valid) return;
    const query: string = this.searchForm.value.query;
    void this.queryClient.resetQueries({queryKey: this.authorQueries.photoSearch(this.authorId, query).queryKey, exact: true});
    this.submitted.set(query);
  }

  selectAndUploadPhoto(photo: AuthorPhotoResult): void {
    this.uploadMutation.mutateAsync({authorId: this.authorId, url: photo.url}).then(
      () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.editor.toast.photoUploadedSummary'),
          detail: this.t.translate('authorBrowser.editor.toast.photoUploadedDetail')
        });
        this.dynamicDialogRef.close(true);
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

  onClear(): void {
    this.searchForm.reset();
    this.submitted.set(null);
  }
}
