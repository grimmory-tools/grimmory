import {Component, inject, OnInit} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {Checkbox} from 'primeng/checkbox';
import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';
import {AppSettingsService} from '../../../core/service/app-settings.service';
import {filter, take} from 'rxjs/operators';
import {MessageService} from 'primeng/api';
import {AppSettingKey} from '../../../core/model/app-settings.model';
import {Select} from 'primeng/select';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';

@Component({
  selector: 'app-metadata-provider-settings',
  imports: [
    ReactiveFormsModule,
    TableModule,
    Checkbox,
    InputText,
    Button,
    FormsModule,
    Select,
    ExternalDocLinkComponent
  ],
  templateUrl: './metadata-provider-settings.component.html',
  styleUrl: './metadata-provider-settings.component.scss'
})
export class MetadataProviderSettingsComponent implements OnInit {

  amazonDomains = [
    {label: 'amazon.com', value: 'com'},
    {label: 'amazon.de', value: 'de'},
    {label: 'amazon.co.uk', value: 'co.uk'},
    {label: 'amazon.co.jp', value: 'co.jp'},
    {label: 'amazon.ca', value: 'ca'},
    {label: 'amazon.in', value: 'in'},
    {label: 'amazon.com.au', value: 'com.au'},
    {label: 'amazon.fr', value: 'fr'},
    {label: 'amazon.it', value: 'it'},
    {label: 'amazon.es', value: 'es'},
    {label: 'amazon.nl', value: 'nl'},
    {label: 'amazon.se', value: 'se'},
    {label: 'amazon.com.br', value: 'com.br'},
    {label: 'amazon.sg', value: 'sg'},
    {label: 'amazon.com.mx', value: 'com.mx'},
    {label: 'amazon.pl', value: 'pl'},
    {label: 'amazon.ae', value: 'ae'},
    {label: 'amazon.sa', value: 'sa'},
    {label: 'amazon.tr', value: 'tr'}
  ];

  selectedAmazonDomain = 'com';

  hardcoverToken: string = '';
  amazonCookie: string = '';
  hardcoverEnabled: boolean = false;
  amazonEnabled: boolean = false;
  goodreadsEnabled: boolean = false;
  googleEnabled: boolean = false;
  comicvineEnabled: boolean = false;
  comicvineToken: string = '';
  doubanEnabled: boolean = false;

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);

  private appSettings$ = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.appSettings$
      .pipe(
        filter(settings => settings != null),
        take(1)
      )
      .subscribe(settings => {
        const metadataProviderSettings = settings!.metadataProviderSettings;
        this.amazonEnabled = metadataProviderSettings?.amazon?.enabled ?? false;
        this.amazonCookie = metadataProviderSettings?.amazon?.cookie ?? "";
        this.selectedAmazonDomain = metadataProviderSettings?.amazon?.domain ?? 'com';
        this.goodreadsEnabled = metadataProviderSettings?.goodReads?.enabled ?? false;
        this.googleEnabled = metadataProviderSettings?.google?.enabled ?? false;
        this.hardcoverToken = metadataProviderSettings?.hardcover?.apiKey ?? '';
        this.hardcoverEnabled = metadataProviderSettings?.hardcover?.enabled ?? false;
        this.comicvineEnabled = metadataProviderSettings?.comicvine?.enabled ?? false;
        this.comicvineToken = metadataProviderSettings?.comicvine?.apiKey ?? '';
        this.doubanEnabled = metadataProviderSettings?.douban?.enabled ?? false;
      });
  }

  onTokenChange(newToken: string): void {
    this.hardcoverToken = newToken;
    if (!newToken.trim()) {
      this.hardcoverEnabled = false;
    }
  }

  onComicTokenChange(newToken: string): void {
    this.comicvineToken = newToken;
    if (!newToken.trim()) {
      this.comicvineEnabled = false;
    }
  }

  saveSettings(): void {
    const payload = [
      {
        key: AppSettingKey.METADATA_PROVIDER_SETTINGS,
        newValue: {
          amazon: {
            enabled: this.amazonEnabled,
            cookie: this.amazonCookie,
            domain: this.selectedAmazonDomain
          },

          comicvine: {
            enabled: this.comicvineEnabled,
            apiKey: this.comicvineToken.trim()
          },

          goodReads: {enabled: this.goodreadsEnabled},
          google: {enabled: this.googleEnabled},
          hardcover: {
            enabled: this.hardcoverEnabled,
            apiKey: this.hardcoverToken.trim()
          },
          douban: {enabled: this.doubanEnabled}
        }
      }
    ];

    this.appSettingsService.saveSettings(payload).subscribe({
      next: () =>
        this.messageService.add({
          severity: 'success',
          summary: 'Saved',
          detail: 'Metadata provider settings saved.'
        }),
      error: () =>
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to save metadata provider settings.'
        })
    });
  }
}
