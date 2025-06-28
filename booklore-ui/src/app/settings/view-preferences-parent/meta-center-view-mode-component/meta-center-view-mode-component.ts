import {Component, inject, OnInit} from '@angular/core';
import {Tooltip} from 'primeng/tooltip';
import {RadioButton} from 'primeng/radiobutton';
import {FormsModule} from '@angular/forms';
import {UserService} from '../../user-management/user.service';
import {MessageService} from 'primeng/api';
import {take} from 'rxjs/operators';

@Component({
  selector: 'app-meta-center-view-mode-component',
  imports: [
    Tooltip,
    RadioButton,
    FormsModule
  ],
  templateUrl: './meta-center-view-mode-component.html',
  styleUrl: './meta-center-view-mode-component.scss'
})
export class MetaCenterViewModeComponent implements OnInit {
  viewMode: 'route' | 'dialog' = 'route';

  private userService = inject(UserService);
  private messageService = inject(MessageService);

  ngOnInit(): void {
    this.userService.userState$.pipe(take(1)).subscribe(user => {
      const preference = user?.userSettings?.metadataCenterViewMode;
      if (preference === 'dialog' || preference === 'route') {
        this.viewMode = preference;
      }
    });
  }

  onViewModeChange(value: 'route' | 'dialog'): void {
    this.viewMode = value;
    this.savePreference(value);
  }

  private savePreference(value: 'route' | 'dialog'): void {
    const user = this.userService.getCurrentUser();
    if (!user) return;

    user.userSettings.metadataCenterViewMode = value;
    this.userService.updateUserSetting(user.id, 'metadataCenterViewMode', value);

    this.messageService.add({
      severity: 'success',
      summary: 'Preferences Updated',
      detail: 'Your metadata center view preference has been saved.',
      life: 1500,
    });
  }
}
