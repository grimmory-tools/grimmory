import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Select} from 'primeng/select';
import {Tooltip} from 'primeng/tooltip';
import {User, UserService, UserSettings} from '../../user-management/user.service';
import {MessageService} from 'primeng/api';
import {Observable, Subscription} from 'rxjs';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-sidebar-filter-sorting-prefs-component',
  imports: [
    Select,
    Tooltip,
    FormsModule
  ],
  templateUrl: './sidebar-filter-sorting-prefs-component.html',
  styleUrl: './sidebar-filter-sorting-prefs-component.scss'
})
export class SidebarFilterSortingPrefsComponent implements OnInit, OnDestroy {

  readonly filterSortingOptions = [
    {label: 'Alphabetical (A–Z)', value: 'alphabetical'},
    {label: 'Book Count (High to Low)', value: 'count'}
  ];

  selectedFilterSorting: 'alphabetical' | 'count' = 'count';

  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);

  userData$: Observable<User | null> = this.userService.userState$;
  private subscription?: Subscription;
  private currentUser: User | null = null;

  ngOnInit(): void {
    this.subscription = this.userData$.subscribe(user => {
      if (user) {
        this.currentUser = user;
        this.loadPreferences(user.userSettings);
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private loadPreferences(settings: UserSettings): void {
    console.log(settings.filterSortingMode)
    this.selectedFilterSorting = settings.filterSortingMode;
  }

  private updatePreference(path: string[], value: any): void {
    if (!this.currentUser) return;
    let target: any = this.currentUser.userSettings;
    for (let i = 0; i < path.length - 1; i++) {
      target = target[path[i]] ||= {};
    }
    target[path.at(-1)!] = value;

    const [rootKey] = path;
    const updatedValue = this.currentUser.userSettings[rootKey as keyof UserSettings];
    this.userService.updateUserSetting(this.currentUser.id, rootKey, updatedValue);
    this.messageService.add({
      severity: 'success',
      summary: 'Preferences Updated',
      detail: 'Your preferences have been saved successfully.',
      life: 2000
    });
  }

  onFilterSortingChange(): void {
    this.updatePreference(['filterSortingMode'], this.selectedFilterSorting);
  }
}
