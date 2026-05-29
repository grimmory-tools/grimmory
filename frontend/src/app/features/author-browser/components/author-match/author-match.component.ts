import {ChangeDetectionStrategy, Component, computed, EventEmitter, inject, Input, OnInit, Output, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {Select} from 'primeng/select';
import {ProgressSpinner} from 'primeng/progressspinner';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {AuthorService} from '../../service/author.service';
import {AuthorDetails, AuthorMatchRequest, AuthorSearchResult} from '../../model/author.model';

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

  @Input({required: true}) authorId!: number;
  @Input({required: true}) authorName!: string;
  @Output() authorMatched = new EventEmitter<AuthorDetails>();

  private authorService = inject(AuthorService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);

  searchQuery = signal('');
  asinQuery = signal('');
  selectedRegion = signal('us');
  searching = signal(false);
  matching = signal(false);
  results = signal<AuthorSearchResult[]>([]);
  hasSearched = signal(false);

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

  ngOnInit(): void {
    this.searchQuery.set(this.authorName);
  }

  search(): void {
    const asin = this.asinQuery().trim();
    const query = this.searchQuery().trim();
    if (!query && !asin) return;
    this.searching.set(true);
    this.results.set([]);
    this.hasSearched.set(true);

    this.authorService.searchAuthorMetadata(this.authorId, query, this.selectedRegion(), asin || undefined)
      .subscribe({
        next: (results: AuthorSearchResult[]) => {
          this.results.set(results);
          this.searching.set(false);
        },
        error: () => {
          this.searching.set(false);
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('authorBrowser.match.toast.searchFailedSummary'),
            detail: this.t.translate('authorBrowser.match.toast.searchFailedDetail'),
            life: 3000
          });
        }
      });
  }

  matchAuthor(result: AuthorSearchResult): void {
    this.matching.set(true);
    const request: AuthorMatchRequest = {
      source: result.source,
      asin: result.asin,
      region: this.selectedRegion()
    };

    this.authorService.matchAuthor(this.authorId, request).subscribe({
      next: (updatedAuthor: AuthorDetails) => {
        this.matching.set(false);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.match.toast.matchSuccessSummary'),
          detail: this.t.translate('authorBrowser.match.toast.matchSuccessDetail'),
          life: 3000
        });
        this.authorMatched.emit(updatedAuthor);
      },
      error: () => {
        this.matching.set(false);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.match.toast.matchFailedSummary'),
          detail: this.t.translate('authorBrowser.match.toast.matchFailedDetail'),
          life: 3000
        });
      }
    });
  }
}
