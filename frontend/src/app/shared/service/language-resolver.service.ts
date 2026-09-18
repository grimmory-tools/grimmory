import {Injectable, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoService} from '@jsverse/transloco';

export interface ResolvedLanguage {
  raw: string;
  tag: string;
  base: string;
  languageName: string;
  regionName: string | null;
  displayName: string;
  recognized: boolean;
}

const LANGUAGE_ALIASES: Record<string, string> = {
  eng: 'en', english: 'en',
  spa: 'es', spanish: 'es',
  fra: 'fr', fre: 'fr', french: 'fr',
  deu: 'de', ger: 'de', german: 'de',
  ita: 'it', italian: 'it',
  por: 'pt', portuguese: 'pt',
  rus: 'ru', russian: 'ru',
  zho: 'zh', chi: 'zh', chinese: 'zh',
  jpn: 'ja', japanese: 'ja',
  kor: 'ko', korean: 'ko',
  pol: 'pl', polish: 'pl',
  nld: 'nl', dut: 'nl', dutch: 'nl',
  swe: 'sv', swedish: 'sv',
  ara: 'ar', arabic: 'ar',
  hin: 'hi', hindi: 'hi',
  tur: 'tr', turkish: 'tr',
  ces: 'cs', cze: 'cs', czech: 'cs',
  dan: 'da', danish: 'da',
  fin: 'fi', finnish: 'fi',
  nor: 'no', norwegian: 'no',
  ukr: 'uk', ukrainian: 'uk',
  heb: 'he', hebrew: 'he',
  ell: 'el', gre: 'el', greek: 'el',
  hun: 'hu', hungarian: 'hu',
  ron: 'ro', rum: 'ro', romanian: 'ro',
  tha: 'th', thai: 'th',
  vie: 'vi', vietnamese: 'vi',
  ind: 'id', indonesian: 'id',
  msa: 'ms', may: 'ms', malay: 'ms'
};

type DisplayNamesType = 'language' | 'region' | 'script';

@Injectable({providedIn: 'root'})
export class LanguageResolverService {
  private readonly translocoService = inject(TranslocoService);
  private readonly displayNamesCache = new Map<string, Intl.DisplayNames | null>();

  public readonly activeLocale = toSignal(this.translocoService.langChanges$, {
    initialValue: this.translocoService.getActiveLang()
  });

  resolve(raw: string | null | undefined): ResolvedLanguage | null {
    const trimmed = raw?.trim();
    if (!trimmed) {
      return null;
    }

    const {base, tag} = this.canonicalize(trimmed.toLowerCase());
    const locale = this.activeLocale() || 'en';

    const resolvedName = this.lookup(locale, 'language', base);
    const recognized = resolvedName !== null;
    const languageName = this.capitalize(resolvedName ?? base, locale);
    const secondaryName = this.secondaryName(locale, tag, base);
    const regionName = secondaryName ? this.capitalize(secondaryName, locale) : null;
    const resolvedDisplayName = (recognized ? this.lookup(locale, 'language', tag) : null)
      ?? (regionName ? `${languageName} (${regionName})` : languageName);

    return {
      raw: trimmed,
      tag,
      base,
      languageName,
      regionName,
      displayName: this.capitalize(resolvedDisplayName, locale),
      recognized
    };
  }

  displayName(raw: string | null | undefined): string {
    return this.resolve(raw)?.displayName ?? '';
  }

  private canonicalize(lower: string): {base: string; tag: string} {
    const aliased = LANGUAGE_ALIASES[lower];
    if (aliased) {
      return {base: aliased, tag: aliased};
    }

    try {
      const canonical = Intl.getCanonicalLocales(lower.replace(/_/g, '-'))[0];
      if (!canonical) {
        return {base: lower, tag: lower};
      }
      const subtags = canonical.split('-');
      const base = LANGUAGE_ALIASES[subtags[0].toLowerCase()] ?? subtags[0].toLowerCase();
      return {base, tag: [base, ...subtags.slice(1)].join('-')};
    } catch {
      return {base: lower, tag: lower};
    }
  }

  private secondaryName(locale: string, tag: string, base: string): string | null {
    if (tag === base) {
      return null;
    }
    try {
      const parsed = new Intl.Locale(tag);
      if (parsed.region) {
        return this.lookup(locale, 'region', parsed.region) ?? parsed.region;
      }
      if (parsed.script) {
        return this.lookup(locale, 'script', parsed.script) ?? parsed.script;
      }
    } catch {
      return tag.slice(base.length + 1);
    }
    return tag.slice(base.length + 1);
  }

  private lookup(locale: string, type: DisplayNamesType, code: string): string | null {
    const displayNames = this.displayNames(locale, type) ?? this.displayNames('en', type);
    if (!displayNames) {
      return null;
    }
    try {
      const name = displayNames.of(code);
      return name && name.toLowerCase() !== code.toLowerCase() ? name : null;
    } catch {
      return null;
    }
  }

  private displayNames(locale: string, type: DisplayNamesType): Intl.DisplayNames | null {
    const key = `${locale}:${type}`;
    let instance = this.displayNamesCache.get(key);
    if (instance === undefined) {
      try {
        instance = new Intl.DisplayNames([locale], {type, languageDisplay: 'standard'});
      } catch {
        instance = null;
      }
      this.displayNamesCache.set(key, instance);
    }
    return instance;
  }

  private capitalize(value: string, locale: string): string {
    try {
      return value.charAt(0).toLocaleUpperCase(locale) + value.slice(1);
    } catch {
      return value.charAt(0).toUpperCase() + value.slice(1);
    }
  }
}
