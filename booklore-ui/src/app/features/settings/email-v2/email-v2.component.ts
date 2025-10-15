import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {Divider} from 'primeng/divider';
import {EmailV2ProviderComponent} from './email-v2-provider/email-v2-provider.component';
import {EmailV2RecipientComponent} from './email-v2-recipient/email-v2-recipient.component';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {UserService} from '../user-management/user.service';
import {Subject} from 'rxjs';
import {filter, takeUntil, tap} from 'rxjs/operators';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-email-v2',
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    Divider,
    EmailV2ProviderComponent,
    EmailV2RecipientComponent,
    ExternalDocLinkComponent
  ],
  templateUrl: './email-v2.component.html',
  styleUrls: ['./email-v2.component.scss'],
})
export class EmailV2Component implements OnInit, OnDestroy {
  private userService = inject(UserService);
  private readonly destroy$ = new Subject<void>();

  hasPermission = false;

  ngOnInit(): void {
    this.userService.userState$.pipe(
      filter(state => !!state?.user && state.loaded),
      takeUntil(this.destroy$),
      tap(state => {
        this.hasPermission = !!(state.user?.permissions.canEmailBook || state.user?.permissions.admin);
      })
    ).subscribe();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
