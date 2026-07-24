import {Component, inject, OnDestroy} from '@angular/core';
import {DatePipe, NgClass} from '@angular/common';
import {BookdropFinalizeResult} from '../../service/bookdrop.service';
import {DynamicDialogConfig, DynamicDialogRef} from "@openng/optimus-ui/dynamicdialog";
import {Button} from '@openng/optimus-ui/button';
import {TranslocoDirective} from '@jsverse/transloco';

@Component({
  selector: 'app-bookdrop-finalize-result-dialog',
  imports: [
    NgClass,
    DatePipe,
    Button,
    TranslocoDirective
  ],
  templateUrl: './bookdrop-finalize-result-dialog.component.html',
  styleUrl: './bookdrop-finalize-result-dialog.component.scss'
})
export class BookdropFinalizeResultDialogComponent implements OnDestroy {
  public ref = inject(DynamicDialogRef);
  public config = inject(DynamicDialogConfig);

  result: BookdropFinalizeResult = this.config.data.result;

  ngOnDestroy(): void {
    this.ref?.close();
  }
}
