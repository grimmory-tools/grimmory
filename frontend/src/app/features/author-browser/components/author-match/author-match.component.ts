import {Component, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
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

  searchQuery = '';
  asinQuery = '';
  selectedRegion = 'us';
  searching = false;
  matching = false;
  results: AuthorSearchResult[] = [];
  hasSearched = false;

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
    this.searchQuery = this.authorName;
  }

  get canSearch(): boolean {
    return !!this.searchQuery.trim() || !!this.asinQuery.trim();
  }

  search(): void {
    const asin = this.asinQuery.trim();
    const query = this.searchQuery.trim();
    if (!query && !asin) return;
    this.searching = true;
    this.results = [];
    this.hasSearched = true;

    this.authorService.searchAuthorMetadata(this.authorId, query, this.selectedRegion, asin || undefined)
      .subscribe({
        next: (results) => {
          this.results = results;
          this.searching = false;
        },
        error: () => {
          this.searching = false;
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
    this.matching = true;
    const request: AuthorMatchRequest = {
      source: result.source,
      asin: result.asin,
      region: this.selectedRegion
    };

    this.authorService.matchAuthor(this.authorId, request).subscribe({
      next: (updatedAuthor) => {
        this.matching = false;
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.match.toast.matchSuccessSummary'),
          detail: this.t.translate('authorBrowser.match.toast.matchSuccessDetail'),
          life: 3000
        });
        this.authorMatched.emit(updatedAuthor);
      },
      error: () => {
        this.matching = false;
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
