import {Component, OnDestroy} from '@angular/core';
import {DatePipe, NgClass} from '@angular/common';
import {BookdropFinalizeResult} from '../../service/bookdrop.service';
import {DynamicDialogConfig, DynamicDialogRef} from "primeng/dynamicdialog";
import {Button} from 'primeng/button';
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

  result: BookdropFinalizeResult;

  constructor(public ref: DynamicDialogRef, public config: DynamicDialogConfig) {
    this.result = config.data.result;
  }

  ngOnDestroy(): void {
    this.ref?.close();
  }
}
