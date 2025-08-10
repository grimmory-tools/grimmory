import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {BookdropFileNotification, BookdropFileService} from '../bookdrop-file.service';
import {DatePipe} from '@angular/common';
import {Router} from '@angular/router';
import {Button} from 'primeng/button';

@Component({
  selector: 'app-bookdrop-files-widget-component',
  standalone: true,
  templateUrl: './bookdrop-files-widget.component.html',
  styleUrl: './bookdrop-files-widget.component.scss',
  imports: [
    DatePipe,
    Button
  ]
})
export class BookdropFilesWidgetComponent implements OnInit, OnDestroy {
  pendingCount = 0;
  totalCount = 0;
  lastUpdatedAt?: string;

  private destroy$ = new Subject<void>();
  private bookdropFileService = inject(BookdropFileService);
  private router = inject(Router);

  ngOnInit(): void {
    this.bookdropFileService.summary$
      .pipe(takeUntil(this.destroy$))
      .subscribe((summary: BookdropFileNotification) => {
        this.pendingCount = summary.pendingCount;
        this.totalCount = summary.totalCount;
        this.lastUpdatedAt = summary.lastUpdatedAt;
      });
  }

  openReviewDialog(): void {
    this.router.navigate(['/bookdrop']);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
