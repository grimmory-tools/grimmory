import {Component, OnDestroy} from '@angular/core';
import {DatePipe, NgClass} from '@angular/common';
import {BookdropFinalizeResult} from '../bookdrop-file-task.service';
import {DynamicDialogConfig, DynamicDialogRef} from "primeng/dynamicdialog";

@Component({
  selector: 'app-bookdrop-finalize-result-dialog-component',
  imports: [
    NgClass,
    DatePipe
  ],
  templateUrl: './bookdrop-finalize-result-dialog-component.html',
  styleUrl: './bookdrop-finalize-result-dialog-component.scss'
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
