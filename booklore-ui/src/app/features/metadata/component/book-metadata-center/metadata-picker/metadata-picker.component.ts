import {Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
import {Book, BookMetadata, MetadataClearFlags, MetadataUpdateWrapper} from '../../../../book/model/book.model';
import {MessageService} from 'primeng/api';
import {Button} from 'primeng/button';
import {FormControl, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {AsyncPipe, NgClass, NgStyle} from '@angular/common';
import {Divider} from 'primeng/divider';
import {Observable} from 'rxjs';
import {Tooltip} from 'primeng/tooltip';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {BookService} from '../../../../book/service/book.service';
import {Textarea} from 'primeng/textarea';
import {filter, map, take} from 'rxjs/operators';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AutoComplete} from 'primeng/autocomplete';
import {Image} from 'primeng/image';
import {LazyLoadImageModule} from 'ng-lazyload-image';

@Component({
  selector: 'app-metadata-picker',
  standalone: true,
  templateUrl: './metadata-picker.component.html',
  styleUrls: ['./metadata-picker.component.scss'],
  imports: [
    Button,
    FormsModule,
    InputText,
    Divider,
    ReactiveFormsModule,
    NgClass,
    NgStyle,
    Tooltip,
    AsyncPipe,
    Textarea,
    AutoComplete,
    Image,
    LazyLoadImageModule
  ]
})
export class MetadataPickerComponent implements OnInit {

  metadataFieldsTop = [
    {label: 'Title', controlName: 'title', lockedKey: 'titleLocked', fetchedKey: 'title'},
    {label: 'Subtitle', controlName: 'subtitle', lockedKey: 'subtitleLocked', fetchedKey: 'subtitle'},
    {label: 'Publisher', controlName: 'publisher', lockedKey: 'publisherLocked', fetchedKey: 'publisher'},
    {label: 'Published', controlName: 'publishedDate', lockedKey: 'publishedDateLocked', fetchedKey: 'publishedDate'}
  ];

  metadataChips = [
    {label: 'Authors', controlName: 'authors', lockedKey: 'authorsLocked', fetchedKey: 'authors'},
    {label: 'Genres', controlName: 'categories', lockedKey: 'categoriesLocked', fetchedKey: 'categories'},
    {label: 'Moods', controlName: 'moods', lockedKey: 'moodsLocked', fetchedKey: 'moods'},
    {label: 'Tags', controlName: 'tags', lockedKey: 'tagsLocked', fetchedKey: 'tags'},
  ];

  metadataDescription = [
    {label: 'Description', controlName: 'description', lockedKey: 'descriptionLocked', fetchedKey: 'description'},
  ];

  metadataFieldsBottom = [
    {label: 'Series', controlName: 'seriesName', lockedKey: 'seriesNameLocked', fetchedKey: 'seriesName'},
    {label: 'Book #', controlName: 'seriesNumber', lockedKey: 'seriesNumberLocked', fetchedKey: 'seriesNumber'},
    {label: 'Total Books', controlName: 'seriesTotal', lockedKey: 'seriesTotalLocked', fetchedKey: 'seriesTotal'},
    {label: 'Language', controlName: 'language', lockedKey: 'languageLocked', fetchedKey: 'language'},
    {label: 'ISBN-10', controlName: 'isbn10', lockedKey: 'isbn10Locked', fetchedKey: 'isbn10'},
    {label: 'ISBN-13', controlName: 'isbn13', lockedKey: 'isbn13Locked', fetchedKey: 'isbn13'},
    {label: 'ASIN', controlName: 'asin', lockedKey: 'asinLocked', fetchedKey: 'asin'},
    {label: 'Amz Reviews', controlName: 'amazonReviewCount', lockedKey: 'amazonReviewCountLocked', fetchedKey: 'amazonReviewCount'},
    {label: 'Amz Rating', controlName: 'amazonRating', lockedKey: 'amazonRatingLocked', fetchedKey: 'amazonRating'},
    {label: 'Comicvine ID', controlName: 'comicvineId', lockedKey: 'comicvineIdLocked', fetchedKey: 'comicvineId'},
    {label: 'Goodreads ID', controlName: 'goodreadsId', lockedKey: 'goodreadsIdLocked', fetchedKey: 'goodreadsId'},
    {label: 'GR Reviews', controlName: 'goodreadsReviewCount', lockedKey: 'goodreadsReviewCountLocked', fetchedKey: 'goodreadsReviewCount'},
    {label: 'GR Rating', controlName: 'goodreadsRating', lockedKey: 'goodreadsRatingLocked', fetchedKey: 'goodreadsRating'},
    {label: 'Hardcover ID', controlName: 'hardcoverId', lockedKey: 'hardcoverIdLocked', fetchedKey: 'hardcoverId'},
    {label: 'HC Reviews', controlName: 'hardcoverReviewCount', lockedKey: 'hardcoverReviewCountLocked', fetchedKey: 'hardcoverReviewCount'},
    {label: 'HC Rating', controlName: 'hardcoverRating', lockedKey: 'hardcoverRatingLocked', fetchedKey: 'hardcoverRating'},
    {label: 'Google ID', controlName: 'googleId', lockedKey: 'googleIdLocked', fetchedKey: 'googleIdRating'},
    {label: 'Pages', controlName: 'pageCount', lockedKey: 'pageCountLocked', fetchedKey: 'pageCount'}
  ];

  @Input() reviewMode!: boolean;
  @Input() fetchedMetadata!: BookMetadata;
  @Input() book$!: Observable<Book | null>;
  @Output() goBack = new EventEmitter<boolean>();

  allAuthors!: string[];
  allCategories!: string[];
  allMoods!: string[];
  allTags!: string[];
  filteredCategories: string[] = [];
  filteredAuthors: string[] = [];
  filteredMoods: string[] = [];
  filteredTags: string[] = [];

  getFiltered(controlName: string): string[] {
    if (controlName === 'authors') return this.filteredAuthors;
    if (controlName === 'categories') return this.filteredCategories;
    if (controlName === 'moods') return this.filteredMoods;
    if (controlName === 'tags') return this.filteredTags;
    return [];
  }

  filterItems(event: { query: string }, controlName: string) {
    const query = event.query.toLowerCase();
    if (controlName === 'authors') {
      this.filteredAuthors = this.allAuthors.filter(a => a.toLowerCase().includes(query));
    } else if (controlName === 'categories') {
      this.filteredCategories = this.allCategories.filter(c => c.toLowerCase().includes(query));
    } else if (controlName === 'moods') {
      this.filteredMoods = this.allMoods.filter(m => m.toLowerCase().includes(query));
    } else if (controlName === 'tags') {
      this.filteredTags = this.allTags.filter(t => t.toLowerCase().includes(query));
    }
  }

  metadataForm: FormGroup;
  currentBookId!: number;
  copiedFields: Record<string, boolean> = {};
  savedFields: Record<string, boolean> = {};
  originalMetadata!: BookMetadata;
  isSaving = false;

  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  protected urlHelper = inject(UrlHelperService);
  private destroyRef = inject(DestroyRef);

  constructor() {
    this.metadataForm = new FormGroup({
      title: new FormControl(''),
      subtitle: new FormControl(''),
      authors: new FormControl(''),
      categories: new FormControl(''),
      moods: new FormControl(''),
      tags: new FormControl(''),
      publisher: new FormControl(''),
      publishedDate: new FormControl(''),
      isbn10: new FormControl(''),
      isbn13: new FormControl(''),
      description: new FormControl(''),
      pageCount: new FormControl(''),
      language: new FormControl(''),
      asin: new FormControl(''),
      amazonRating: new FormControl(''),
      amazonReviewCount: new FormControl(''),
      goodreadsId: new FormControl(''),
      comicvineId: new FormControl(''),
      goodreadsRating: new FormControl(''),
      goodreadsReviewCount: new FormControl(''),
      hardcoverId: new FormControl(''),
      hardcoverRating: new FormControl(''),
      hardcoverReviewCount: new FormControl(''),
      googleId: new FormControl(''),
      seriesName: new FormControl(''),
      seriesNumber: new FormControl(''),
      seriesTotal: new FormControl(''),
      thumbnailUrl: new FormControl(''),

      titleLocked: new FormControl(false),
      subtitleLocked: new FormControl(false),
      authorsLocked: new FormControl(false),
      categoriesLocked: new FormControl(false),
      moodsLocked: new FormControl(false),
      tagsLocked: new FormControl(false),
      publisherLocked: new FormControl(false),
      publishedDateLocked: new FormControl(false),
      isbn10Locked: new FormControl(false),
      isbn13Locked: new FormControl(false),
      descriptionLocked: new FormControl(false),
      pageCountLocked: new FormControl(false),
      languageLocked: new FormControl(false),
      asinLocked: new FormControl(false),
      personalRatingLocked: new FormControl(false),
      amazonRatingLocked: new FormControl(false),
      amazonReviewCountLocked: new FormControl(false),
      goodreadsIdLocked: new FormControl(false),
      comicvineIdLocked: new FormControl(false),
      goodreadsRatingLocked: new FormControl(false),
      goodreadsReviewCountLocked: new FormControl(false),
      hardcoverIdLocked: new FormControl(false),
      hardcoverRatingLocked: new FormControl(false),
      hardcoverReviewCountLocked: new FormControl(false),
      googleIdLocked: new FormControl(false),
      seriesNameLocked: new FormControl(false),
      seriesNumberLocked: new FormControl(false),
      seriesTotalLocked: new FormControl(false),
      coverLocked: new FormControl(false),
    });
  }

  ngOnInit(): void {

    this.bookService.bookState$
      .pipe(
        filter(bookState => bookState.loaded),
        take(1)
      )
      .subscribe(bookState => {
        const authors = new Set<string>();
        const categories = new Set<string>();
        const moods = new Set<string>();
        const tags = new Set<string>();

        (bookState.books ?? []).forEach(book => {
          book.metadata?.authors?.forEach(author => authors.add(author));
          book.metadata?.categories?.forEach(category => categories.add(category));
          book.metadata?.moods?.forEach(mood => moods.add(mood));
          book.metadata?.tags?.forEach(tag => tags.add(tag));
        });

        this.allAuthors = Array.from(authors);
        this.allCategories = Array.from(categories);
        this.allMoods = Array.from(moods);
        this.allTags = Array.from(tags);
      });

    this.book$
      .pipe(
        filter((book): book is Book => !!book && !!book.metadata),
        map(book => book.metadata),
        takeUntilDestroyed(this.destroyRef)
      ).subscribe((metadata) => {

      if (this.reviewMode) {
        this.metadataForm.reset();
        this.copiedFields = {};
        this.savedFields = {};
        this.hoveredFields = {};
      }

      if (metadata) {
        this.originalMetadata = metadata;
        this.originalMetadata.thumbnailUrl = this.urlHelper.getThumbnailUrl(metadata.bookId, metadata.coverUpdatedOn);
        this.currentBookId = metadata.bookId;
        this.metadataForm.patchValue({
          title: metadata.title || null,
          subtitle: metadata.subtitle || null,
          authors: [...(metadata.authors ?? [])].sort(),
          categories: [...(metadata.categories ?? [])].sort(),
          moods: [...(metadata.moods ?? [])].sort(),
          tags: [...(metadata.tags ?? [])].sort(),
          publisher: metadata.publisher || null,
          publishedDate: metadata.publishedDate || null,
          isbn10: metadata.isbn10 || null,
          isbn13: metadata.isbn13 || null,
          description: metadata.description || null,
          pageCount: metadata.pageCount || null,
          language: metadata.language || null,
          asin: metadata.asin || null,
          amazonRating: metadata.amazonRating || null,
          amazonReviewCount: metadata.amazonReviewCount || null,
          goodreadsId: metadata.goodreadsId || null,
          comicvineId: metadata.comicvineId || null,
          goodreadsRating: metadata.goodreadsRating || null,
          goodreadsReviewCount: metadata.goodreadsReviewCount || null,
          hardcoverId: metadata.hardcoverId || null,
          hardcoverRating: metadata.hardcoverRating || null,
          hardcoverReviewCount: metadata.hardcoverReviewCount || null,
          googleId: metadata.googleId || null,
          seriesName: metadata.seriesName || null,
          seriesNumber: metadata.seriesNumber || null,
          seriesTotal: metadata.seriesTotal || null,
          thumbnailUrl: this.urlHelper.getCoverUrl(metadata.bookId, metadata.coverUpdatedOn),

          titleLocked: metadata.titleLocked || false,
          subtitleLocked: metadata.subtitleLocked || false,
          authorsLocked: metadata.authorsLocked || false,
          categoriesLocked: metadata.categoriesLocked || false,
          moodsLocked: metadata.moodsLocked || false,
          tagsLocked: metadata.tagsLocked || false,
          publisherLocked: metadata.publisherLocked || false,
          publishedDateLocked: metadata.publishedDateLocked || false,
          isbn10Locked: metadata.isbn10Locked || false,
          isbn13Locked: metadata.isbn13Locked || false,
          descriptionLocked: metadata.descriptionLocked || false,
          pageCountLocked: metadata.pageCountLocked || false,
          languageLocked: metadata.languageLocked || false,
          asinLocked: metadata.asinLocked || false,
          personalRatingLocked: metadata.personalRatingLocked || false,
          amazonRatingLocked: metadata.amazonRatingLocked || false,
          amazonReviewCountLocked: metadata.amazonReviewCountLocked || false,
          goodreadsIdLocked: metadata.goodreadsIdLocked || false,
          comicvineIdLocked: metadata.comicvineIdLocked || false,
          goodreadsRatingLocked: metadata.goodreadsRatingLocked || false,
          goodreadsReviewCountLocked: metadata.goodreadsReviewCountLocked || false,
          hardcoverIdLocked: metadata.hardcoverIdLocked || false,
          hardcoverRatingLocked: metadata.hardcoverRatingLocked || false,
          hardcoverReviewCountLocked: metadata.hardcoverReviewCountLocked || false,
          googleIdLocked: metadata.googleIdLocked || false,
          seriesNameLocked: metadata.seriesNameLocked || false,
          seriesNumberLocked: metadata.seriesNumberLocked || false,
          seriesTotalLocked: metadata.seriesTotalLocked || false,
          coverLocked: metadata.coverLocked || false,
        });

        // Always enable all fields before applying locked state
        Object.keys(this.metadataForm.controls).forEach((key) => {
          if (!key.endsWith('Locked')) {
            this.metadataForm.get(key)?.enable({emitEvent: false});
          }
        });

        if (metadata.titleLocked) this.metadataForm.get('title')?.disable({emitEvent: false});
        if (metadata.subtitleLocked) this.metadataForm.get('subtitle')?.disable({emitEvent: false});
        if (metadata.authorsLocked) this.metadataForm.get('authors')?.disable({emitEvent: false});
        if (metadata.categoriesLocked) this.metadataForm.get('categories')?.disable({emitEvent: false});
        if (metadata.moodsLocked) this.metadataForm.get('moods')?.disable({emitEvent: false});
        if (metadata.tagsLocked) this.metadataForm.get('tags')?.disable({emitEvent: false});
        if (metadata.publisherLocked) this.metadataForm.get('publisher')?.disable({emitEvent: false});
        if (metadata.publishedDateLocked) this.metadataForm.get('publishedDate')?.disable({emitEvent: false});
        if (metadata.languageLocked) this.metadataForm.get('language')?.disable({emitEvent: false});
        if (metadata.isbn10Locked) this.metadataForm.get('isbn10')?.disable({emitEvent: false});
        if (metadata.isbn13Locked) this.metadataForm.get('isbn13')?.disable({emitEvent: false});
        if (metadata.asinLocked) this.metadataForm.get('asin')?.disable({emitEvent: false});
        if (metadata.personalRatingLocked) this.metadataForm.get('personalRating')?.disable({emitEvent: false});
        if (metadata.amazonReviewCountLocked) this.metadataForm.get('amazonReviewCount')?.disable({emitEvent: false});
        if (metadata.amazonRatingLocked) this.metadataForm.get('amazonRating')?.disable({emitEvent: false});
        if (metadata.googleIdLocked) this.metadataForm.get('googleIdCount')?.disable({emitEvent: false});
        if (metadata.comicvineIdLocked) this.metadataForm.get('comicvineId')?.disable({emitEvent: false});
        if (metadata.goodreadsIdLocked) this.metadataForm.get('goodreadsId')?.disable({emitEvent: false});
        if (metadata.goodreadsReviewCountLocked) this.metadataForm.get('goodreadsReviewCount')?.disable({emitEvent: false});
        if (metadata.goodreadsRatingLocked) this.metadataForm.get('goodreadsRating')?.disable({emitEvent: false});
        if (metadata.hardcoverIdLocked) this.metadataForm.get('hardcoverId')?.disable({emitEvent: false});
        if (metadata.hardcoverReviewCountLocked) this.metadataForm.get('hardcoverReviewCount')?.disable({emitEvent: false});
        if (metadata.hardcoverRatingLocked) this.metadataForm.get('hardcoverRating')?.disable({emitEvent: false});
        if (metadata.googleIdLocked) this.metadataForm.get('googleId')?.disable({emitEvent: false});
        if (metadata.pageCountLocked) this.metadataForm.get('pageCount')?.disable({emitEvent: false});
        if (metadata.descriptionLocked) this.metadataForm.get('description')?.disable({emitEvent: false});
        if (metadata.seriesNameLocked) this.metadataForm.get('seriesName')?.disable({emitEvent: false});
        if (metadata.seriesNumberLocked) this.metadataForm.get('seriesNumber')?.disable({emitEvent: false});
        if (metadata.seriesTotalLocked) this.metadataForm.get('seriesTotal')?.disable({emitEvent: false});
      }
    });
  }

  onAutoCompleteSelect(fieldName: string, event: any) {
    const values = this.metadataForm.get(fieldName)?.value || [];
    if (!values.includes(event.value)) {
      this.metadataForm.get(fieldName)?.setValue([...values, event.value]);
    }
    (event.originalEvent.target as HTMLInputElement).value = '';
  }

  onAutoCompleteKeyUp(fieldName: string, event: KeyboardEvent) {
    if (event.key === 'Enter') {
      const input = event.target as HTMLInputElement;
      const value = input.value?.trim();
      if (value) {
        const values = this.metadataForm.get(fieldName)?.value || [];
        if (!values.includes(value)) {
          this.metadataForm.get(fieldName)?.setValue([...values, value]);
        }
        input.value = '';
      }
    }
  }

  onSave(): void {
    this.isSaving = true;
    const updatedBookMetadata = this.buildMetadataWrapper(undefined);
    this.bookService.updateBookMetadata(this.currentBookId, updatedBookMetadata, false).subscribe({
      next: (bookMetadata) => {
        this.isSaving = false;
        Object.keys(this.copiedFields).forEach((field) => {
          if (this.copiedFields[field]) {
            this.savedFields[field] = true;
          }
        });
        this.messageService.add({severity: 'info', summary: 'Success', detail: 'Book metadata updated'});
      },
      error: () => {
        this.isSaving = false;
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update book metadata'});
      }
    });
  }

  private buildMetadataWrapper(shouldLockAllFields: boolean | undefined): MetadataUpdateWrapper {
    const updatedBookMetadata: BookMetadata = {
      bookId: this.currentBookId,
      title: this.metadataForm.get('title')?.value || this.copiedFields['title'] ? this.getValueOrCopied('title') : '',
      subtitle: this.metadataForm.get('subtitle')?.value || this.copiedFields['subtitle'] ? this.getValueOrCopied('subtitle') : '',
      authors: this.metadataForm.get('authors')?.value || this.copiedFields['authors'] ? this.getArrayFromFormField('authors', this.fetchedMetadata.authors) : [],
      categories: this.metadataForm.get('categories')?.value || this.copiedFields['categories'] ? this.getArrayFromFormField('categories', this.fetchedMetadata.categories) : [],
      moods: this.metadataForm.get('moods')?.value || this.copiedFields['moods'] ? this.getArrayFromFormField('moods', this.fetchedMetadata.moods) : [],
      tags: this.metadataForm.get('tags')?.value || this.copiedFields['tags'] ? this.getArrayFromFormField('tags', this.fetchedMetadata.tags) : [],
      publisher: this.metadataForm.get('publisher')?.value || this.copiedFields['publisher'] ? this.getValueOrCopied('publisher') : '',
      publishedDate: this.metadataForm.get('publishedDate')?.value || this.copiedFields['publishedDate'] ? this.getValueOrCopied('publishedDate') : '',
      isbn10: this.metadataForm.get('isbn10')?.value || this.copiedFields['isbn10'] ? this.getValueOrCopied('isbn10') : '',
      isbn13: this.metadataForm.get('isbn13')?.value || this.copiedFields['isbn13'] ? this.getValueOrCopied('isbn13') : '',
      description: this.metadataForm.get('description')?.value || this.copiedFields['description'] ? this.getValueOrCopied('description') : '',
      pageCount: this.metadataForm.get('pageCount')?.value || this.copiedFields['pageCount'] ? this.getPageCountOrCopied() : null,
      language: this.metadataForm.get('language')?.value || this.copiedFields['language'] ? this.getValueOrCopied('language') : '',
      asin: this.metadataForm.get('asin')?.value || this.copiedFields['asin'] ? this.getValueOrCopied('asin') : '',
      personalRating: this.metadataForm.get('personalRating')?.value || this.copiedFields['personalRating'] ? this.getNumberOrCopied('personalRating') : null,
      amazonRating: this.metadataForm.get('amazonRating')?.value || this.copiedFields['amazonRating'] ? this.getNumberOrCopied('amazonRating') : null,
      amazonReviewCount: this.metadataForm.get('amazonReviewCount')?.value || this.copiedFields['amazonReviewCount'] ? this.getNumberOrCopied('amazonReviewCount') : null,
      goodreadsId: this.metadataForm.get('goodreadsId')?.value || this.copiedFields['goodreadsId'] ? this.getValueOrCopied('goodreadsId') : '',
      comicvineId: this.metadataForm.get('comicvineId')?.value || this.copiedFields['comicvineId'] ? this.getValueOrCopied('comicvineId') : '',
      goodreadsRating: this.metadataForm.get('goodreadsRating')?.value || this.copiedFields['goodreadsRating'] ? this.getNumberOrCopied('goodreadsRating') : null,
      goodreadsReviewCount: this.metadataForm.get('goodreadsReviewCount')?.value || this.copiedFields['goodreadsReviewCount'] ? this.getNumberOrCopied('goodreadsReviewCount') : null,
      hardcoverId: this.metadataForm.get('hardcoverId')?.value || this.copiedFields['hardcoverId'] ? this.getValueOrCopied('hardcoverId') : '',
      hardcoverRating: this.metadataForm.get('hardcoverRating')?.value || this.copiedFields['hardcoverRating'] ? this.getNumberOrCopied('hardcoverRating') : null,
      hardcoverReviewCount: this.metadataForm.get('hardcoverReviewCount')?.value || this.copiedFields['hardcoverReviewCount'] ? this.getNumberOrCopied('hardcoverReviewCount') : null,
      googleId: this.metadataForm.get('googleId')?.value || this.copiedFields['googleId'] ? this.getValueOrCopied('googleId') : '',
      seriesName: this.metadataForm.get('seriesName')?.value || this.copiedFields['seriesName'] ? this.getValueOrCopied('seriesName') : '',
      seriesNumber: this.metadataForm.get('seriesNumber')?.value || this.copiedFields['seriesNumber'] ? this.getNumberOrCopied('seriesNumber') : null,
      seriesTotal: this.metadataForm.get('seriesTotal')?.value || this.copiedFields['seriesTotal'] ? this.getNumberOrCopied('seriesTotal') : null,
      thumbnailUrl: this.getThumbnail(),

      titleLocked: this.metadataForm.get('titleLocked')?.value,
      subtitleLocked: this.metadataForm.get('subtitleLocked')?.value,
      authorsLocked: this.metadataForm.get('authorsLocked')?.value,
      categoriesLocked: this.metadataForm.get('categoriesLocked')?.value,
      moodsLocked: this.metadataForm.get('moodsLocked')?.value,
      tagsLocked: this.metadataForm.get('tagsLocked')?.value,
      publisherLocked: this.metadataForm.get('publisherLocked')?.value,
      publishedDateLocked: this.metadataForm.get('publishedDateLocked')?.value,
      isbn10Locked: this.metadataForm.get('isbn10Locked')?.value,
      isbn13Locked: this.metadataForm.get('isbn13Locked')?.value,
      descriptionLocked: this.metadataForm.get('descriptionLocked')?.value,
      pageCountLocked: this.metadataForm.get('pageCountLocked')?.value,
      languageLocked: this.metadataForm.get('languageLocked')?.value,
      asinLocked: this.metadataForm.get('asinLocked')?.value,
      personalRatingLocked: this.metadataForm.get('personalRatingLocked')?.value,
      amazonRatingLocked: this.metadataForm.get('amazonRatingLocked')?.value,
      amazonReviewCountLocked: this.metadataForm.get('amazonReviewCountLocked')?.value,
      goodreadsIdLocked: this.metadataForm.get('goodreadsIdLocked')?.value,
      comicvineIdLocked: this.metadataForm.get('comicvineIdLocked')?.value,
      goodreadsRatingLocked: this.metadataForm.get('goodreadsRatingLocked')?.value,
      goodreadsReviewCountLocked: this.metadataForm.get('goodreadsReviewCountLocked')?.value,
      hardcoverIdLocked: this.metadataForm.get('hardcoverIdLocked')?.value,
      hardcoverRatingLocked: this.metadataForm.get('hardcoverRatingLocked')?.value,
      hardcoverReviewCountLocked: this.metadataForm.get('hardcoverReviewCountLocked')?.value,
      googleIdLocked: this.metadataForm.get('googleIdLocked')?.value,
      seriesNameLocked: this.metadataForm.get('seriesNameLocked')?.value,
      seriesNumberLocked: this.metadataForm.get('seriesNumberLocked')?.value,
      seriesTotalLocked: this.metadataForm.get('seriesTotalLocked')?.value,
      coverLocked: this.metadataForm.get('coverLocked')?.value,

      ...(shouldLockAllFields !== undefined && {allFieldsLocked: shouldLockAllFields}),
    };

    const clearFlags: MetadataClearFlags = this.inferClearFlags(updatedBookMetadata, this.originalMetadata);

    return {
      metadata: updatedBookMetadata,
      clearFlags: clearFlags
    };
  }

  private inferClearFlags(current: BookMetadata, original: BookMetadata): MetadataClearFlags {
    return {
      title: !current.title && !!original.title,
      subtitle: !current.subtitle && !!original.subtitle,
      authors: current.authors?.length === 0 && original.authors?.length! > 0,
      categories: current.categories?.length === 0 && original.categories?.length! > 0,
      moods: current.moods?.length === 0 && original.moods?.length! > 0,
      tags: current.tags?.length === 0 && original.tags?.length! > 0,
      publisher: !current.publisher && !!original.publisher,
      publishedDate: !current.publishedDate && !!original.publishedDate,
      isbn10: !current.isbn10 && !!original.isbn10,
      isbn13: !current.isbn13 && !!original.isbn13,
      description: !current.description && !!original.description,
      pageCount: current.pageCount === null && original.pageCount !== null,
      language: !current.language && !!original.language,
      asin: !current.asin && !!original.asin,
      amazonRating: current.amazonRating === null && original.amazonRating !== null,
      amazonReviewCount: current.amazonReviewCount === null && original.amazonReviewCount !== null,
      goodreadsId: !current.goodreadsId && !!original.goodreadsId,
      comicvineId: !current.comicvineId && !!original.comicvineId,
      goodreadsRating: current.goodreadsRating === null && original.goodreadsRating !== null,
      goodreadsReviewCount: current.goodreadsReviewCount === null && original.goodreadsReviewCount !== null,
      hardcoverId: !current.hardcoverId && !!original.hardcoverId,
      hardcoverRating: current.hardcoverRating === null && original.hardcoverRating !== null,
      hardcoverReviewCount: current.hardcoverReviewCount === null && original.hardcoverReviewCount !== null,
      googleId: !current.googleId && !!original.googleId,
      seriesName: !current.seriesName && !!original.seriesName,
      seriesNumber: current.seriesNumber === null && original.seriesNumber !== null,
      seriesTotal: current.seriesTotal === null && original.seriesTotal !== null,
      cover: !current.thumbnailUrl && !!original.thumbnailUrl,
      personalRating: current.personalRating === null && original.personalRating !== null,
    };
  }

  getThumbnail() {
    const thumbnailUrl = this.metadataForm.get('thumbnailUrl')?.value;
    if (thumbnailUrl?.includes('api/v1')) {
      return null;
    }
    return this.copiedFields['thumbnailUrl'] ? this.getValueOrCopied('thumbnailUrl') : null;
  }

  private updateMetadata(shouldLockAllFields: boolean | undefined): void {
    this.bookService.updateBookMetadata(this.currentBookId, this.buildMetadataWrapper(shouldLockAllFields), false).subscribe({
      next: (response) => {
        if (shouldLockAllFields !== undefined) {
          this.messageService.add({
            severity: 'success',
            summary: shouldLockAllFields ? 'Metadata Locked' : 'Metadata Unlocked',
            detail: shouldLockAllFields
              ? 'All fields have been successfully locked.'
              : 'All fields have been successfully unlocked.',
          });
        }
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to update lock state',
        });
      }
    });
  }

  toggleLock(field: string): void {
    if (field === 'thumbnailUrl') {
      field = 'cover'
    }
    const isLocked = this.metadataForm.get(field + 'Locked')?.value;
    const updatedLockedState = !isLocked;
    this.metadataForm.get(field + 'Locked')?.setValue(updatedLockedState);
    if (updatedLockedState) {
      this.metadataForm.get(field)?.disable();
    } else {
      this.metadataForm.get(field)?.enable();
    }
    this.updateMetadata(undefined);
  }

  copyMissing(): void {
    Object.keys(this.fetchedMetadata).forEach((field) => {
      const isLocked = this.metadataForm.get(`${field}Locked`)?.value;
      const currentValue = this.metadataForm.get(field)?.value;
      const fetchedValue = this.fetchedMetadata[field];

      const isEmpty = Array.isArray(currentValue)
        ? currentValue.length === 0
        : !currentValue;

      if (!isLocked && isEmpty && fetchedValue) {
        this.copyFetchedToCurrent(field);
      }
    });
  }

  copyAll() {
    Object.keys(this.fetchedMetadata).forEach((field) => {
      const isLocked = this.metadataForm.get(`${field}Locked`)?.value;
      if (!isLocked && this.fetchedMetadata[field] && field !== 'thumbnailUrl') {
        this.copyFetchedToCurrent(field);
      }
    });
  }

  copyFetchedToCurrent(field: string): void {
    if (field === 'thumbnailUrl') {
      field = 'cover';
    }
    const isLocked = this.metadataForm.get(`${field}Locked`)?.value;
    if (isLocked) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Action Blocked',
        detail: `${field} is locked and cannot be updated.`
      });
      return;
    }
    if (field === 'cover') {
      field = 'thumbnailUrl';
    }
    const value = this.fetchedMetadata[field];
    if (value) {
      this.metadataForm.get(field)?.setValue(value);
      this.copiedFields[field] = true;
      this.highlightCopiedInput(field);
    }
  }

  private getNumberOrCopied(field: string): number | null {
    const formValue = this.metadataForm.get(field)?.value;
    if (formValue === '' || formValue === null || isNaN(formValue)) {
      this.copiedFields[field] = true;
      return this.fetchedMetadata[field] || null;
    }
    return Number(formValue);
  }

  private getPageCountOrCopied(): number | null {
    const formValue = this.metadataForm.get('pageCount')?.value;
    if (formValue === '' || formValue === null || isNaN(formValue)) {
      this.copiedFields['pageCount'] = true;
      return this.fetchedMetadata.pageCount || null;
    }
    return Number(formValue);
  }

  private getValueOrCopied(field: string): string {
    const formValue = this.metadataForm.get(field)?.value;
    if (!formValue || formValue === '') {
      this.copiedFields[field] = true;
      return this.fetchedMetadata[field] || '';
    }
    return formValue;
  }

  getArrayFromFormField(field: string, fallbackValue: any): any[] {
    const fieldValue = this.metadataForm.get(field)?.value;
    if (!fieldValue) {
      return fallbackValue ? (Array.isArray(fallbackValue) ? fallbackValue : [fallbackValue]) : [];
    }
    if (typeof fieldValue === 'string') {
      return fieldValue.split(',').map(item => item.trim());
    }
    return Array.isArray(fieldValue) ? fieldValue : [];
  }

  lockAll(): void {
    Object.keys(this.metadataForm.controls).forEach((key) => {
      if (key.endsWith('Locked')) {
        this.metadataForm.get(key)?.setValue(true);
        const fieldName = key.replace('Locked', '');
        this.metadataForm.get(fieldName)?.disable();
      }
    });
    this.updateMetadata(true);
  }

  unlockAll(): void {
    Object.keys(this.metadataForm.controls).forEach((key) => {
      if (key.endsWith('Locked')) {
        this.metadataForm.get(key)?.setValue(false);
        const fieldName = key.replace('Locked', '');
        this.metadataForm.get(fieldName)?.enable();
      }
    });
    this.updateMetadata(false);
  }

  highlightCopiedInput(field: string): void {
    this.copiedFields = {...this.copiedFields, [field]: true};
  }

  isValueCopied(field: string): boolean {
    return this.copiedFields[field];
  }

  isValueSaved(field: string): boolean {
    return this.savedFields[field];
  }

  goBackClick() {
    this.goBack.emit(true);
  }

  hoveredFields: { [key: string]: boolean } = {};

  onMouseEnter(controlName: string): void {
    if (this.isValueCopied(controlName) && !this.isValueSaved(controlName)) {
      this.hoveredFields[controlName] = true;
    }
  }

  onMouseLeave(controlName: string): void {
    this.hoveredFields[controlName] = false;
  }

  resetField(field: string) {
    this.metadataForm.get(field)?.setValue(this.originalMetadata[field]);
    this.copiedFields[field] = false;
    this.hoveredFields[field] = false;
  }
}
