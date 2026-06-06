import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {DialogLauncherService} from '../services/dialog-launcher.service';

export interface IconSelection {
  type: 'PRIME_NG' | 'CUSTOM_SVG';
  value: string;
}

@Injectable({providedIn: 'root'})
export class IconPickerService {
  private dialogLauncherService = inject(DialogLauncherService);

  open(): Observable<IconSelection> {
    const ref = this.dialogLauncherService.openIconPickerDialog();
    return ref!.onClose as Observable<IconSelection>;
  }
}
