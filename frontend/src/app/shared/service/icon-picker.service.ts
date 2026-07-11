import {inject, Injectable} from '@angular/core';
import {EMPTY, from, Observable, switchMap} from 'rxjs';
import {DialogLauncherService} from '../services/dialog-launcher.service';
import {IconSelection} from '../icons/icon-selection';

@Injectable({providedIn: 'root'})
export class IconPickerService {
  private dialogLauncherService = inject(DialogLauncherService);

  open(): Observable<IconSelection> {
    return from(this.dialogLauncherService.openIconPickerDialog()).pipe(
      switchMap(ref => {
        if (!ref) {
          return EMPTY;
        }
        return ref.onClose as Observable<IconSelection>;
      })
    );
  }
}
