import {Component, inject} from '@angular/core';
import {KoreaderSettingsComponent} from './component/koreader-settings/koreader-settings-component';
import {KoboSyncSettingsComponent} from './component/kobo-sync-settings/kobo-sync-settings-component';
import {HardcoverSettingsComponent} from './component/hardcover-settings/hardcover-settings-component';
import {UserService} from '../user-management/user.service';

@Component({
  selector: 'app-device-settings-component',
  imports: [
    KoreaderSettingsComponent,
    KoboSyncSettingsComponent,
    HardcoverSettingsComponent
  ],
  templateUrl: './device-settings-component.html',
  styleUrl: './device-settings-component.scss'
})
export class DeviceSettingsComponent {
  protected readonly userService = inject(UserService);

}
