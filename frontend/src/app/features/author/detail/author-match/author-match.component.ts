import {ChangeDetectionStrategy, Component, computed, effect, EventEmitter, inject, Input, OnInit, Output, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from '@openng/optimus-ui/button';
import {InputText} from '@openng/optimus-ui/inputtext';
import {Select} from '@openng/optimus-ui/select';
import {ProgressSpinner} from '@openng/optimus-ui/progressspinner';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {injectMutation, injectQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {AuthorCommandService} from '../../data/author-command.service';
import {type AuthorMetadataSearchParams} from '../../data/author-query-params';
import {AuthorQueryService} from '../../data/author-query.service';
import {type AuthorSearchResult} from '../../data/author-response.models';

const NO_SEARCH: AuthorMetadataSearchParams = {query: '', region: 'us', asin: ''};

interface RegionOption {
  label: string;
  value: string;
}

@Component({
  selector: 'app-author-match',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './author-match.component.html',
  styleUrls: ['./author-match.component.scss'],
  imports: [
    FormsModule,
    Button,
    InputText,
    Select,
    ProgressSpinner,
    TranslocoDirective
  ]
})
export class AuthorMatchComponent implements OnInit {
  private readonly authorQueries = inject(AuthorQueryService);
  private readonly authorCommands = inject(AuthorCommandService);
  private readonly queryClient = inject(QueryClient);

  @Input({required: true}) authorId!: number;
  @Input({required: true}) authorName!: string;
  @Output() authorMatched = new EventEmitter<void>();

  private messageService = inject(MessageService);
  private t = inject(TranslocoService);

  searchQuery = signal('');
  asinQuery = signal('');
  selectedRegion = signal('us');
  private readonly submitted = signal<AuthorMetadataSearchParams | null>(null);
  private readonly searchResults = injectQuery(() => ({
    ...this.authorQueries.metadataSearch(this.authorId, this.submitted() ?? NO_SEARCH),
    enabled: this.submitted() !== null,
  }));
  private readonly matchMutation = injectMutation(() => this.authorCommands.match());
  readonly searching = this.searchResults.isFetching;
  readonly results = computed(() => this.searchResults.data() ?? []);
  readonly hasSearched = computed(() => this.submitted() !== null);
  readonly matching = this.matchMutation.isPending;

  canSearch = computed(() => !!this.searchQuery().trim() || !!this.asinQuery().trim());

  regionOptions: RegionOption[] = [
    {label: 'US', value: 'us'},
    {label: 'UK', value: 'uk'},
    {label: 'AU', value: 'au'},
    {label: 'CA', value: 'ca'},
    {label: 'IN', value: 'in'},
    {label: 'FR', value: 'fr'},
    {label: 'DE', value: 'de'},
    {label: 'IT', value: 'it'},
    {label: 'ES', value: 'es'},
    {label: 'JP', value: 'jp'}
  ];

  constructor() {
    effect(() => {
      if (this.searchResults.isError()) {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.match.toast.searchFailedSummary'),
          detail: this.t.translate('authorBrowser.match.toast.searchFailedDetail'),
          life: 3000
        });
      }
    });
  }

  ngOnInit(): void {
    this.searchQuery.set(this.authorName);
  }

  search(): void {
    const asin = this.asinQuery().trim();
    const query = this.searchQuery().trim();
    if (!query && !asin) return;
    const params: AuthorMetadataSearchParams = {query, region: this.selectedRegion(), asin};
    void this.queryClient.resetQueries({queryKey: this.authorQueries.metadataSearch(this.authorId, params).queryKey, exact: true});
    this.submitted.set(params);
  }

  matchAuthor(result: AuthorSearchResult): void {
    const request = {source: result.source, asin: result.asin, region: this.selectedRegion()};
    this.matchMutation.mutateAsync({authorId: this.authorId, request}).then(
      () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.match.toast.matchSuccessSummary'),
          detail: this.t.translate('authorBrowser.match.toast.matchSuccessDetail'),
          life: 3000
        });
        this.authorMatched.emit();
      },
      () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.match.toast.matchFailedSummary'),
          detail: this.t.translate('authorBrowser.match.toast.matchFailedDetail'),
          life: 3000
        });
      }
    );
  }
}
