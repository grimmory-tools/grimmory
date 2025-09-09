import { Component, inject, ViewChild } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Button } from "primeng/button";
import { ActivatedRoute } from "@angular/router";
import { AsyncPipe, NgClass, NgFor, NgIf, NgStyle } from "@angular/common";
import { map, filter, switchMap } from "rxjs/operators";
import { Observable, combineLatest } from "rxjs";
import { Book, ReadStatus } from "../../model/book.model";
import { BookService } from "../../service/book.service";
import { BookCardComponent } from "../book-browser/book-card/book-card.component";
import { CoverScalePreferenceService } from "../book-browser/cover-scale-preference.service";
import { Tab, TabList, TabPanel, TabPanels, Tabs } from "primeng/tabs";
import { Tag } from "primeng/tag";
import { VirtualScrollerModule } from "@iharbeck/ngx-virtual-scroller";
import { ProgressSpinner } from "primeng/progressspinner";
import { DialogService, DynamicDialogRef } from "primeng/dynamicdialog";
import { Router } from "@angular/router";

@Component({
  selector: "app-series-page",
  standalone: true,
  templateUrl: "./series-page.component.html",
  styleUrls: ["./series-page.component.scss"],
  imports: [
    AsyncPipe,
    Button,
    FormsModule,
    NgIf,
    NgFor,
    NgStyle,
    NgClass,
    BookCardComponent,
    ProgressSpinner,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    Tag,

    VirtualScrollerModule,
  ],
})
export class SeriesPageComponent {

  private route = inject(ActivatedRoute);
  private bookService = inject(BookService);
  protected coverScalePreferenceService = inject(CoverScalePreferenceService);
  private metadataCenterViewMode: "route" | "dialog" = "route";
  private dialogRef?: DynamicDialogRef;
  private router = inject(Router);

  tab: string = "view";
  isExpanded = false;


  seriesParam$: Observable<string> = this.route.paramMap.pipe(
    map((params) => params.get("seriesName") || ""),
    map((name) => decodeURIComponent(name))
  );

  booksInSeries$: Observable<Book[]> = this.bookService.bookState$.pipe(
    filter((state) => state.loaded && !!state.books),
    map((state) => state.books || [])
  );

  filteredBooks$: Observable<Book[]> = combineLatest([
    this.seriesParam$.pipe(map((n) => n.trim().toLowerCase())),
    this.booksInSeries$,
  ]).pipe(
    map(([seriesName, books]) => {
      const inSeries = books.filter(
        (b) => b.metadata?.seriesName?.toLowerCase() === seriesName
      );
      return inSeries.sort((a, b) => {
        const aNum = a.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
        const bNum = b.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
        return aNum - bNum;
      });
    })
  );

  seriesTitle$: Observable<string> = combineLatest([
    this.seriesParam$,
    this.filteredBooks$,
  ]).pipe(map(([param, books]) => books[0]?.metadata?.seriesName || param));

  yearsRange$: Observable<string | null> = this.filteredBooks$.pipe(
    map((books) => {
      const years = books
        .map((b) => b.metadata?.publishedDate)
        .filter((d): d is string => !!d)
        .map((d) => {
          const match = d.match(/\d{4}/);
          return match ? parseInt(match[0], 10) : null;
        })
        .filter((y): y is number => y !== null);

      if (years.length === 0) return null;
      const min = Math.min(...years);
      const max = Math.max(...years);
      return min === max ? String(min) : `${min}-${max}`;
    })
  );

  firstBookWithDesc$: Observable<Book> = this.filteredBooks$.pipe(
    map((books) => books[0]),
    filter((b): b is Book => !!b),
    switchMap((b) => this.bookService.getBookByIdFromAPI(b.id, true))
  );

  firstDescription$: Observable<string> = this.firstBookWithDesc$.pipe(
    map((b) => b.metadata?.description || "")
  );

  seriesReadStatus$: Observable<ReadStatus> = this.filteredBooks$.pipe(
    map((books) => {
      if (!books || books.length === 0) return ReadStatus.UNREAD;
      const statuses = books.map((b) => (b.readStatus as ReadStatus) ?? ReadStatus.UNREAD);

      const hasWontRead = statuses.includes(ReadStatus.WONT_READ);
      if (hasWontRead) return ReadStatus.WONT_READ;

      const hasAbandoned = statuses.includes(ReadStatus.ABANDONED);
      if (hasAbandoned) return ReadStatus.ABANDONED;

      const allRead = statuses.every((s) => s === ReadStatus.READ);
      if (allRead) return ReadStatus.READ;

      const someRead = statuses.some((s) => s === ReadStatus.READ);
      if (someRead) return ReadStatus.PARTIALLY_READ;

      const allUnread = statuses.every((s) => s === ReadStatus.UNREAD);
      if (allUnread) return ReadStatus.UNREAD;

      return ReadStatus.PARTIALLY_READ;
    })
  );

  get currentCardSize() {
    return this.coverScalePreferenceService.currentCardSize;
  }

  get gridColumnMinWidth(): string {
    return this.coverScalePreferenceService.gridColumnMinWidth;
  }

  goToAuthorBooks(author: string): void {
    this.handleMetadataClick("author", author);
  }

  goToCategory(category: string): void {
    this.handleMetadataClick("category", category);
  }

  goToPublisher(publisher: string): void {
    this.handleMetadataClick("publisher", publisher);
  }

  private navigateToFilteredBooks(
    filterKey: string,
    filterValue: string
  ): void {
    this.router.navigate(["/all-books"], {
      queryParams: {
        view: "grid",
        sort: "title",
        direction: "asc",
        sidebar: true,
        filter: `${filterKey}:${filterValue}`,
      },
    });
  }

  private handleMetadataClick(filterKey: string, filterValue: string): void {
    if (this.metadataCenterViewMode === "dialog") {
      this.dialogRef?.close();
      setTimeout(
        () => this.navigateToFilteredBooks(filterKey, filterValue),
        200
      );
    } else {
      this.navigateToFilteredBooks(filterKey, filterValue);
    }
  }

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
  }

   getStatusLabel(value: string | ReadStatus | null | undefined): string {
    const v = (value ?? '').toString().toUpperCase();
    switch (v) {
      case ReadStatus.UNREAD:
        return 'UNREAD';
      case ReadStatus.READ:
        return 'READ';
      case ReadStatus.PARTIALLY_READ:
        return 'PARTIALLY READ';
      case ReadStatus.ABANDONED:
        return 'ABANDONED';
      case ReadStatus.WONT_READ:
        return "WON'T READ";
      default:
        return 'UNSET';
    }
  }

  getStatusSeverityClass(status: string): string {
    const normalized = status?.toUpperCase();
    switch (normalized) {
      case "UNREAD":
        return "bg-gray-500";
      case "READ":
        return "bg-green-600";
      case "PARTIALLY_READ":
        return "bg-yellow-600";
      case "ABANDONED":
        return "bg-red-600";
      case "WONT_READ":
        return "bg-pink-700";
      default:
        return "bg-gray-600";
    }
  }
}
