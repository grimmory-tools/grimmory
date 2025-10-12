import {Component, DestroyRef, inject, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {FetchedProposal, MetadataTaskService} from '../../../book/service/metadata-task';
import {BookService} from '../../../book/service/book.service';
import {Book} from '../../../book/model/book.model';
import {BehaviorSubject, Observable} from 'rxjs';
import {map} from 'rxjs/operators';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Button} from 'primeng/button';
import {Divider} from 'primeng/divider';
import {ProgressBar} from 'primeng/progressbar';
import {Tooltip} from 'primeng/tooltip';
import {MetadataProgressService} from '../../../../shared/service/metadata-progress-service';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MetadataPickerComponent} from '../book-metadata-center/metadata-picker/metadata-picker.component';

@Component({
  selector: 'app-metadata-review-dialog-component',
  standalone: true,
  templateUrl: './metadata-review-dialog-component.html',
  styleUrls: ['./metadata-review-dialog-component.scss'],
  imports: [CommonModule, MetadataPickerComponent, ProgressSpinner, Button, Divider, ProgressBar, Tooltip],
})
export class MetadataReviewDialogComponent implements OnInit {

  @ViewChild(MetadataPickerComponent)
  pickerComponent!: MetadataPickerComponent;

  private config = inject(DynamicDialogConfig);
  private dialogRef = inject(DynamicDialogRef);
  private metadataTaskService = inject(MetadataTaskService);
  private bookService = inject(BookService);
  private progressService = inject(MetadataProgressService);
  private destroyRef = inject(DestroyRef);

  proposals: FetchedProposal[] = [];
  currentBooks: Record<number, Book> = {};
  loading = true;
  currentIndex = 0;
  private initialized = false;

  private currentIndexSubject = new BehaviorSubject<number>(0);

  book$: Observable<Book | null> = this.currentIndexSubject.pipe(
    map(idx => {
      const proposal = this.proposals[idx];
      if (!proposal) return null;
      return this.currentBooks[proposal.bookId] ?? null;
    })
  );

  ngOnInit() {
    const taskId = this.config.data?.taskId;
    if (!taskId) {
      this.dialogRef.close();
      return;
    }

    this.metadataTaskService.getTaskWithProposals(taskId).subscribe({
      next: (task) => {
        this.proposals = task.proposals || [];
        const bookIds = new Set(this.proposals.map(p => p.bookId));

        this.bookService.bookState$
          .pipe(
            map(bookState => bookState.books?.filter(book => bookIds.has(book.id)) ?? []),
            takeUntilDestroyed(this.destroyRef)
          )
          .subscribe((matchedBooks) => {

            if (!this.initialized && matchedBooks.length === bookIds.size) {
              this.currentBooks = matchedBooks.reduce((map, book) => {
                map[book.id] = book;
                return map;
              }, {} as Record<number, Book>);
              this.loading = false;
              this.currentIndex = 0;
              this.currentIndexSubject.next(0);
              this.initialized = true;
            } else if (!this.initialized) {
              this.loading = true;
            }
          });
      },
      error: () => {
        this.dialogRef.close();
      },
    });
  }

  get currentProposal(): FetchedProposal | null {
    return this.proposals[this.currentIndex] ?? null;
  }

  onSave(updatedFields: Partial<FetchedProposal>): void {
    const currentProposal = this.currentProposal;
    if (!currentProposal) return;
    this.pickerComponent.onSave();
    this.metadataTaskService.updateProposalStatus(currentProposal.taskId, currentProposal.proposalId, 'ACCEPTED').subscribe({
      next: () => {
        if (this.isLast) {
          this.metadataTaskService.deleteTask(currentProposal.taskId).subscribe(() => {
            this.progressService.clearTask(currentProposal.taskId);
          });
        }
      }
    });
  }

  onNext(): void {
    const nextIndex = this.currentIndex + 1;
    if (nextIndex >= this.proposals.length) {
      this.dialogRef.close();
    } else {
      this.currentIndex = nextIndex;
      this.currentIndexSubject.next(nextIndex);
    }
  }

  lockAllMetadata(): void {
    this.pickerComponent?.lockAll();
  }

  unlockAllMetadata(): void {
    this.pickerComponent?.unlockAll();
  }

  get isLast(): boolean {
    return this.currentIndex === this.proposals.length - 1;
  }

  close(): void {
    this.dialogRef.close();
  }
}
