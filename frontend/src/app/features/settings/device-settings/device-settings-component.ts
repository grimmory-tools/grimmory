import {Component} from '@angular/core';
import {KoreaderSettingsComponent} from './component/koreader-settings/koreader-settings-component';
import {KoboSyncSettingsComponent} from './component/kobo-sync-settings/kobo-sync-settings-component';
import {HardcoverSettingsComponent} from './component/hardcover-settings/hardcover-settings-component';

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

}
