import {Component, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
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
  imports: [
    DatePipe,
    Button,
    TranslocoDirective
  ]
})
export class BookdropFilesWidgetComponent {
  private readonly bookdropFileService = inject(BookdropFileService);
  private readonly router = inject(Router);
  protected readonly summary = toSignal(this.bookdropFileService.summary$, {
    initialValue: {
      pendingCount: 0,
      totalCount: 0,
      lastUpdatedAt: undefined,
    },
  });

  openReviewDialog(): void {
    this.router.navigate(['/bookdrop'], {queryParams: {reload: Date.now()}});
  }
}
