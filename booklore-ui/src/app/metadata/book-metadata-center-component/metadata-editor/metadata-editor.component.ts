import {
  Component,
  DestroyRef,
  EventEmitter,
  inject,
  Input,
  OnInit,
  Output,
} from "@angular/core";
import { InputText } from "primeng/inputtext";
import { Button } from "primeng/button";
import { Divider } from "primeng/divider";
import {
  FormControl,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
} from "@angular/forms";
import { Observable } from "rxjs";
import { AsyncPipe } from "@angular/common";
import { MessageService } from "primeng/api";
import {
  Book,
  BookMetadata,
  MetadataClearFlags,
  MetadataUpdateWrapper,
} from "../../../book/model/book.model";
import { UrlHelperService } from "../../../utilities/service/url-helper.service";
import {
  FileUpload,
  FileUploadErrorEvent,
  FileUploadEvent,
} from "primeng/fileupload";
import { HttpResponse } from "@angular/common/http";
import { BookService } from "../../../book/service/book.service";
import { ProgressSpinner } from "primeng/progressspinner";
import { Tooltip } from "primeng/tooltip";
import { filter, take } from "rxjs/operators";
import { MetadataRestoreDialogComponent } from "../../../book/components/book-browser/metadata-restore-dialog-component/metadata-restore-dialog-component";
import { DialogService } from "primeng/dynamicdialog";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { MetadataRefreshRequest } from "../../model/request/metadata-refresh-request.model";
import { MetadataRefreshType } from "../../model/request/metadata-refresh-type.enum";
import { AutoComplete } from "primeng/autocomplete";
import { Textarea } from "primeng/textarea";
import { IftaLabel } from "primeng/iftalabel";
import { CoverSearchComponent } from "../../cover-search/cover-search.component";
import { Image } from "primeng/image";
import { LazyLoadImageModule } from "ng-lazyload-image";

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
    Textarea,
    IftaLabel,
    Image,
    LazyLoadImageModule,
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
  protected urlHelper = inject(UrlHelperService);
  private dialogService = inject(DialogService);
  private destroyRef = inject(DestroyRef);

  metadataForm: FormGroup;
  currentBookId!: number;
  isUploading = false;
  isLoading = false;
  isSaving = false;

  refreshingBookIds = new Set<number>();
  isAutoFetching = false;

  originalMetadata!: BookMetadata;

  allAuthors!: string[];
  allCategories!: string[];
  filteredCategories: string[] = [];
  filteredAuthors: string[] = [];

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

  constructor() {
    this.metadataForm = new FormGroup({
      title: new FormControl(""),
      subtitle: new FormControl(""),
      authors: new FormControl(""),
      categories: new FormControl(""),
      publisher: new FormControl(""),
      publishedDate: new FormControl(""),
      isbn10: new FormControl(""),
      isbn13: new FormControl(""),
      description: new FormControl(""),
      pageCount: new FormControl(""),
      language: new FormControl(""),
      asin: new FormControl(""),
      personalRating: new FormControl(""),
      amazonRating: new FormControl(""),
      amazonReviewCount: new FormControl(""),
      goodreadsId: new FormControl(""),
      comicvineId: new FormControl(""),
      goodreadsRating: new FormControl(""),
      goodreadsReviewCount: new FormControl(""),
      hardcoverId: new FormControl(""),
      hardcoverRating: new FormControl(""),
      hardcoverReviewCount: new FormControl(""),
      googleId: new FormControl(""),
      seriesName: new FormControl(""),
      seriesNumber: new FormControl(""),
      seriesTotal: new FormControl(""),
      thumbnailUrl: new FormControl(""),

      titleLocked: new FormControl(false),
      subtitleLocked: new FormControl(false),
      authorsLocked: new FormControl(false),
      categoriesLocked: new FormControl(false),
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
      goodreadsIdLocked: new FormControl(""),
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

    this.bookService.bookState$
      .pipe(
        filter((bookState) => bookState.loaded),
        take(1)
      )
      .subscribe((bookState) => {
        const authors = new Set<string>();
        const categories = new Set<string>();

        (bookState.books ?? []).forEach((book) => {
          book.metadata?.authors?.forEach((author) => authors.add(author));
          book.metadata?.categories?.forEach((category) =>
            categories.add(category)
          );
        });

        this.allAuthors = Array.from(authors);
        this.allCategories = Array.from(categories);
      });
  }

  private populateFormFromMetadata(metadata: BookMetadata): void {
    this.metadataForm.patchValue({
      title: metadata.title ?? null,
      subtitle: metadata.subtitle ?? null,
      authors: [...(metadata.authors ?? [])].sort(),
      categories: [...(metadata.categories ?? [])].sort(),
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
      personalRating: metadata.personalRating ?? null,
      amazonRating: metadata.amazonRating ?? null,
      amazonReviewCount: metadata.amazonReviewCount ?? null,
      goodreadsId: metadata.goodreadsId ?? null,
      comicvineId: metadata.comicvineId ?? null,
      goodreadsRating: metadata.goodreadsRating ?? null,
      goodreadsReviewCount: metadata.goodreadsReviewCount ?? null,
      hardcoverId: metadata.hardcoverId ?? null,
      hardcoverRating: metadata.hardcoverRating ?? null,
      hardcoverReviewCount: metadata.hardcoverReviewCount ?? null,
      googleId: metadata.googleId ?? null,
      seriesName: metadata.seriesName ?? null,
      seriesNumber: metadata.seriesNumber ?? null,
      seriesTotal: metadata.seriesTotal ?? null,
      titleLocked: metadata.titleLocked ?? false,
      subtitleLocked: metadata.subtitleLocked ?? false,
      authorsLocked: metadata.authorsLocked ?? false,
      categoriesLocked: metadata.categoriesLocked ?? false,
      publisherLocked: metadata.publisherLocked ?? false,
      publishedDateLocked: metadata.publishedDateLocked ?? false,
      isbn10Locked: metadata.isbn10Locked ?? false,
      isbn13Locked: metadata.isbn13Locked ?? false,
      descriptionLocked: metadata.descriptionLocked ?? false,
      pageCountLocked: metadata.pageCountLocked ?? false,
      languageLocked: metadata.languageLocked ?? false,
      asinLocked: metadata.asinLocked ?? false,
      personalRatingLocked: metadata.personalRatingLocked ?? false,
      amazonRatingLocked: metadata.amazonRatingLocked ?? false,
      amazonReviewCountLocked: metadata.amazonReviewCountLocked ?? false,
      goodreadsIdLocked: metadata.goodreadsIdLocked ?? false,
      comicvineIdLocked: metadata.comicvineIdLocked ?? false,
      goodreadsRatingLocked: metadata.goodreadsRatingLocked ?? false,
      goodreadsReviewCountLocked: metadata.goodreadsReviewCountLocked ?? false,
      hardcoverIdLocked: metadata.hardcoverIdLocked ?? false,
      hardcoverRatingLocked: metadata.hardcoverRatingLocked ?? false,
      hardcoverReviewCountLocked: metadata.hardcoverReviewCountLocked ?? false,
      googleIdLocked: metadata.googleIdLocked ?? false,
      seriesNameLocked: metadata.seriesNameLocked ?? false,
      seriesNumberLocked: metadata.seriesNumberLocked ?? false,
      seriesTotalLocked: metadata.seriesTotalLocked ?? false,
      coverLocked: metadata.coverLocked ?? false,
    });

    const lockableFields: { key: keyof BookMetadata; control: string }[] = [
      { key: "titleLocked", control: "title" },
      { key: "subtitleLocked", control: "subtitle" },
      { key: "authorsLocked", control: "authors" },
      { key: "categoriesLocked", control: "categories" },
      { key: "publisherLocked", control: "publisher" },
      { key: "publishedDateLocked", control: "publishedDate" },
      { key: "languageLocked", control: "language" },
      { key: "isbn10Locked", control: "isbn10" },
      { key: "isbn13Locked", control: "isbn13" },
      { key: "asinLocked", control: "asin" },
      { key: "amazonReviewCountLocked", control: "amazonReviewCount" },
      { key: "amazonRatingLocked", control: "amazonRating" },
      { key: "personalRatingLocked", control: "personalRating" },
      { key: "goodreadsIdLocked", control: "goodreadsId" },
      { key: "comicvineIdLocked", control: "comicvineId" },
      { key: "goodreadsReviewCountLocked", control: "goodreadsReviewCount" },
      { key: "goodreadsRatingLocked", control: "goodreadsRating" },
      { key: "hardcoverIdLocked", control: "hardcoverId" },
      { key: "hardcoverReviewCountLocked", control: "hardcoverReviewCount" },
      { key: "hardcoverRatingLocked", control: "hardcoverRating" },
      { key: "googleIdLocked", control: "googleId" },
      { key: "pageCountLocked", control: "pageCount" },
      { key: "descriptionLocked", control: "description" },
      { key: "seriesNameLocked", control: "seriesName" },
      { key: "seriesNumberLocked", control: "seriesNumber" },
      { key: "seriesTotalLocked", control: "seriesTotal" },
      { key: "coverLocked", control: "thumbnailUrl" },
    ];

    for (const { key, control } of lockableFields) {
      const isLocked = metadata[key] === true;
      const formControl = this.metadataForm.get(control);
      if (formControl) {
        isLocked ? formControl.disable() : formControl.enable();
      }
    }
  }

  onAutoCompleteSelect(fieldName: string, event: any) {
    const values = this.metadataForm.get(fieldName)?.value || [];
    if (!values.includes(event.value)) {
      this.metadataForm.get(fieldName)?.setValue([...values, event.value]);
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
    this.isSaving = true;
    this.bookService
      .updateBookMetadata(
        this.currentBookId,
        this.buildMetadataWrapper(undefined),
        false
      )
      .subscribe({
        next: (response) => {
          this.isSaving = false;
          this.messageService.add({
            severity: "info",
            summary: "Success",
            detail: "Book metadata updated",
          });
        },
        error: (err) => {
          this.isSaving = false;
          this.messageService.add({
            severity: "error",
            summary: "Error",
            detail: err?.error?.message || "Failed to update book metadata",
          });
        },
      });
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

  quillDisabled(): boolean {
    return this.metadataForm.get("descriptionLocked")?.value === true;
  }

  private buildMetadataWrapper(
    shouldLockAllFields?: boolean
  ): MetadataUpdateWrapper {
    const form = this.metadataForm;

    const metadata: BookMetadata = {
      bookId: this.currentBookId,
      title: form.get("title")?.value,
      subtitle: form.get("subtitle")?.value,
      authors: form.get("authors")?.value ?? [],
      categories: form.get("categories")?.value ?? [],
      publisher: form.get("publisher")?.value,
      publishedDate: form.get("publishedDate")?.value,
      isbn10: form.get("isbn10")?.value,
      isbn13: form.get("isbn13")?.value,
      description: form.get("description")?.value,
      pageCount: form.get("pageCount")?.value,
      rating: form.get("rating")?.value,
      reviewCount: form.get("reviewCount")?.value,
      asin: form.get("asin")?.value,
      personalRating: form.get("personalRating")?.value,
      amazonRating: form.get("amazonRating")?.value,
      amazonReviewCount: form.get("amazonReviewCount")?.value,
      goodreadsId: form.get("goodreadsId")?.value,
      comicvineId: form.get("comicvineId")?.value,
      goodreadsRating: form.get("goodreadsRating")?.value,
      goodreadsReviewCount: form.get("goodreadsReviewCount")?.value,
      hardcoverId: form.get("hardcoverId")?.value,
      hardcoverRating: form.get("hardcoverRating")?.value,
      hardcoverReviewCount: form.get("hardcoverReviewCount")?.value,
      googleId: form.get("googleId")?.value,
      language: form.get("language")?.value,
      seriesName: form.get("seriesName")?.value,
      seriesNumber: form.get("seriesNumber")?.value,
      seriesTotal: form.get("seriesTotal")?.value,
      thumbnailUrl: form.get("thumbnailUrl")?.value,

      // Locks
      titleLocked: form.get("titleLocked")?.value,
      subtitleLocked: form.get("subtitleLocked")?.value,
      authorsLocked: form.get("authorsLocked")?.value,
      categoriesLocked: form.get("categoriesLocked")?.value,
      publisherLocked: form.get("publisherLocked")?.value,
      publishedDateLocked: form.get("publishedDateLocked")?.value,
      isbn10Locked: form.get("isbn10Locked")?.value,
      isbn13Locked: form.get("isbn13Locked")?.value,
      descriptionLocked: form.get("descriptionLocked")?.value,
      pageCountLocked: form.get("pageCountLocked")?.value,
      languageLocked: form.get("languageLocked")?.value,
      asinLocked: form.get("asinLocked")?.value,
      amazonRatingLocked: form.get("amazonRatingLocked")?.value,
      personalRatingLocked: form.get("personalRatingLocked")?.value,
      amazonReviewCountLocked: form.get("amazonReviewCountLocked")?.value,
      goodreadsIdLocked: form.get("goodreadsIdLocked")?.value,
      comicvineIdLocked: form.get("comicvineIdLocked")?.value,
      goodreadsRatingLocked: form.get("goodreadsRatingLocked")?.value,
      goodreadsReviewCountLocked: form.get("goodreadsReviewCountLocked")?.value,
      hardcoverIdLocked: form.get("hardcoverIdLocked")?.value,
      hardcoverRatingLocked: form.get("hardcoverRatingLocked")?.value,
      hardcoverReviewCountLocked: form.get("hardcoverReviewCountLocked")?.value,
      googleIdLocked: form.get("googleIdLocked")?.value,
      seriesNameLocked: form.get("seriesNameLocked")?.value,
      seriesNumberLocked: form.get("seriesNumberLocked")?.value,
      seriesTotalLocked: form.get("seriesTotalLocked")?.value,
      coverLocked: form.get("coverLocked")?.value,

      ...(shouldLockAllFields !== undefined && {
        allFieldsLocked: shouldLockAllFields,
      }),
    };

    const original = this.originalMetadata;

    const wasCleared = (key: keyof BookMetadata): boolean => {
      const current = (metadata[key] as any) ?? null;
      const prev = (original[key] as any) ?? null;

      const isEmpty = (val: any): boolean =>
        val === null || val === "" || (Array.isArray(val) && val.length === 0);

      return isEmpty(current) && !isEmpty(prev);
    };

    const clearFlags: MetadataClearFlags = {
      title: wasCleared("title"),
      subtitle: wasCleared("subtitle"),
      authors: wasCleared("authors"),
      categories: wasCleared("categories"),
      publisher: wasCleared("publisher"),
      publishedDate: wasCleared("publishedDate"),
      isbn10: wasCleared("isbn10"),
      isbn13: wasCleared("isbn13"),
      description: wasCleared("description"),
      pageCount: wasCleared("pageCount"),
      language: wasCleared("language"),
      asin: wasCleared("asin"),
      personalRating: wasCleared("personalRating"),
      amazonRating: wasCleared("personalRating"),
      amazonReviewCount: wasCleared("amazonReviewCount"),
      goodreadsId: wasCleared("goodreadsId"),
      comicvineId: wasCleared("comicvineId"),
      goodreadsRating: wasCleared("goodreadsRating"),
      goodreadsReviewCount: wasCleared("goodreadsReviewCount"),
      hardcoverId: wasCleared("hardcoverId"),
      hardcoverRating: wasCleared("hardcoverRating"),
      hardcoverReviewCount: wasCleared("hardcoverReviewCount"),
      googleId: wasCleared("googleId"),
      seriesName: wasCleared("seriesName"),
      seriesNumber: wasCleared("seriesNumber"),
      seriesTotal: wasCleared("seriesTotal"),
      cover: false,
    };

    return { metadata, clearFlags };
  }

  private updateMetadata(shouldLockAllFields: boolean | undefined): void {
    let metadataUpdateWrapper = this.buildMetadataWrapper(shouldLockAllFields);
    this.bookService
      .updateBookMetadata(this.currentBookId, metadataUpdateWrapper, false)
      .subscribe({
        next: (response) => {
          if (shouldLockAllFields !== undefined) {
            this.messageService.add({
              severity: "success",
              summary: shouldLockAllFields
                ? "Metadata Locked"
                : "Metadata Unlocked",
              detail: shouldLockAllFields
                ? "All fields have been successfully locked."
                : "All fields have been successfully unlocked.",
            });
          }
        },
        error: () => {
          this.messageService.add({
            severity: "error",
            summary: "Error",
            detail: "Failed to update lock state",
          });
        },
      });
  }

  getUploadCoverUrl(): string {
    return this.bookService.getUploadCoverUrl(this.currentBookId);
  }

  onBeforeSend(): void {
    this.isUploading = true;
  }

  onUpload(event: FileUploadEvent): void {
    const response: HttpResponse<any> =
      event.originalEvent as HttpResponse<any>;
    if (response && response.status === 200) {
      const bookMetadata: BookMetadata = response.body as BookMetadata;
      this.bookService.handleBookMetadataUpdate(
        this.currentBookId,
        bookMetadata
      );
      this.isUploading = false;
    } else {
      this.isUploading = false;
      this.messageService.add({
        severity: "error",
        summary: "Upload Failed",
        detail: "An error occurred while uploading the cover",
        life: 3000,
      });
    }
  }

  onUploadError($event: FileUploadErrorEvent) {
    this.isUploading = false;
    this.messageService.add({
      severity: "error",
      summary: "Upload Error",
      detail: "An error occurred while uploading the cover",
      life: 3000,
    });
  }

  regenerateCover(bookId: number) {
    this.bookService.regenerateCover(bookId).subscribe({
      next: () => {
        this.messageService.add({
          severity: "success",
          summary: "Success",
          detail:
            "Book cover regenerated successfully. Refresh page to see the new cover.",
        });
      },
      error: () => {
        this.messageService.add({
          severity: "error",
          summary: "Error",
          detail: "Failed to start cover regeneration",
        });
      },
    });
  }

  // restoreCbxMetadata() {
  //   this.isLoading = true;
  //   this.bookService.getComicInfoMetadata(this.currentBookId).subscribe();
  //   setTimeout(() => {
  //     this.isLoading = false;
  //     // this.refreshingBookIds.delete(bookId);
  //   }, 10000);
  // }
  restoreCbxMetadata() {
    this.isLoading = true;
    console.log("LOADING CBX METADATA FOR BOOK ID:", this.currentBookId);
    this.bookService.getComicInfoMetadata(this.currentBookId).subscribe({
      next: (metadata) => {
        console.log("Retrieved ComicInfo.xml metadata:", metadata);
        
        if (metadata) {
          this.originalMetadata = structuredClone(metadata);
          this.populateFormFromMetadata(metadata);
          this.messageService.add({
            severity: "success",
            summary: "Restored",
            detail: "Metadata loaded from ComicInfo.xml",
          });
        } else {
          this.messageService.add({
            severity: "warn",
            summary: "No Data",
            detail: "ComicInfo.xml not found or empty.",
          });
        }
        this.isLoading = false;
      },
      error: (err) => {
        console.error("Error loading ComicInfo.xml metadata:", err);
        console.error(err.message);
        this.isLoading = false;
        this.messageService.add({
          severity: "error",
          summary: "Error",
          detail: err?.error?.message || "Failed to load ComicInfo.xml",
        });
      },
    });
  }

  restoreMetadata() {
    this.dialogService.open(MetadataRestoreDialogComponent, {
      header: "Restore Metadata from Backup",
      modal: true,
      closable: true,
      data: {
        bookId: [this.currentBookId],
      },
    });
  }

  autoFetch(bookId: number) {
    this.refreshingBookIds.add(bookId);
    this.isAutoFetching = true;
    const request: MetadataRefreshRequest = {
      quick: true,
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: [bookId],
    };
    this.bookService.autoRefreshMetadata(request).subscribe();
    setTimeout(() => {
      this.isAutoFetching = false;
      this.refreshingBookIds.delete(bookId);
    }, 15000);
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
    const ref = this.dialogService.open(CoverSearchComponent, {
      header: "Search Cover",
      modal: true,
      closable: true,
      data: {
        bookId: [this.currentBookId],
      },
      style: {
        width: "90vw",
        height: "90vh",
        maxWidth: "1200px",
        position: "absolute",
      },
    });

    ref.onClose.subscribe((result) => {
      if (result) {
        this.metadataForm.get("thumbnailUrl")?.setValue(result);
        this.onSave();
      }
    });
  }
}
