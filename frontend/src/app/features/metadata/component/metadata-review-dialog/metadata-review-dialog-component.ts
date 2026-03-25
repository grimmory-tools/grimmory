import {Component, computed, effect, inject, OnInit, signal, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {FetchedProposal, MetadataTaskService} from '../../../book/service/metadata-task';
import {BookService} from '../../../book/service/book.service';
import {Book} from '../../../book/model/book.model';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Button} from 'primeng/button';
import {Divider} from 'primeng/divider';
import {ProgressBar} from 'primeng/progressbar';
import {Tooltip} from 'primeng/tooltip';
import {MetadataProgressService} from '../../../../shared/service/metadata-progress.service';
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

  loading = true;
  readonly proposals = signal<FetchedProposal[]>([]);
  readonly currentIndex = signal(0);
  readonly currentBook = computed<Book | null>(() => {
    const proposal = this.proposals()[this.currentIndex()];
    if (!proposal) {
      return null;
    }

    return this.bookService.findBookById(proposal.bookId) ?? null;
  });

  constructor() {
    effect(() => {
      const proposals = this.proposals();
      if (proposals.length === 0) {
        return;
      }

      const bookIds = new Set(proposals.map(proposal => proposal.bookId));
      const matchedBooks = this.bookService.books().filter(book => bookIds.has(book.id));
      this.loading = matchedBooks.length !== bookIds.size;
    });
  }

  ngOnInit() {
    const taskId = this.config.data?.taskId;
    if (!taskId) {
      this.dialogRef.close();
      return;
    }

    this.metadataTaskService.getTaskWithProposals(taskId).subscribe({
      next: (task) => {
        this.proposals.set(task.proposals || []);
        this.currentIndex.set(0);
      },
      error: () => {
        this.dialogRef.close();
      },
    });
  }

  get currentProposal(): FetchedProposal | null {
    return this.proposals()[this.currentIndex()] ?? null;
  }

  onSave(): void {
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
    const nextIndex = this.currentIndex() + 1;
    if (nextIndex >= this.proposals().length) {
      this.dialogRef.close();
    } else {
      this.currentIndex.set(nextIndex);
    }
  }

  lockAllMetadata(): void {
    this.pickerComponent?.lockAll();
  }

  unlockAllMetadata(): void {
    this.pickerComponent?.unlockAll();
  }

  get isLast(): boolean {
    return this.currentIndex() === this.proposals().length - 1;
  }

  close(): void {
    this.dialogRef.close();
  }
}
