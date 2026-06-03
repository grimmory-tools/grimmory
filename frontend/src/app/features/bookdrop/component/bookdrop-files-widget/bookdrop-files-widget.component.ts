import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {BookdropFileService} from '../../service/bookdrop-file.service';
import {DatePipe} from '@angular/common';
import {Router} from '@angular/router';
import {Button} from 'primeng/button';
import {TranslocoDirective} from '@jsverse/transloco';

@Component({
  selector: 'app-bookdrop-files-widget-component',
  standalone: true,
  templateUrl: './bookdrop-files-widget.component.html',
  styleUrl: './bookdrop-files-widget.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    Button,
    TranslocoDirective
  ]
})
export class BookdropFilesWidgetComponent {
  private readonly bookdropFileService = inject(BookdropFileService);
  private readonly router = inject(Router);
  private readonly summary = this.bookdropFileService.summary;
  protected readonly pendingCount = computed(() => this.summary().pendingCount);
  protected readonly lastUpdatedAt = computed(() => this.summary().lastUpdatedAt);

  openReviewDialog(): void {
    this.router.navigate(['/bookdrop'], {queryParams: {reload: Date.now()}});
  }
}
