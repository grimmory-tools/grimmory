import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {of, Subject} from 'rxjs';
import {debounceTime, switchMap, takeUntil} from 'rxjs/operators';
import {InputTextModule} from 'primeng/inputtext';
import {Select} from 'primeng/select';
import {Button} from 'primeng/button';
import {TooltipModule} from 'primeng/tooltip';
import {Paginator} from 'primeng/paginator';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {NotebookService} from '../../service/notebook.service';
import {NotebookEntry, NotebookPage} from '../../model/notebook.model';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';

interface BookGroup {
  bookId: number;
  bookTitle: string;
  thumbnailUrl: string;
  entries: NotebookEntry[];
}

interface BookOption {
  label: string;
  value: number;
}

const EMPTY_PAGE: NotebookPage = {
  content: [],
  page: { totalElements: 0, totalPages: 0, number: 0, size: 0 },
};

@Component({
  selector: 'app-notebook',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    InputTextModule,
    Select,
    Button,
    TooltipModule,
    Paginator,
    TranslocoDirective,
  ],
  templateUrl: './notebook.component.html',
  styleUrls: ['./notebook.component.scss'],
})
export class NotebookComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly searchSubject = new Subject<void>();
  private readonly loadTrigger$ = new Subject<void>();
  private readonly bookFilterSubject = new Subject<string>();
  private readonly notebookService = inject(NotebookService);
  private readonly urlHelper = inject(UrlHelperService);
  private readonly pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);

  filteredGroups: BookGroup[] = [];
  totalEntries = 0;
  loading = true;
  exporting = false;

  searchQuery = '';
  showHighlights = true;
  showNotes = true;
  showBookmarks = true;
  sortNewest = true;

  bookOptions: BookOption[] = [];
  selectedBookId: number | null = null;

  page = 0;
  pageSize = 50;
  first = 0;

  private collapsedGroups = new Set<number>();

  ngOnInit(): void {
    this.pageTitle.setPageTitle(this.t.translate('notebook.pageTitle'));

    this.loadTrigger$.pipe(
      switchMap(() => {
        const types = this.activeTypes;
        if (types.length === 0) {
          return of(EMPTY_PAGE);
        }
        this.loading = true;
        return this.notebookService.getNotebookEntries(
          this.page, this.pageSize, types, this.selectedBookId, this.searchQuery, this.sortDirection
        );
      }),
      takeUntil(this.destroy$)
    ).subscribe(result => {
      this.totalEntries = result.page.totalElements;
      this.groupEntries(result.content);
      this.loading = false;
    });

    this.searchSubject.pipe(
      debounceTime(300),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.page = 0;
      this.first = 0;
      this.loadTrigger$.next();
    });

    this.bookFilterSubject.pipe(
      debounceTime(300),
      switchMap(filter => this.notebookService.getBooksWithAnnotations(filter || undefined)),
      takeUntil(this.destroy$)
    ).subscribe(books => {
      this.updateBookOptions(books.map(b => ({label: b.bookTitle, value: b.bookId})));
    });

    this.loadBooks();
    this.loadTrigger$.next();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearchChange(): void {
    this.searchSubject.next();
  }

  onFilterChange(): void {
    this.page = 0;
    this.first = 0;
    this.loadTrigger$.next();
  }

  onBookFilter(event: { filter: string }): void {
    this.bookFilterSubject.next(event.filter);
  }

  onPageChange(event: { page?: number; first?: number; rows?: number }): void {
    this.page = event.page ?? 0;
    this.first = event.first ?? 0;
    this.pageSize = event.rows ?? this.pageSize;
    this.loadTrigger$.next();
  }

  private get activeTypes(): string[] {
    const types: string[] = [];
    if (this.showHighlights) types.push('HIGHLIGHT');
    if (this.showNotes) types.push('NOTE');
    if (this.showBookmarks) types.push('BOOKMARK');
    return types;
  }

  private get sortDirection(): string {
    return this.sortNewest ? 'desc' : 'asc';
  }

  private loadBooks(): void {
    this.notebookService.getBooksWithAnnotations()
      .pipe(takeUntil(this.destroy$))
      .subscribe(books => {
        this.bookOptions = books.map(b => ({label: b.bookTitle, value: b.bookId}));
      });
  }

  private updateBookOptions(options: BookOption[]): void {
    if (this.selectedBookId !== null && !options.some(o => o.value === this.selectedBookId)) {
      const current = this.bookOptions.find(o => o.value === this.selectedBookId);
      if (current) {
        options = [current, ...options];
      }
    }
    this.bookOptions = options;
  }

  private groupEntries(entries: NotebookEntry[]): void {
    const groupMap = new Map<number, BookGroup>();
    for (const entry of entries) {
      if (!groupMap.has(entry.bookId)) {
        const isAudiobook = entry.primaryBookType === 'AUDIOBOOK';
        groupMap.set(entry.bookId, {
          bookId: entry.bookId,
          bookTitle: entry.bookTitle,
          thumbnailUrl: isAudiobook
            ? this.urlHelper.getAudiobookThumbnailUrl(entry.bookId)
            : this.urlHelper.getDirectThumbnailUrl(entry.bookId),
          entries: [],
        });
      }
      groupMap.get(entry.bookId)!.entries.push(entry);
    }
    this.filteredGroups = Array.from(groupMap.values());
  }

  toggleSort(): void {
    this.sortNewest = !this.sortNewest;
    this.page = 0;
    this.first = 0;
    this.loadTrigger$.next();
  }

  toggleGroup(bookId: number): void {
    if (this.collapsedGroups.has(bookId)) {
      this.collapsedGroups.delete(bookId);
    } else {
      this.collapsedGroups.add(bookId);
    }
  }

  isGroupCollapsed(bookId: number): boolean {
    return this.collapsedGroups.has(bookId);
  }

  getTypeIcon(type: string): string {
    switch (type) {
      case 'HIGHLIGHT': return 'pi pi-highlighter';
      case 'NOTE': return 'pi pi-file-edit';
      case 'BOOKMARK': return 'pi pi-bookmark';
      default: return 'pi pi-circle';
    }
  }

  getTypeLabel(type: string): string {
    switch (type) {
      case 'HIGHLIGHT': return this.t.translate('notebook.highlight');
      case 'NOTE': return this.t.translate('notebook.note');
      case 'BOOKMARK': return this.t.translate('notebook.bookmark');
      default: return type;
    }
  }

  exportMarkdown(): void {
    const types = this.activeTypes;
    if (types.length === 0) return;

    this.exporting = true;
    this.notebookService.getExportEntries(
      types, this.selectedBookId, this.searchQuery, this.sortDirection
    ).pipe(takeUntil(this.destroy$)).subscribe(entries => {
      this.generateMarkdownDownload(entries);
      this.exporting = false;
    });
  }

  private generateMarkdownDownload(entries: NotebookEntry[]): void {
    const groupMap = new Map<number, { bookTitle: string; entries: NotebookEntry[] }>();
    for (const entry of entries) {
      if (!groupMap.has(entry.bookId)) {
        groupMap.set(entry.bookId, {bookTitle: entry.bookTitle, entries: []});
      }
      groupMap.get(entry.bookId)!.entries.push(entry);
    }

    let md = '# Notebook Export\n\n';
    for (const group of groupMap.values()) {
      md += `## ${group.bookTitle}\n\n`;
      let currentChapter = '';
      for (const entry of group.entries) {
        if (entry.chapterTitle && entry.chapterTitle !== currentChapter) {
          currentChapter = entry.chapterTitle;
          md += `### ${currentChapter}\n\n`;
        }
        if (entry.text) {
          md += `> ${entry.text}\n\n`;
        }
        if (entry.note) {
          md += `**Note:** ${entry.note}\n\n`;
        }
        md += '---\n\n';
      }
    }

    const blob = new Blob([md], {type: 'text/markdown'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'notebook-export.md';
    a.click();
    URL.revokeObjectURL(url);
  }
}
