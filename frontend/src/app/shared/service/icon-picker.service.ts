import {inject, Service} from '@angular/core';
import {EMPTY, from, Observable, switchMap} from 'rxjs';
import {DialogLauncherService} from '../services/dialog-launcher.service';
import {IconSelection} from '../icons/icon-selection';

@Service()
export class IconPickerService {
  private readonly dialogLauncherService = inject(DialogLauncherService);

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
