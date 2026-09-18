import {Component, input, output, provideZonelessChangeDetection, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Subject} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {Book, BookMetadata} from '../../../../book/model/book.model';
import {BookMetadataService} from '../../../../book/service/book-metadata.service';
import {AppSettings} from '../../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {CoverComponent} from '../../../../../shared/components/cover/cover.component';
import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';
import {MetadataPickerComponent} from '../metadata-picker/metadata-picker.component';
import {MetadataSearcherComponent} from './metadata-searcher.component';

@Component({selector: 'app-metadata-picker', template: '', standalone: true})
class PickerStub {
  readonly fetchedMetadata = input<BookMetadata>();
  readonly book = input<Book | null>();
  readonly detailLoading = input(false);
  readonly goBack = output<boolean>();
}

@Component({selector: 'app-cover', template: '', standalone: true})
class CoverStub {
  readonly src = input<string | undefined>();
  readonly title = input<string | undefined>();
  readonly authors = input<string[]>();
  readonly alt = input<string>();
}

describe('MetadataSearcherComponent', () => {
  let fixture: ComponentFixture<MetadataSearcherComponent>;
  let component: MetadataSearcherComponent;
  let search$: Subject<BookMetadata>;
  let detail$: Subject<BookMetadata>;

  const appSettings = signal<AppSettings | null>(null);
  const fetchBookMetadata = vi.fn();
  const fetchMetadataDetail = vi.fn();

  const settings = (): AppSettings => ({
    autoBookSearch: false,
    metadataProviderSettings: {
      openLibrary: {enabled: true},
      goodReads: {enabled: true},
    },
  } as unknown as AppSettings);

  const book = (): Book => ({
    id: 1,
    libraryId: 1,
    metadata: {bookId: 1, title: 'Dune', authors: ['Frank Herbert'], isbn13: '9780441013593'},
  } as Book);

  const openLibraryResult = (): BookMetadata => ({
    bookId: 1,
    provider: 'OpenLibrary',
    title: 'Dune',
    openlibraryId: '/books/OL30014174M',
    goodreadsId: '53403754',
  });

  beforeEach(async () => {
    search$ = new Subject<BookMetadata>();
    detail$ = new Subject<BookMetadata>();
    appSettings.set(null);
    fetchBookMetadata.mockReset().mockReturnValue(search$.asObservable());
    fetchMetadataDetail.mockReset().mockReturnValue(detail$.asObservable());

    await TestBed.configureTestingModule({
      imports: [MetadataSearcherComponent, getTranslocoModule()],
      providers: [
        provideZonelessChangeDetection(),
        {provide: AppSettingsService, useValue: {appSettings}},
        {provide: BookMetadataService, useValue: {fetchBookMetadata, fetchMetadataDetail}},
      ],
    })
      .overrideComponent(MetadataSearcherComponent, {
        remove: {imports: [MetadataPickerComponent, CoverComponent]},
        add: {imports: [PickerStub, CoverStub]},
      })
      .compileComponents();

    fixture = TestBed.createComponent(MetadataSearcherComponent);
    component = fixture.componentInstance;
  });

  function setUp(isActiveTab = false): void {
    appSettings.set(settings());
    fixture.componentRef.setInput('book', book());
    fixture.componentRef.setInput('isActiveTab', isActiveTab);
    fixture.detectChanges();
  }

  it('keeps an OpenLibrary result even when it carries a Goodreads ID', () => {
    setUp();
    const result = openLibraryResult();
    component.onSubmit();
    search$.next(result);

    component.onBookClick(result);

    expect(fetchMetadataDetail).not.toHaveBeenCalled();
    expect(component.selected()).toBe(result);
    expect(component.detailLoading()).toBe(false);
  });

  it('keeps user-edited search terms when the tab is switched away and back', () => {
    setUp(true);
    component.form.patchValue({title: 'Dune Messiah', isbn: ''});

    fixture.componentRef.setInput('isActiveTab', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('isActiveTab', true);
    fixture.detectChanges();

    expect(component.form.value.title).toBe('Dune Messiah');
    expect(component.form.value.isbn).toBe('');
  });
});
