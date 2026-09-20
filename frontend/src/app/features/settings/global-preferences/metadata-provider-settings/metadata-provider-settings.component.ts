import {Component, computed, DestroyRef, effect, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {TableModule} from '@openng/optimus-ui/table';
import {InputText} from '@openng/optimus-ui/inputtext';
import {Button} from '@openng/optimus-ui/button';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {MessageService} from '@openng/optimus-ui/api';
import {AppSettingKey, type MetadataProviderSettings} from '../../../../shared/model/app-settings.model';
import {MetadataCatalogService} from '../../../../shared/metadata/metadata-catalog.service';
import type {MetadataProviderId} from '../../../../shared/metadata/metadata-providers';
import {MetadataSourceQueryService} from '../../../metadata/sources/metadata-source-query.service';
import {Select} from '@openng/optimus-ui/select';
import {ExternalDocLinkComponent} from '../../../../shared/components/external-doc-link/external-doc-link.component';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

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
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './metadata-provider-settings.component.html',
  styleUrl: './metadata-provider-settings.component.scss'
})
export class MetadataProviderSettingsComponent {
  private readonly catalog = inject(MetadataCatalogService);
  private readonly sourceQuery = inject(MetadataSourceQueryService);
  readonly configurableProviders = computed(() => this.catalog.providers().filter(provider => provider.hasOptions));
  readonly simpleProviders = computed(() => this.catalog.providers().filter(provider => !provider.hasOptions));
  readonly enabled: Partial<Record<MetadataProviderId, boolean>> = {};

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

  appleBooksCountries = [
    {label: 'US', value: 'US'},
    {label: 'CA', value: 'CA'},
    {label: 'GB', value: 'GB'},
    {label: 'DE', value: 'DE'},
    {label: 'FR', value: 'FR'},
    {label: 'PL', value: 'PL'},
    {label: 'JP', value: 'JP'},
    {label: 'AU', value: 'AU'},
    {label: 'IT', value: 'IT'},
    {label: 'ES', value: 'ES'},
  ]

  selectedAppleBooksCountry = 'US';
  selectedAudibleDomain = 'com';

  hardcoverToken: string = '';
  amazonCookie: string = '';
  comicvineToken: string = '';
  ranobedbPreferRomaji: boolean = false;
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
    this.amazonCookie = metadataProviderSettings?.amazon?.cookie ?? "";
    this.selectedAmazonDomain = metadataProviderSettings?.amazon?.domain ?? 'com';
    this.selectedGoogleLanguage = metadataProviderSettings?.google?.language ?? '';
    this.googleApiKey = metadataProviderSettings?.google?.apiKey ?? '';
    this.hardcoverToken = metadataProviderSettings?.hardcover?.apiKey ?? '';
    this.comicvineToken = metadataProviderSettings?.comicvine?.apiKey ?? '';
    this.ranobedbPreferRomaji = metadataProviderSettings?.ranobedb?.preferRomaji ?? false;
    this.selectedAudibleDomain = metadataProviderSettings?.audible?.domain ?? 'com';
    this.selectedAppleBooksCountry = metadataProviderSettings?.appleBooks?.country ?? 'US';
    for (const provider of this.catalog.providers()) {
      this.enabled[provider.id] = !!metadataProviderSettings?.[provider.settingsKey]?.enabled && !this.isToggleDisabled(provider.id);
    }
  }

  private readonly requiredKey: Partial<Record<MetadataProviderId, () => string>> = {
    Google: () => this.googleApiKey,
    Hardcover: () => this.hardcoverToken,
    Comicvine: () => this.comicvineToken,
  };

  isToggleDisabled(id: MetadataProviderId): boolean {
    const key = this.requiredKey[id];
    return key !== undefined && !key().trim();
  }

  onKeyChange(id: MetadataProviderId, key: string): void {
    if (!key.trim()) {
      this.enabled[id] = false;
    }
  }

  saveSettings(): void {
    const options: {
      [Id in MetadataProviderId]?: Omit<MetadataProviderSettings[Uncapitalize<Id>], 'enabled'>;
    } = {
      Amazon: {cookie: this.amazonCookie, domain: this.selectedAmazonDomain},
      Google: {language: this.selectedGoogleLanguage, apiKey: this.googleApiKey.trim()},
      Hardcover: {apiKey: this.hardcoverToken.trim()},
      Comicvine: {apiKey: this.comicvineToken.trim()},
      Ranobedb: {preferRomaji: this.ranobedbPreferRomaji},
      Audible: {domain: this.selectedAudibleDomain},
      AppleBooks: {country: this.selectedAppleBooksCountry},
    };
    const payload = [
      {
        key: AppSettingKey.METADATA_PROVIDER_SETTINGS,
        newValue: Object.fromEntries(this.catalog.providers().map(provider => [
          provider.settingsKey,
          {enabled: this.enabled[provider.id] ?? false, ...options[provider.id]},
        ])),
      }
    ];

    this.appSettingsService.saveSettings(payload).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        void this.sourceQuery.refreshProviders();
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsMeta.providers.saveSuccess')
        });
      },
      error: () =>
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsMeta.providers.saveError')
        })
    });
  }
}
