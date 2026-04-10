import {Component, DestroyRef, effect, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {MessageService} from 'primeng/api';
import {AppSettingKey} from '../../../../shared/model/app-settings.model';
import {Select} from 'primeng/select';
import {ExternalDocLinkComponent} from '../../../../shared/components/external-doc-link/external-doc-link.component';
import { ToggleSwitch } from 'primeng/toggleswitch';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-metadata-provider-settings',
  imports: [
    ReactiveFormsModule,
    TableModule,
    InputText,
    Button,
    FormsModule,
    Select,
    ExternalDocLinkComponent,
    ToggleSwitch,
    TranslocoDirective
  ],
  templateUrl: './metadata-provider-settings.component.html',
  styleUrl: './metadata-provider-settings.component.scss'
})
export class MetadataProviderSettingsComponent {

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

  googleLanguages = [
    {label: 'Dutch', value: 'nl'},
    {label: 'English', value: 'en'},
    {label: 'French', value: 'fr'},
    {label: 'German', value: 'de'},
    {label: 'Italian', value: 'it'},
    {label: 'Japanese', value: 'ja'},
    {label: 'Polish', value: 'pl'},
    {label: 'Portuguese', value: 'pt'},
    {label: 'Spanish', value: 'es'},
    {label: 'Swedish', value: 'sv'}
  ];

  selectedGoogleLanguage = '';

  audibleDomains = [
    {label: 'audible.com', value: 'com'},
    {label: 'audible.co.uk', value: 'co.uk'},
    {label: 'audible.de', value: 'de'},
    {label: 'audible.fr', value: 'fr'},
    {label: 'audible.it', value: 'it'},
    {label: 'audible.es', value: 'es'},
    {label: 'audible.ca', value: 'ca'},
    {label: 'audible.com.au', value: 'com.au'},
    {label: 'audible.co.jp', value: 'co.jp'},
    {label: 'audible.in', value: 'in'}
  ];

  selectedAudibleDomain = 'com';
  audibleEnabled: boolean = false;

  hardcoverToken: string = '';
  amazonCookie: string = '';
  hardcoverEnabled: boolean = false;
  amazonEnabled: boolean = false;
  goodreadsEnabled: boolean = false;
  googleEnabled: boolean = false;
  comicvineEnabled: boolean = false;
  comicvineToken: string = '';
  doubanEnabled: boolean = false;
  lubimyCzytacEnabled: boolean = false;
  ranobedbEnabled: boolean = false;
  googleApiKey: string = '';

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);
  private destroyRef = inject(DestroyRef);

  private readonly syncSettingsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (settings) {
      this.applySettings(settings);
    }
  });

  private applySettings(settings: NonNullable<ReturnType<typeof this.appSettingsService.appSettings>>): void {
    const metadataProviderSettings = settings.metadataProviderSettings;
    this.amazonEnabled = metadataProviderSettings?.amazon?.enabled ?? false;
    this.amazonCookie = metadataProviderSettings?.amazon?.cookie ?? "";
    this.selectedAmazonDomain = metadataProviderSettings?.amazon?.domain ?? 'com';
    this.goodreadsEnabled = metadataProviderSettings?.goodReads?.enabled ?? false;
    this.googleEnabled = metadataProviderSettings?.google?.enabled ?? false;
    this.selectedGoogleLanguage = metadataProviderSettings?.google?.language ?? '';
    this.googleApiKey = metadataProviderSettings?.google?.apiKey ?? '';
    this.hardcoverToken = metadataProviderSettings?.hardcover?.apiKey ?? '';
    this.hardcoverEnabled = metadataProviderSettings?.hardcover?.enabled ?? false;
    this.comicvineEnabled = metadataProviderSettings?.comicvine?.enabled ?? false;
    this.comicvineToken = metadataProviderSettings?.comicvine?.apiKey ?? '';
    this.doubanEnabled = metadataProviderSettings?.douban?.enabled ?? false;
    this.lubimyCzytacEnabled = metadataProviderSettings?.lubimyczytac?.enabled ?? false;
    this.ranobedbEnabled = metadataProviderSettings?.ranobedb?.enabled ?? false;
    this.audibleEnabled = metadataProviderSettings?.audible?.enabled ?? false;
    this.selectedAudibleDomain = metadataProviderSettings?.audible?.domain ?? 'com';
  }

  onTokenChange(newToken: string): void {
    this.hardcoverToken = newToken;
    if (!newToken.trim()) {
      this.hardcoverEnabled = false;
    }
  }

  onComicTokenChange(newToken: string): void {
    this.comicvineToken = newToken;
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
          google: {
            enabled: this.googleEnabled,
            language: this.selectedGoogleLanguage,
            apiKey: this.googleApiKey.trim()
          },
          hardcover: {
            enabled: this.hardcoverEnabled,
            apiKey: this.hardcoverToken.trim()
          },
          douban: {enabled: this.doubanEnabled},
          lubimyczytac: {enabled: this.lubimyCzytacEnabled},
          ranobedb: {enabled: this.ranobedbEnabled},
          audible: {
            enabled: this.audibleEnabled,
            domain: this.selectedAudibleDomain
          }
        }
      }
    ];

    this.appSettingsService.saveSettings(payload).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () =>
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsMeta.providers.saveSuccess')
        }),
      error: () =>
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsMeta.providers.saveError')
        })
    });
  }
}
