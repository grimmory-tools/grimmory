import {inject, Injectable} from '@angular/core';
import {EMPTY, from, Observable, switchMap} from 'rxjs';
import {DialogLauncherService} from '../services/dialog-launcher.service';

export interface IconSelection {
  type: 'PRIME_NG' | 'CUSTOM_SVG';
  value: string;
}

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
