import {Component, DestroyRef, effect, inject, OnInit, signal} from '@angular/core';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {UserService} from './user-management/user.service';
import {GlobalPreferencesComponent} from './global-preferences/global-preferences.component';
import {ActivatedRoute, Router} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {UserManagementComponent} from './user-management/user-management.component';
import {AuthenticationSettingsComponent} from '../../core/security/oauth2-management/authentication-settings.component';
import {ViewPreferencesParentComponent} from './view-preferences-parent/view-preferences-parent.component';
import {ReaderPreferences} from './reader-preferences/reader-preferences.component';
import {FileNamingPatternComponent} from './file-naming-pattern/file-naming-pattern.component';
import {TaskManagementComponent} from './task-management/task-management.component';
import {AuditLogsComponent} from './audit-logs/audit-logs.component';
import {OpdsSettings} from './opds-settings/opds-settings';
import {MetadataSettingsComponent} from './metadata-settings/metadata-settings-component';
import {DeviceSettingsComponent} from './device-settings/device-settings-component';
import {LibraryMetadataSettingsComponent} from './library-metadata-settings/library-metadata-settings.component';
import {PageTitleService} from "../../shared/service/page-title.service";
import {EmailV2Component} from './email-v2/email-v2.component';
import {TranslocoDirective} from '@jsverse/transloco';

export enum SettingsTab {
  ReaderSettings = 'reader',
  ViewPreferences = 'view',
  DeviceSettings = 'device',
  UserManagement = 'user',
  EmailSettingsV2 = 'email-v2',
  NamingPattern = 'naming-pattern',
  MetadataSettings = 'metadata',
  LibraryMetadataSettings = 'metadata-library',
  ApplicationSettings = 'application',
  AuthenticationSettings = 'authentication',
  OpdsV2 = 'opds',
  Tasks = 'task',
  AuditLogs = 'audit-logs',
}

@Component({
  selector: 'app-settings',
  imports: [
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    GlobalPreferencesComponent,
    UserManagementComponent,
    AuthenticationSettingsComponent,
    ViewPreferencesParentComponent,
    ReaderPreferences,
    MetadataSettingsComponent,
    DeviceSettingsComponent,
    FileNamingPatternComponent,
    OpdsSettings,
    LibraryMetadataSettingsComponent,
    TaskManagementComponent,
    AuditLogsComponent,
    EmailV2Component,
    TranslocoDirective
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit {
  protected userService = inject(UserService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private pageTitle = inject(PageTitleService);
  private destroyRef = inject(DestroyRef);

  SettingsTab = SettingsTab;

  private validTabs = Object.values(SettingsTab);
  private _activeTab = signal<SettingsTab>(SettingsTab.ReaderSettings);

  visitedTabs = signal<Set<SettingsTab>>(new Set([
    SettingsTab.UserManagement,
    SettingsTab.EmailSettingsV2
  ]));

  get activeTab(): SettingsTab {
    return this._activeTab();
  }

  set activeTab(value: SettingsTab) {
    this._activeTab.set(value);

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {tab: value},
      queryParamsHandling: 'merge'
    });
  }

  constructor() {
    const pageTitle = inject(PageTitleService);
    pageTitle.setPageTitle('Settings');

    effect(() => {
      const active = this._activeTab();
      this.visitedTabs.update(set => {
        if (set.has(active)) return set;
        const newSet = new Set(set);
        newSet.add(active);
        return newSet;
      });
    });
  }

  ngOnInit(): void {

    // Initialize from snapshot synchronously to ensure p-tabs (lazy) picks up the correct value on first render
    const initialTab = this.route.snapshot.queryParams['tab'];
    if (this.validTabs.includes(initialTab)) {
      this._activeTab.set(initialTab as SettingsTab);
    }

    this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const tabParam = params['tab'];
      if (this.validTabs.includes(tabParam)) {
        this._activeTab.set(tabParam as SettingsTab);
      } else {
        const defaultTab = SettingsTab.ReaderSettings;
        this._activeTab.set(defaultTab);
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: {tab: defaultTab},
          queryParamsHandling: 'merge',
          replaceUrl: true
        });
      }
    });
  }

}
