import {ChangeDetectionStrategy, Component, computed, inject, Input, OnInit, output, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from '@openng/optimus-ui/button';
import {InputText} from '@openng/optimus-ui/inputtext';
import {Select} from '@openng/optimus-ui/select';
import {ProgressSpinner} from '@openng/optimus-ui/progressspinner';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';
import {AuthorService} from '../../service/author.service';
import {AuthorDetails, AuthorMatchRequest, AuthorSearchResult} from '../../model/author.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';

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
  private readonly authorService = inject(AuthorService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private readonly appSettingsService = inject(AppSettingsService);

  @Input({required: true}) authorId!: number;
  @Input({required: true}) authorName!: string;
  readonly authorMatched = output<AuthorDetails>();

  readonly searchQuery = signal('');
  readonly asinQuery = signal('');
  readonly selectedRegion = signal('us');
  readonly searching = signal(false);
  readonly matching = signal(false);
  readonly results = signal<AuthorSearchResult[]>([]);
  readonly hasSearched = signal(false);

  readonly canSearch = computed(() => !!this.searchQuery().trim() || !!this.asinQuery().trim());

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
    {label: 'JP', value: 'jp'},
    {label: 'NL', value: 'nl'},
    {label: 'BR', value: 'br'},
    {label: 'SG', value: 'sg'},
    {label: 'MX', value: 'mx'},
    {label: 'SE', value: 'se'},
    {label: 'PL', value: 'pl'},
    {label: 'AE', value: 'ae'},
    {label: 'SA', value: 'sa'},
    {label: 'TR', value: 'tr'}
  ];

  ngOnInit(): void {
    this.searchQuery.set(this.authorName);
    const domain = this.appSettingsService.appSettings()?.metadataProviderSettings?.amazon?.domain;
    this.selectedRegion.set(authorRegionForAmazonDomain(domain));
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

const AMAZON_DOMAIN_REGION_MAP: Record<string, string> = {
  com: 'us',
  'co.uk': 'uk',
  'com.au': 'au',
  ca: 'ca',
  in: 'in',
  fr: 'fr',
  de: 'de',
  it: 'it',
  es: 'es',
  'co.jp': 'jp',
  nl: 'nl',
  'com.br': 'br',
  sg: 'sg',
  'com.mx': 'mx',
  se: 'se',
  pl: 'pl',
  ae: 'ae',
  sa: 'sa',
  tr: 'tr',
};

export function authorRegionForAmazonDomain(domain: string | null | undefined): string {
  return AMAZON_DOMAIN_REGION_MAP[domain ?? 'com'] ?? 'us';
}
