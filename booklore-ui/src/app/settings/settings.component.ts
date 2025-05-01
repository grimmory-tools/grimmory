import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {BookPreferences} from './book-preferences/book-preferences.component';
import {UserService} from './user-management/user.service';
import {AsyncPipe, NgIf} from '@angular/common';
import {EmailComponent} from './email/email.component';
import {GlobalPreferencesComponent} from './global-preferences/global-preferences.component';
import {ActivatedRoute, Router} from '@angular/router';
import {Subscription} from 'rxjs';
import {UserManagementComponent} from './user-management/user-management.component';

export enum SettingsTab {
  BookReader = 'book-reader',
  UserManagement = 'user-management',
  Email = 'email',
  GlobalSettings = 'global-settings'
}

@Component({
  selector: 'app-settings',
  imports: [
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    BookPreferences,
    NgIf,
    AsyncPipe,
    EmailComponent,
    GlobalPreferencesComponent,
    UserManagementComponent
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit, OnDestroy {

  protected userService = inject(UserService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  private routeSub!: Subscription;

  SettingsTab = SettingsTab;

  private validTabs = Object.values(SettingsTab);
  private _activeTab: SettingsTab = SettingsTab.BookReader;

  get activeTab(): SettingsTab {
    return this._activeTab;
  }

  set activeTab(value: SettingsTab) {
    this._activeTab = value;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: value },
      queryParamsHandling: 'merge'
    });
  }

  ngOnInit(): void {
    this.routeSub = this.route.queryParams.subscribe(params => {
      const tabParam = params['tab'];
      if (this.validTabs.includes(tabParam)) {
        this._activeTab = tabParam as SettingsTab;
      } else {
        this._activeTab = SettingsTab.BookReader;
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { tab: this._activeTab },
          queryParamsHandling: 'merge',
          replaceUrl: true
        });
      }
    });
  }

  ngOnDestroy(): void {
    this.routeSub.unsubscribe();
  }
}
