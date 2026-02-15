import {Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output,} from "@angular/core";
import {InputText} from "primeng/inputtext";
import {Button} from "primeng/button";
import {Divider} from "primeng/divider";
import {FormControl, FormGroup, FormsModule, ReactiveFormsModule,} from "@angular/forms";
import {Observable, sample} from "rxjs";
import {AsyncPipe} from "@angular/common";
import {MessageService} from "primeng/api";
import {Book, BookMetadata, ComicMetadata, MetadataClearFlags, MetadataUpdateWrapper,} from "../../../../book/model/book.model";
import {UrlHelperService} from "../../../../../shared/service/url-helper.service";
import {ALL_COMIC_METADATA_FIELDS, AUDIOBOOK_METADATA_FIELDS, COMIC_FORM_TO_MODEL_LOCK, COMIC_TEXT_METADATA_FIELDS, COMIC_ARRAY_METADATA_FIELDS, COMIC_TEXTAREA_METADATA_FIELDS, MetadataFieldConfig} from '../../../../../shared/metadata';
import {FileUpload, FileUploadErrorEvent, FileUploadEvent,} from "primeng/fileupload";
import {HttpResponse} from "@angular/common/http";
import {BookService} from "../../../../book/service/book.service";
import {BookMetadataManageService} from "../../../../book/service/book-metadata-manage.service";
import {ProgressSpinner} from "primeng/progressspinner";
import {Tooltip} from "primeng/tooltip";
import {filter, finalize, switchMap, take, tap} from "rxjs/operators";
import {takeUntilDestroyed} from "@angular/core/rxjs-interop";
import {MetadataRefreshType} from "../../../model/request/metadata-refresh-type.enum";
import {AutoComplete, AutoCompleteSelectEvent} from "primeng/autocomplete";
import {DatePicker} from "primeng/datepicker";
import {Textarea} from "primeng/textarea";
import {Image} from "primeng/image";
import {LazyLoadImageModule} from "ng-lazyload-image";
import {Select} from "primeng/select";
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {BookDialogHelperService} from "../../../../book/components/book-browser/book-dialog-helper.service";
import {BookNavigationService} from '../../../../book/service/book-navigation.service';
import {BookMetadataHostService} from '../../../../../shared/service/book-metadata-host.service';
import {Router} from '@angular/router';
import {UserService} from '../../../../settings/user-management/user.service';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {MetadataProviderSpecificFields} from '../../../../../shared/model/app-settings.model';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: "app-metadata-editor",
  standalone: true,
  templateUrl: "./metadata-editor.component.html",
  styleUrls: ["./metadata-editor.component.scss"],
  imports: [
    InputText,
    Button,
    Divider,
    FormsModule,
    AsyncPipe,
    ReactiveFormsModule,
    FileUpload,
    ProgressSpinner,
    Tooltip,
    AutoComplete,
    DatePicker,
    Textarea,
    Image,
    LazyLoadImageModule,
    Select,
    TranslocoDirective,
  ],
})
export class MetadataEditorComponent implements OnInit {
  @Input() book$!: Observable<Book | null>;
  @Output() nextBookClicked = new EventEmitter<void>();
  @Output() previousBookClicked = new EventEmitter<void>();
  @Output() closeDialogButtonClicked = new EventEmitter<void>();

  @Input() disableNext = false;
  @Input() disablePrevious = false;
  @Input() showNavigationButtons = false;

  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  private taskHelperService = inject(TaskHelperService);
  protected urlHelper = inject(UrlHelperService);
  private bookDialogHelperService = inject(BookDialogHelperService);
  private bookNavigationService = inject(BookNavigationService);
  private metadataHostService = inject(BookMetadataHostService);
  private router = inject(Router);
  private userService = inject(UserService);
  private destroyRef = inject(DestroyRef);
  private appSettingsService = inject(AppSettingsService);
  private readonly t = inject(TranslocoService);

  metadataForm: FormGroup;
  currentBookId!: number;
  isUploading = false;
  isLoading = false;
  isSaving = false;
  isGeneratingCover = false;
  isGeneratingAudiobookCover = false;

  refreshingBookIds = new Set<number>();
  isAutoFetching = false;
  isFetchingFromFile = false;
  autoSaveEnabled = false;

  originalMetadata!: BookMetadata;

  allAuthors!: string[];
  allCategories!: string[];
  allMoods!: string[];
  allTags!: string[];
  allPublishers!: string[];
  allSeries!: string[];
  filteredCategories: string[] = [];
  filteredAuthors: string[] = [];
  filteredMoods: string[] = [];
  filteredTags: string[] = [];
  filteredPublishers: string[] = [];
  filteredSeries: string[] = [];
  private metadataCenterViewMode: 'route' | 'dialog' = 'route';

  contentRatingOptions: {label: string, value: string}[] = [];
  ageRatingOptions: {label: string, value: number}[] = [];
  booleanOptions: {label: string, value: boolean | null}[] = [];

  comicSectionExpanded = true;
  audiobookSectionExpanded = true;
  providerSectionExpanded = true;

  comicTextFields = COMIC_TEXT_METADATA_FIELDS;
  comicArrayFields = COMIC_ARRAY_METADATA_FIELDS;
  comicTextareaFields = COMIC_TEXTAREA_METADATA_FIELDS;
  audiobookMetadataFields = AUDIOBOOK_METADATA_FIELDS;

  providerSpecificFields: MetadataProviderSpecificFields = {
    asin: true,
    amazonRating: true,
    amazonReviewCount: true,
    googleId: true,
    goodreadsId: true,
    goodreadsRating: true,
    goodreadsReviewCount: true,
    hardcoverId: true,
    hardcoverBookId: true,
    hardcoverRating: true,
    hardcoverReviewCount: true,
    comicvineId: true,
    lubimyczytacId: true,
    lubimyczytacRating: true,
    ranobedbId: true,
    ranobedbRating: true,
    audibleId: true,
    audibleRating: true,
    audibleReviewCount: true,
  };

  navigationState$ = this.bookNavigationService.getNavigationState();

  filterCategories(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredCategories = this.allCategories.filter((cat) =>
      cat.toLowerCase().includes(query)
    );
  }

  filterAuthors(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredAuthors = this.allAuthors.filter((cat) =>
      cat.toLowerCase().includes(query)
    );
  }

  filterMoods(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredMoods = this.allMoods.filter((mood) =>
      mood.toLowerCase().includes(query)
    );
  }

  filterTags(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredTags = this.allTags.filter((tag) =>
      tag.toLowerCase().includes(query)
    );
  }

  filterPublishers(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredPublishers = this.allPublishers.filter((publisher) =>
      publisher.toLowerCase().includes(query)
    );
  }

  filterSeries(event: { query: string }) {
    const query = event.query.toLowerCase();
    this.filteredSeries = this.allSeries.filter((seriesName) =>
      seriesName.toLowerCase().includes(query)
    );
  }

  constructor() {
    this.metadataForm = new FormGroup({
      title: new FormControl(""),
      subtitle: new FormControl(""),
      authors: new FormControl(""),
      categories: new FormControl(""),
      moods: new FormControl(""),
      tags: new FormControl(""),
      publisher: new FormControl(""),
      publishedDate: new FormControl(""),
      isbn10: new FormControl(""),
      isbn13: new FormControl(""),
      description: new FormControl(""),
      pageCount: new FormControl(""),
      language: new FormControl(""),
      asin: new FormControl(""),
      amazonRating: new FormControl(""),
      amazonReviewCount: new FormControl(""),
      goodreadsId: new FormControl(""),
      comicvineId: new FormControl(""),
      goodreadsRating: new FormControl(""),
      goodreadsReviewCount: new FormControl(""),
      hardcoverId: new FormControl(""),
      hardcoverBookId: new FormControl(""),
      hardcoverRating: new FormControl(""),
      hardcoverReviewCount: new FormControl(""),
      lubimyczytacId: new FormControl(""),
      lubimyczytacRating: new FormControl(""),
      ranobedbId: new FormControl(""),
      ranobedbRating: new FormControl(""),
      googleId: new FormControl(""),
      audibleId: new FormControl(""),
      audibleRating: new FormControl(""),
      audibleReviewCount: new FormControl(""),
      seriesName: new FormControl(""),
      seriesNumber: new FormControl(""),
      seriesTotal: new FormControl(""),
      thumbnailUrl: new FormControl(""),
      audiobookCover: new FormControl(""),

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
      amazonRatingLocked: new FormControl(false),
      amazonReviewCountLocked: new FormControl(false),
      goodreadsIdLocked: new FormControl(""),
      comicvineIdLocked: new FormControl(false),
      goodreadsRatingLocked: new FormControl(false),
      goodreadsReviewCountLocked: new FormControl(false),
      hardcoverIdLocked: new FormControl(false),
      hardcoverBookIdLocked: new FormControl(false),
      hardcoverRatingLocked: new FormControl(false),
      hardcoverReviewCountLocked: new FormControl(false),
      lubimyczytacIdLocked: new FormControl(false),
      lubimyczytacRatingLocked: new FormControl(false),
      ranobedbIdLocked: new FormControl(""),
      ranobedbRatingLocked: new FormControl(false),
      googleIdLocked: new FormControl(false),
      audibleIdLocked: new FormControl(false),
      audibleRatingLocked: new FormControl(false),
      audibleReviewCountLocked: new FormControl(false),
      seriesNameLocked: new FormControl(false),
      seriesNumberLocked: new FormControl(false),
      seriesTotalLocked: new FormControl(false),
      coverLocked: new FormControl(false),
      audiobookCoverLocked: new FormControl(false),
      reviewsLocked: new FormControl(false),
      ageRating: new FormControl(""),
      contentRating: new FormControl(""),
      ageRatingLocked: new FormControl(false),
      contentRatingLocked: new FormControl(false),
    });

    this.booleanOptions = [
      {label: this.t.translate('metadata.editor.booleanUnknown'), value: null},
      {label: this.t.translate('metadata.editor.booleanYes'), value: true},
      {label: this.t.translate('metadata.editor.booleanNo'), value: false},
    ];

    this.contentRatingOptions = [
      {label: this.t.translate('metadata.editor.contentRatingEveryone'), value: 'EVERYONE'},
      {label: this.t.translate('metadata.editor.contentRatingTeen'), value: 'TEEN'},
      {label: this.t.translate('metadata.editor.contentRatingMature'), value: 'MATURE'},
      {label: this.t.translate('metadata.editor.contentRatingAdult'), value: 'ADULT'},
      {label: this.t.translate('metadata.editor.contentRatingExplicit'), value: 'EXPLICIT'}
    ];

    this.ageRatingOptions = [
      {label: this.t.translate('metadata.editor.ageRatingAllAges'), value: 0},
      {label: '6+', value: 6},
      {label: '10+', value: 10},
      {label: '13+', value: 13},
      {label: '16+', value: 16},
      {label: '18+', value: 18},
      {label: '21+', value: 21}
    ];

    // Add audiobook metadata form controls
    for (const field of AUDIOBOOK_METADATA_FIELDS) {
      const defaultValue = field.type === 'boolean' ? null : '';
      this.metadataForm.addControl(field.controlName, new FormControl(defaultValue));
      this.metadataForm.addControl(field.lockedKey, new FormControl(false));
    }

    // Add comic metadata form controls
    for (const field of ALL_COMIC_METADATA_FIELDS) {
      const defaultValue = field.type === 'array' ? [] : (field.type === 'number' || field.type === 'boolean') ? null : '';
      this.metadataForm.addControl(field.controlName, new FormControl(defaultValue));
      this.metadataForm.addControl(field.lockedKey, new FormControl(false));
    }
  }

  ngOnInit(): void {
    this.book$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((book) => {
      const metadata = book?.metadata;
      if (!metadata) return;
      this.currentBookId = metadata.bookId;
      if (this.refreshingBookIds.has(book.id)) {
        this.refreshingBookIds.delete(book.id);
        this.isAutoFetching = false;
      }
      this.originalMetadata = structuredClone(metadata);
      this.populateFormFromMetadata(metadata);
    });

    this.prepareAutoComplete();

    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1)
      )
      .subscribe(userState => {
        this.metadataCenterViewMode = userState.user?.userSettings.metadataCenterViewMode ?? 'route';
        this.autoSaveEnabled = userState.user?.userSettings.autoSaveMetadata ?? false;
      });

    this.appSettingsService.appSettings$
      .pipe(
        filter(settings => !!settings),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(settings => {
        if (settings?.metadataProviderSpecificFields) {
          this.providerSpecificFields = settings.metadataProviderSpecificFields;
        }
      });
  }

  private prepareAutoComplete(): void {
    this.bookService.bookState$
      .pipe(
        filter((bookState) => bookState.loaded),
        take(1)
      )
      .subscribe((bookState) => {
        const authors = new Set<string>();
        const categories = new Set<string>();
        const moods = new Set<string>();
        const tags = new Set<string>();
        const publishers = new Set<string>();
        const series = new Set<string>();

        (bookState.books ?? []).forEach((book) => {
          book.metadata?.authors?.forEach((author) => authors.add(author));
          book.metadata?.categories?.forEach((category) =>
            categories.add(category)
          );
          book.metadata?.moods?.forEach((mood) => moods.add(mood));
          book.metadata?.tags?.forEach((tag) => tags.add(tag));
          if (book.metadata?.publisher) {
            publishers.add(book.metadata.publisher);
          }
          if (book.metadata?.seriesName) {
            series.add(book.metadata.seriesName);
          }
        });

        this.allAuthors = Array.from(authors);
        this.allCategories = Array.from(categories);
        this.allMoods = Array.from(moods);
        this.allTags = Array.from(tags);
        this.allPublishers = Array.from(publishers);
        this.allSeries = Array.from(series);
      });
  }

  private populateFormFromMetadata(metadata: BookMetadata): void {
    this.metadataForm.patchValue({
      title: metadata.title ?? null,
      subtitle: metadata.subtitle ?? null,
      authors: [...(metadata.authors ?? [])].sort(),
      categories: [...(metadata.categories ?? [])].sort(),
      moods: [...(metadata.moods ?? [])].sort(),
      tags: [...(metadata.tags ?? [])].sort(),
      publisher: metadata.publisher ?? null,
      publishedDate: metadata.publishedDate ?? null,
      isbn10: metadata.isbn10 ?? null,
      isbn13: metadata.isbn13 ?? null,
      description: metadata.description ?? null,
      pageCount: metadata.pageCount ?? null,
      language: metadata.language ?? null,
      rating: metadata.rating ?? null,
      reviewCount: metadata.reviewCount ?? null,
      asin: metadata.asin ?? null,
      amazonRating: metadata.amazonRating ?? null,
      amazonReviewCount: metadata.amazonReviewCount ?? null,
      goodreadsId: metadata.goodreadsId ?? null,
      comicvineId: metadata.comicvineId ?? null,
      goodreadsRating: metadata.goodreadsRating ?? null,
      goodreadsReviewCount: metadata.goodreadsReviewCount ?? null,
      hardcoverId: metadata.hardcoverId ?? null,
      hardcoverBookId: metadata.hardcoverBookId ?? null,
      hardcoverRating: metadata.hardcoverRating ?? null,
      hardcoverReviewCount: metadata.hardcoverReviewCount ?? null,
      lubimyczytacId: metadata.lubimyczytacId ?? null,
      lubimyczytacRating: metadata.lubimyczytacRating ?? null,
      ranobedbId: metadata.ranobedbId ?? null,
      ranobedbRating: metadata.ranobedbRating ?? null,
      googleId: metadata.googleId ?? null,
      audibleId: metadata.audibleId ?? null,
      audibleRating: metadata.audibleRating ?? null,
      audibleReviewCount: metadata.audibleReviewCount ?? null,
      seriesName: metadata.seriesName ?? null,
      seriesNumber: metadata.seriesNumber ?? null,
      seriesTotal: metadata.seriesTotal ?? null,
      titleLocked: metadata.titleLocked ?? false,
      subtitleLocked: metadata.subtitleLocked ?? false,
      authorsLocked: metadata.authorsLocked ?? false,
      categoriesLocked: metadata.categoriesLocked ?? false,
      moodsLocked: metadata.moodsLocked ?? false,
      tagsLocked: metadata.tagsLocked ?? false,
      publisherLocked: metadata.publisherLocked ?? false,
      publishedDateLocked: metadata.publishedDateLocked ?? false,
      isbn10Locked: metadata.isbn10Locked ?? false,
      isbn13Locked: metadata.isbn13Locked ?? false,
      descriptionLocked: metadata.descriptionLocked ?? false,
      pageCountLocked: metadata.pageCountLocked ?? false,
      languageLocked: metadata.languageLocked ?? false,
      asinLocked: metadata.asinLocked ?? false,
      amazonRatingLocked: metadata.amazonRatingLocked ?? false,
      amazonReviewCountLocked: metadata.amazonReviewCountLocked ?? false,
      goodreadsIdLocked: metadata.goodreadsIdLocked ?? false,
      comicvineIdLocked: metadata.comicvineIdLocked ?? false,
      goodreadsRatingLocked: metadata.goodreadsRatingLocked ?? false,
      goodreadsReviewCountLocked: metadata.goodreadsReviewCountLocked ?? false,
      hardcoverIdLocked: metadata.hardcoverIdLocked ?? false,
      hardcoverBookIdLocked: metadata.hardcoverBookIdLocked ?? false,
      hardcoverRatingLocked: metadata.hardcoverRatingLocked ?? false,
      hardcoverReviewCountLocked: metadata.hardcoverReviewCountLocked ?? false,
      lubimyczytacIdLocked: metadata.lubimyczytacIdLocked ?? false,
      lubimyczytacRatingLocked: metadata.lubimyczytacRatingLocked ?? false,
      ranobedbIdLocked: metadata.ranobedbIdLocked ?? false,
      ranobedbRatingLocked: metadata.ranobedbRatingLocked ?? false,
      googleIdLocked: metadata.googleIdLocked ?? false,
      audibleIdLocked: metadata.audibleIdLocked ?? false,
      audibleRatingLocked: metadata.audibleRatingLocked ?? false,
      audibleReviewCountLocked: metadata.audibleReviewCountLocked ?? false,
      seriesNameLocked: metadata.seriesNameLocked ?? false,
      seriesNumberLocked: metadata.seriesNumberLocked ?? false,
      seriesTotalLocked: metadata.seriesTotalLocked ?? false,
      coverLocked: metadata.coverLocked ?? false,
      audiobookCoverLocked: metadata.audiobookCoverLocked ?? false,
      reviewsLocked: metadata.reviewsLocked ?? false,
      ageRating: metadata.ageRating ?? null,
      contentRating: metadata.contentRating ?? null,
      ageRatingLocked: metadata.ageRatingLocked ?? false,
      contentRatingLocked: metadata.contentRatingLocked ?? false,
    });

    // Patch audiobook metadata
    const audiobookPatch: Record<string, unknown> = {};
    for (const field of AUDIOBOOK_METADATA_FIELDS) {
      const key = field.controlName as keyof BookMetadata;
      const lockedKey = field.lockedKey as keyof BookMetadata;
      audiobookPatch[field.controlName] = metadata[key] ?? (field.type === 'boolean' ? null : '');
      audiobookPatch[field.lockedKey] = metadata[lockedKey] ?? false;
    }
    this.metadataForm.patchValue(audiobookPatch);

    // Patch comic metadata
    const comicMeta = metadata.comicMetadata;
    const comicPatch: Record<string, unknown> = {};
    for (const field of ALL_COMIC_METADATA_FIELDS) {
      const value = comicMeta?.[field.fetchedKey as keyof ComicMetadata];
      if (field.type === 'array') {
        comicPatch[field.controlName] = [...(value as string[] ?? [])].sort();
      } else if (field.type === 'boolean' || field.type === 'number') {
        comicPatch[field.controlName] = value ?? null;
      } else {
        comicPatch[field.controlName] = value ?? '';
      }
      const modelLockKey = COMIC_FORM_TO_MODEL_LOCK[field.lockedKey];
      comicPatch[field.lockedKey] = comicMeta?.[modelLockKey as keyof ComicMetadata] ?? false;
    }
    this.metadataForm.patchValue(comicPatch);

    const lockableFields: { key: keyof BookMetadata; control: string }[] = [
      {key: "titleLocked", control: "title"},
      {key: "subtitleLocked", control: "subtitle"},
      {key: "authorsLocked", control: "authors"},
      {key: "categoriesLocked", control: "categories"},
      {key: "moodsLocked", control: "moods"},
      {key: "tagsLocked", control: "tags"},
      {key: "publisherLocked", control: "publisher"},
      {key: "publishedDateLocked", control: "publishedDate"},
      {key: "languageLocked", control: "language"},
      {key: "isbn10Locked", control: "isbn10"},
      {key: "isbn13Locked", control: "isbn13"},
      {key: "asinLocked", control: "asin"},
      {key: "amazonReviewCountLocked", control: "amazonReviewCount"},
      {key: "amazonRatingLocked", control: "amazonRating"},
      {key: "goodreadsIdLocked", control: "goodreadsId"},
      {key: "comicvineIdLocked", control: "comicvineId"},
      {key: "goodreadsReviewCountLocked", control: "goodreadsReviewCount"},
      {key: "goodreadsRatingLocked", control: "goodreadsRating"},
      {key: "hardcoverIdLocked", control: "hardcoverId"},
      {key: "hardcoverBookIdLocked", control: "hardcoverBookId"},
      {key: "hardcoverReviewCountLocked", control: "hardcoverReviewCount"},
      {key: "hardcoverRatingLocked", control: "hardcoverRating"},
      {key: "lubimyczytacIdLocked", control: "lubimyczytacId"},
      {key: "lubimyczytacRatingLocked", control: "lubimyczytacRating"},
      {key: "ranobedbReviewCountLocked", control: "ranobedbReviewCount"},
      {key: "ranobedbIdLocked", control: "ranobedbId"},
      {key: "ranobedbRatingLocked", control: "ranobedbRating"},
      {key: "googleIdLocked", control: "googleId"},
      {key: "audibleIdLocked", control: "audibleId"},
      {key: "audibleRatingLocked", control: "audibleRating"},
      {key: "audibleReviewCountLocked", control: "audibleReviewCount"},
      {key: "pageCountLocked", control: "pageCount"},
      {key: "descriptionLocked", control: "description"},
      {key: "seriesNameLocked", control: "seriesName"},
      {key: "seriesNumberLocked", control: "seriesNumber"},
      {key: "seriesTotalLocked", control: "seriesTotal"},
      {key: "coverLocked", control: "thumbnailUrl"},
      {key: "audiobookCoverLocked", control: "audiobookCover"},
      {key: "reviewsLocked", control: "reviews"},
      {key: "ageRatingLocked", control: "ageRating"},
      {key: "contentRatingLocked", control: "contentRating"},
    ];

    for (const {key, control} of lockableFields) {
      const isLocked = metadata[key] === true;
      const formControl = this.metadataForm.get(control);
      if (formControl) {
        isLocked ? formControl.disable() : formControl.enable();
      }
    }

    // Apply audiobook lock states
    for (const field of AUDIOBOOK_METADATA_FIELDS) {
      const isLocked = this.metadataForm.get(field.lockedKey)?.value === true;
      const formControl = this.metadataForm.get(field.controlName);
      if (formControl) {
        isLocked ? formControl.disable() : formControl.enable();
      }
    }

    // Apply comic lock states
    for (const field of ALL_COMIC_METADATA_FIELDS) {
      const isLocked = this.metadataForm.get(field.lockedKey)?.value === true;
      const formControl = this.metadataForm.get(field.controlName);
      if (formControl) {
        isLocked ? formControl.disable() : formControl.enable();
      }
    }
  }

  onAutoCompleteSelect(fieldName: string, event: AutoCompleteSelectEvent) {
    const values = (this.metadataForm.get(fieldName)?.value as string[]) || [];
    if (!values.includes(event.value as string)) {
      this.metadataForm.get(fieldName)?.setValue([...values, event.value as string]);
    }
    (event.originalEvent.target as HTMLInputElement).value = "";
  }

  onAutoCompleteKeyUp(fieldName: string, event: KeyboardEvent) {
    if (event.key === "Enter") {
      const input = event.target as HTMLInputElement;
      const value = input.value?.trim();
      if (value) {
        const values = this.metadataForm.get(fieldName)?.value || [];
        if (!values.includes(value)) {
          this.metadataForm.get(fieldName)?.setValue([...values, value]);
        }
        input.value = "";
      }
    }
  }

  onSave(): void {
    this.saveMetadata().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  saveMetadata(): Observable<void> {
    this.isSaving = true;
    return this.bookMetadataManageService
      .updateBookMetadata(
        this.currentBookId,
        this.buildMetadataWrapper(undefined),
        false
      )
      .pipe(
        tap({
          next: (response: any) => {
            this.isSaving = false;
            this.messageService.add({
              severity: "info",
              summary: this.t.translate('metadata.editor.toast.successSummary'),
              detail: this.t.translate('metadata.editor.toast.metadataUpdated'),
            });
            this.prepareAutoComplete();
            this.metadataForm.markAsPristine();
          },
          error: (err: any) => {
            this.isSaving = false;
            this.messageService.add({
              severity: "error",
              summary: this.t.translate('metadata.editor.toast.errorSummary'),
              detail: err?.error?.message || this.t.translate('metadata.editor.toast.metadataUpdateFailed'),
            });
          },
        })
      );
  }

  toggleLock(field: string): void {
    if (field === "thumbnailUrl") {
      field = "cover";
    }
    const isLocked = this.metadataForm.get(field + "Locked")?.value;
    const updatedLockedState = !isLocked;
    this.metadataForm.get(field + "Locked")?.setValue(updatedLockedState);

    if (updatedLockedState) {
      this.metadataForm.get(field)?.disable();
    } else {
      this.metadataForm.get(field)?.enable();
    }
    this.updateMetadata(undefined);
  }

  lockAll(): void {
    Object.keys(this.metadataForm.controls).forEach((key) => {
      if (key.endsWith("Locked")) {
        this.metadataForm.get(key)?.setValue(true);
        const fieldName = key.replace("Locked", "");
        this.metadataForm.get(fieldName)?.disable();
      }
    });
    this.updateMetadata(true);
  }

  unlockAll(): void {
    Object.keys(this.metadataForm.controls).forEach((key) => {
      if (key.endsWith("Locked")) {
        this.metadataForm.get(key)?.setValue(false);
        const fieldName = key.replace("Locked", "");
        this.metadataForm.get(fieldName)?.enable();
      }
    });
    this.updateMetadata(false);
  }

  private buildMetadataWrapper(shouldLockAllFields?: boolean): MetadataUpdateWrapper {
    const form = this.metadataForm;

    const metadata: BookMetadata = {
      bookId: this.currentBookId,
      title: form.get("title")?.value,
      subtitle: form.get("subtitle")?.value,
      authors: form.get("authors")?.value ?? [],
      categories: form.get("categories")?.value ?? [],
      moods: form.get("moods")?.value ?? [],
      tags: form.get("tags")?.value ?? [],
      publisher: form.get("publisher")?.value,
      publishedDate: form.get("publishedDate")?.value,
      isbn10: form.get("isbn10")?.value,
      isbn13: form.get("isbn13")?.value,
      description: form.get("description")?.value,
      pageCount: form.get("pageCount")?.value,
      rating: form.get("rating")?.value,
      reviewCount: form.get("reviewCount")?.value,
      asin: form.get("asin")?.value,
      amazonRating: form.get("amazonRating")?.value,
      amazonReviewCount: form.get("amazonReviewCount")?.value,
      goodreadsId: form.get("goodreadsId")?.value,
      comicvineId: form.get("comicvineId")?.value,
      goodreadsRating: form.get("goodreadsRating")?.value,
      goodreadsReviewCount: form.get("goodreadsReviewCount")?.value,
      hardcoverId: form.get("hardcoverId")?.value,
      hardcoverBookId: form.get("hardcoverBookId")?.value,
      hardcoverRating: form.get("hardcoverRating")?.value,
      hardcoverReviewCount: form.get("hardcoverReviewCount")?.value,
      lubimyczytacId: form.get("lubimyczytacId")?.value,
      lubimyczytacRating: form.get("lubimyczytacRating")?.value,
      ranobedbId: form.get("ranobedbId")?.value,
      ranobedbRating: form.get("ranobedbRating")?.value,
      googleId: form.get("googleId")?.value,
      audibleId: form.get("audibleId")?.value,
      audibleRating: form.get("audibleRating")?.value,
      audibleReviewCount: form.get("audibleReviewCount")?.value,
      language: form.get("language")?.value,
      seriesName: form.get("seriesName")?.value,
      seriesNumber: form.get("seriesNumber")?.value,
      seriesTotal: form.get("seriesTotal")?.value,
      thumbnailUrl: form.get("thumbnailUrl")?.value,
      audiobookCover: form.get("audiobookCover")?.value,

      // Audiobook metadata
      narrator: form.get("narrator")?.value,
      abridged: form.get("abridged")?.value,
      narratorLocked: form.get("narratorLocked")?.value,
      abridgedLocked: form.get("abridgedLocked")?.value,

      // Locks
      titleLocked: form.get("titleLocked")?.value,
      subtitleLocked: form.get("subtitleLocked")?.value,
      authorsLocked: form.get("authorsLocked")?.value,
      categoriesLocked: form.get("categoriesLocked")?.value,
      moodsLocked: form.get("moodsLocked")?.value,
      tagsLocked: form.get("tagsLocked")?.value,
      publisherLocked: form.get("publisherLocked")?.value,
      publishedDateLocked: form.get("publishedDateLocked")?.value,
      isbn10Locked: form.get("isbn10Locked")?.value,
      isbn13Locked: form.get("isbn13Locked")?.value,
      descriptionLocked: form.get("descriptionLocked")?.value,
      pageCountLocked: form.get("pageCountLocked")?.value,
      languageLocked: form.get("languageLocked")?.value,
      asinLocked: form.get("asinLocked")?.value,
      amazonRatingLocked: form.get("amazonRatingLocked")?.value,
      amazonReviewCountLocked: form.get("amazonReviewCountLocked")?.value,
      goodreadsIdLocked: form.get("goodreadsIdLocked")?.value,
      comicvineIdLocked: form.get("comicvineIdLocked")?.value,
      goodreadsRatingLocked: form.get("goodreadsRatingLocked")?.value,
      goodreadsReviewCountLocked: form.get("goodreadsReviewCountLocked")?.value,
      hardcoverIdLocked: form.get("hardcoverIdLocked")?.value,
      hardcoverBookIdLocked: form.get("hardcoverBookIdLocked")?.value,
      hardcoverRatingLocked: form.get("hardcoverRatingLocked")?.value,
      hardcoverReviewCountLocked: form.get("hardcoverReviewCountLocked")?.value,
      lubimyczytacIdLocked: form.get("lubimyczytacIdLocked")?.value,
      lubimyczytacRatingLocked: form.get("lubimyczytacRatingLocked")?.value,
      ranobedbIdLocked: form.get("ranobedbIdLocked")?.value,
      ranobedbRatingLocked: form.get("ranobedbRatingLocked")?.value,
      googleIdLocked: form.get("googleIdLocked")?.value,
      audibleIdLocked: form.get("audibleIdLocked")?.value,
      audibleRatingLocked: form.get("audibleRatingLocked")?.value,
      audibleReviewCountLocked: form.get("audibleReviewCountLocked")?.value,
      seriesNameLocked: form.get("seriesNameLocked")?.value,
      seriesNumberLocked: form.get("seriesNumberLocked")?.value,
      seriesTotalLocked: form.get("seriesTotalLocked")?.value,
      coverLocked: form.get("coverLocked")?.value,
      audiobookCoverLocked: form.get("audiobookCoverLocked")?.value,
      reviewsLocked: form.get("reviewsLocked")?.value,
      ageRating: form.get("ageRating")?.value,
      contentRating: form.get("contentRating")?.value,
      ageRatingLocked: form.get("ageRatingLocked")?.value,
      contentRatingLocked: form.get("contentRatingLocked")?.value,

      ...(shouldLockAllFields !== undefined && {
        allFieldsLocked: shouldLockAllFields,
      }),
    };

    // Build comic metadata from form controls
    const comicMetadata: Record<string, unknown> = {};
    for (const field of ALL_COMIC_METADATA_FIELDS) {
      const value = form.get(field.controlName)?.value;
      if (field.type === 'array') {
        comicMetadata[field.fetchedKey] = Array.isArray(value) ? value : [];
      } else if (field.type === 'number') {
        comicMetadata[field.fetchedKey] = value !== '' && value !== null ? Number(value) : null;
      } else if (field.type === 'boolean') {
        comicMetadata[field.fetchedKey] = value ?? null;
      } else {
        comicMetadata[field.fetchedKey] = value ?? '';
      }
    }
    // Consolidate comic lock states to model lock keys
    const lockGroups: Record<string, boolean> = {};
    for (const [formKey, modelKey] of Object.entries(COMIC_FORM_TO_MODEL_LOCK)) {
      const value = form.get(formKey)?.value ?? false;
      if (value) {
        lockGroups[modelKey] = true;
      } else if (!(modelKey in lockGroups)) {
        lockGroups[modelKey] = false;
      }
    }
    for (const [modelKey, value] of Object.entries(lockGroups)) {
      comicMetadata[modelKey] = value;
    }
    metadata.comicMetadata = comicMetadata as ComicMetadata;

    const original = this.originalMetadata;

    const wasCleared = (key: keyof BookMetadata): boolean => {
      const current = (metadata[key] as unknown) ?? null;
      const prev = (original[key] as unknown) ?? null;

      const isEmpty = (val: unknown): boolean =>
        val === null || val === "" || (Array.isArray(val) && val.length === 0);

      return isEmpty(current) && !isEmpty(prev);
    };

    const clearFlags: MetadataClearFlags = {
      title: wasCleared("title"),
      subtitle: wasCleared("subtitle"),
      authors: wasCleared("authors"),
      categories: wasCleared("categories"),
      moods: wasCleared("moods"),
      tags: wasCleared("tags"),
      publisher: wasCleared("publisher"),
      publishedDate: wasCleared("publishedDate"),
      isbn10: wasCleared("isbn10"),
      isbn13: wasCleared("isbn13"),
      description: wasCleared("description"),
      pageCount: wasCleared("pageCount"),
      language: wasCleared("language"),
      asin: wasCleared("asin"),
      amazonRating: wasCleared("amazonRating"),
      amazonReviewCount: wasCleared("amazonReviewCount"),
      goodreadsId: wasCleared("goodreadsId"),
      comicvineId: wasCleared("comicvineId"),
      goodreadsRating: wasCleared("goodreadsRating"),
      goodreadsReviewCount: wasCleared("goodreadsReviewCount"),
      hardcoverId: wasCleared("hardcoverId"),
      hardcoverRating: wasCleared("hardcoverRating"),
      hardcoverReviewCount: wasCleared("hardcoverReviewCount"),
      hardcoverBookId: wasCleared("hardcoverBookId"),
      lubimyczytacId: wasCleared("lubimyczytacId"),
      lubimyczytacRating: wasCleared("lubimyczytacRating"),
      ranobedbId: wasCleared("ranobedbId"),
      ranobedbRating: wasCleared("ranobedbRating"),
      googleId: wasCleared("googleId"),
      audibleId: wasCleared("audibleId"),
      audibleRating: wasCleared("audibleRating"),
      audibleReviewCount: wasCleared("audibleReviewCount"),
      seriesName: wasCleared("seriesName"),
      seriesNumber: wasCleared("seriesNumber"),
      seriesTotal: wasCleared("seriesTotal"),
      narrator: wasCleared("narrator"),
      abridged: wasCleared("abridged"),
      audiobookCover: wasCleared("audiobookCover"),
      cover: false,
      ageRating: wasCleared("ageRating"),
      contentRating: wasCleared("contentRating"),
    };

    return {metadata, clearFlags};
  }

  private updateMetadata(shouldLockAllFields: boolean | undefined): void {
    const metadataUpdateWrapper = this.buildMetadataWrapper(shouldLockAllFields);
    this.bookMetadataManageService
      .updateBookMetadata(this.currentBookId, metadataUpdateWrapper, false)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          if (shouldLockAllFields !== undefined) {
            this.messageService.add({
              severity: "success",
              summary: shouldLockAllFields
                ? this.t.translate('metadata.editor.toast.metadataLocked')
                : this.t.translate('metadata.editor.toast.metadataUnlocked'),
              detail: shouldLockAllFields
                ? this.t.translate('metadata.editor.toast.allFieldsLocked')
                : this.t.translate('metadata.editor.toast.allFieldsUnlocked'),
            });
          }
        },
        error: () => {
          this.messageService.add({
            severity: "error",
            summary: this.t.translate('metadata.editor.toast.errorSummary'),
            detail: this.t.translate('metadata.editor.toast.lockStateFailed'),
          });
        },
      });
  }

  getUploadCoverUrl(): string {
    return this.bookMetadataManageService.getUploadCoverUrl(this.currentBookId);
  }

  onBeforeSend(): void {
    this.isUploading = true;
  }

  onUpload(event: FileUploadEvent): void {
    const response: HttpResponse<unknown> =
      event.originalEvent as HttpResponse<unknown>;
    if (response && response.status === 200) {
      this.isUploading = false;
    } else {
      this.isUploading = false;
      this.messageService.add({
        severity: "error",
        summary: this.t.translate('metadata.editor.toast.uploadFailedSummary'),
        detail: this.t.translate('metadata.editor.toast.uploadFailedDetail'),
        life: 3000,
      });
    }
  }

  onUploadError($event: FileUploadErrorEvent) {
    this.isUploading = false;
    this.messageService.add({
      severity: "error",
      summary: this.t.translate('metadata.editor.toast.uploadErrorSummary'),
      detail: this.t.translate('metadata.editor.toast.uploadErrorDetail'),
      life: 3000,
    });
  }

  regenerateCover(bookId: number) {
    this.bookMetadataManageService.regenerateCover(bookId).pipe(
      switchMap(() => this.bookService.getBookByIdFromAPI(bookId, false)),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (updatedBook) => {
        this.bookService.handleBookUpdate(updatedBook);
        this.messageService.add({
          severity: "success",
          summary: this.t.translate('metadata.editor.toast.successSummary'),
          detail: this.t.translate('metadata.editor.toast.coverRegenerated'),
        });
      },
      error: (err) => {
        this.messageService.add({
          severity: "error",
          summary: this.t.translate('metadata.editor.toast.errorSummary'),
          detail: err?.error?.message || this.t.translate('metadata.editor.toast.coverRegenFailed'),
        });
      }
    });
  }

  generateCustomCover(bookId: number) {
    this.isGeneratingCover = true;
    this.bookMetadataManageService.generateCustomCover(bookId).pipe(
      switchMap(() => this.bookService.getBookByIdFromAPI(bookId, false)),
      finalize(() => this.isGeneratingCover = false),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (updatedBook) => {
        this.bookService.handleBookUpdate(updatedBook);
        this.messageService.add({
          severity: "success",
          summary: this.t.translate('metadata.editor.toast.successSummary'),
          detail: this.t.translate('metadata.editor.toast.customCoverGenerated'),
        });
      },
      error: (err) => {
        this.messageService.add({
          severity: "error",
          summary: this.t.translate('metadata.editor.toast.errorSummary'),
          detail: this.t.translate('metadata.editor.toast.customCoverFailed'),
        });
      }
    });
  }

  regenerateAudiobookCover(bookId: number) {
    this.bookMetadataManageService.regenerateAudiobookCover(bookId).pipe(
      switchMap(() => this.bookService.getBookByIdFromAPI(bookId, false)),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (updatedBook) => {
        this.bookService.handleBookUpdate(updatedBook);
        this.messageService.add({
          severity: "success",
          summary: this.t.translate('metadata.editor.toast.successSummary'),
          detail: this.t.translate('metadata.editor.toast.audiobookCoverRegenerated'),
        });
      },
      error: (err) => {
        this.messageService.add({
          severity: "error",
          summary: this.t.translate('metadata.editor.toast.errorSummary'),
          detail: err?.error?.message || this.t.translate('metadata.editor.toast.audiobookCoverRegenFailed'),
        });
      }
    });
  }

  generateCustomAudiobookCover(bookId: number) {
    this.isGeneratingAudiobookCover = true;
    this.bookMetadataManageService.generateCustomAudiobookCover(bookId).pipe(
      switchMap(() => this.bookService.getBookByIdFromAPI(bookId, false)),
      finalize(() => this.isGeneratingAudiobookCover = false),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (updatedBook) => {
        this.bookService.handleBookUpdate(updatedBook);
        this.messageService.add({
          severity: "success",
          summary: this.t.translate('metadata.editor.toast.successSummary'),
          detail: this.t.translate('metadata.editor.toast.customAudiobookCoverGenerated'),
        });
      },
      error: (err) => {
        this.messageService.add({
          severity: "error",
          summary: this.t.translate('metadata.editor.toast.errorSummary'),
          detail: this.t.translate('metadata.editor.toast.customAudiobookCoverFailed'),
        });
      }
    });
  }

  autoFetch(bookId: number) {
    this.refreshingBookIds.add(bookId);
    this.isAutoFetching = true;

    this.taskHelperService.refreshMetadataTask({
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: [bookId],
    }).subscribe({
      next: () => {
        this.isAutoFetching = false;
      },
      error: () => {
        this.isAutoFetching = false;
      },
      complete: () => {
        this.isAutoFetching = false;
        this.refreshingBookIds.delete(bookId);
      }
    });

    setTimeout(() => {
      this.isAutoFetching = false;
      this.refreshingBookIds.delete(bookId);
    }, 15000);
  }

  fetchFromFile(bookId: number) {
    this.isFetchingFromFile = true;
    this.bookMetadataManageService.getFileMetadata(bookId).pipe(
      finalize(() => this.isFetchingFromFile = false),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (metadata) => {
        this.populateFormFromMetadata(metadata);
        this.metadataForm.markAsDirty();
        this.messageService.add({
          severity: 'info',
          summary: this.t.translate('metadata.editor.toast.successSummary'),
          detail: this.t.translate('metadata.editor.toast.fileMetadataLoaded'),
        });
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.editor.toast.errorSummary'),
          detail: err?.error?.message || this.t.translate('metadata.editor.toast.fileMetadataFailed'),
        });
      }
    });
  }

  onNext() {
    this.nextBookClicked.emit();
  }

  onPrevious() {
    this.previousBookClicked.emit();
  }

  closeDialog() {
    this.closeDialogButtonClicked.emit();
  }

  openCoverSearch() {
    const ref = this.bookDialogHelperService.openCoverSearchDialog(this.currentBookId, 'ebook');
    ref?.onClose.pipe(
      take(1),
      filter(result => !!result),
      switchMap(() => this.bookService.getBookByIdFromAPI(this.currentBookId, false)),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(updatedBook => {
      this.bookService.handleBookUpdate(updatedBook);
    });
  }

  canNavigatePrevious(): boolean {
    return this.bookNavigationService.canNavigatePrevious();
  }

  canNavigateNext(): boolean {
    return this.bookNavigationService.canNavigateNext();
  }

  navigatePrevious(): void {
    const prevBookId = this.bookNavigationService.getPreviousBookId();
    if (prevBookId) {
      if (this.autoSaveEnabled && this.metadataForm.dirty) {
        this.saveMetadata().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.navigateToBook(prevBookId));
      } else {
        this.navigateToBook(prevBookId);
      }
    }
  }

  navigateNext(): void {
    const nextBookId = this.bookNavigationService.getNextBookId();
    if (nextBookId) {
      if (this.autoSaveEnabled && this.metadataForm.dirty) {
        this.saveMetadata().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.navigateToBook(nextBookId));
      } else {
        this.navigateToBook(nextBookId);
      }
    }
  }

  private navigateToBook(bookId: number): void {
    this.bookNavigationService.updateCurrentBook(bookId);
    if (this.metadataCenterViewMode === 'route') {
      this.router.navigate(['/book', bookId], {
        queryParams: {tab: 'edit'}
      });
    } else {
      this.metadataHostService.switchBook(bookId);
    }
  }

  getNavigationPosition(): string {
    const position = this.bookNavigationService.getCurrentPosition();
    return position ? this.t.translate('metadata.editor.navigationPosition', {current: position.current, total: position.total}) : '';
  }

  isFieldVisible(field: keyof MetadataProviderSpecificFields): boolean {
    return this.providerSpecificFields[field] ?? false;
  }

  protected readonly sample = sample;

  onFieldChange(): void {
    this.metadataForm.markAsDirty();
  }

  // Cover type detection methods
  hasEbookFormat(book: Book): boolean {
    if (book.isPhysical) {
      return true;
    }
    const allFiles = [book.primaryFile, ...(book.alternativeFormats || [])].filter(f => f?.bookType);
    return allFiles.some(f => f!.bookType !== 'AUDIOBOOK');
  }

  hasAudiobookFormat(book: Book): boolean {
    const allFiles = [book.primaryFile, ...(book.alternativeFormats || [])].filter(f => f?.bookType);
    return allFiles.some(f => f!.bookType === 'AUDIOBOOK');
  }

  supportsDualCovers(book: Book): boolean {
    return this.bookMetadataManageService.supportsDualCovers(book);
  }

  isCBX(book: Book): boolean {
    return book.primaryFile?.bookType === 'CBX';
  }

  isAudiobook(book: Book): boolean {
    return book.primaryFile?.bookType === 'AUDIOBOOK';
  }

  getUploadAudiobookCoverUrl(): string {
    return this.bookMetadataManageService.getUploadAudiobookCoverUrl(this.currentBookId);
  }

  openAudiobookCoverSearch() {
    const ref = this.bookDialogHelperService.openCoverSearchDialog(this.currentBookId, 'audiobook');
    ref?.onClose.pipe(
      take(1),
      filter(result => !!result),
      switchMap(() => this.bookService.getBookByIdFromAPI(this.currentBookId, false)),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(updatedBook => {
      this.bookService.handleBookUpdate(updatedBook);
    });
  }

  onAudiobookCoverUpload(event: FileUploadEvent): void {
    const response: HttpResponse<unknown> =
      event.originalEvent as HttpResponse<unknown>;
    if (response && response.status === 200) {
      this.isUploading = false;
      this.bookService.getBookByIdFromAPI(this.currentBookId, false).pipe(
        takeUntilDestroyed(this.destroyRef)
      ).subscribe(updatedBook => {
        this.bookService.handleBookUpdate(updatedBook);
      });
    } else {
      this.isUploading = false;
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('metadata.editor.toast.uploadFailedSummary'),
        detail: this.t.translate('metadata.editor.toast.audiobookUploadFailed'),
        life: 3000,
      });
    }
  }
}
