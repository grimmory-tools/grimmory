import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed, toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute, Router} from '@angular/router';
import {TableLazyLoadEvent, TableModule} from '@openng/optimus-ui/table';
import {Select} from '@openng/optimus-ui/select';
import {DatePicker} from '@openng/optimus-ui/datepicker';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Subscription, interval} from 'rxjs';
import {AuditLog, AuditLogService} from './audit-log.service';
import {TagColor, TagComponent} from '../../../shared/components/tag/tag.component';

const ACTION_COLOR_GROUPS: [TagColor, string[]][] = [
  ['red', ['USER_DELETED', 'LIBRARY_DELETED', 'BOOK_DELETED', 'SHELF_DELETED', 'MAGIC_SHELF_DELETED', 'EMAIL_PROVIDER_DELETED', 'OPDS_USER_DELETED', 'AUTHOR_DELETED']],
  ['green', ['USER_CREATED', 'LIBRARY_CREATED', 'BOOK_UPLOADED', 'SHELF_CREATED', 'MAGIC_SHELF_CREATED', 'EMAIL_PROVIDER_CREATED', 'OPDS_USER_CREATED', 'LOGIN_SUCCESS']],
  ['blue', ['USER_UPDATED', 'LIBRARY_UPDATED', 'SHELF_UPDATED', 'MAGIC_SHELF_UPDATED', 'EMAIL_PROVIDER_UPDATED', 'OPDS_USER_UPDATED']],
  ['purple', ['METADATA_UPDATED', 'AUTHOR_METADATA_UPDATED']],
  ['orange', ['SETTINGS_UPDATED', 'OIDC_CONFIG_CHANGED', 'NAMING_PATTERN_CHANGED']],
  ['amber', ['PASSWORD_CHANGED', 'PERMISSIONS_CHANGED']],
  ['fuchsia', ['LOGIN_FAILED']],
  ['pink', ['LOGIN_RATE_LIMITED', 'REFRESH_RATE_LIMITED']],
  ['teal', ['LIBRARY_SCANNED', 'BOOK_SENT', 'BOOK_FILE_DETACHED', 'DUPLICATE_BOOKS_MERGED']],
  ['indigo', ['TASK_EXECUTED']],
];

const ACTION_COLOR_MAP = new Map<string, TagColor>(
  ACTION_COLOR_GROUPS.flatMap(([color, actions]) => actions.map(a => [a, color]))
);

interface ActionOption {
  label: string;
  value: string;
  translationKey: string;
}

interface UsernameOption {
  label: string;
  value: string;
}

@Component({
  selector: 'app-audit-logs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    TableModule, Select, DatePicker, FormsModule, TranslocoDirective, TagComponent],
  templateUrl: './audit-logs.component.html',
  styleUrl: './audit-logs.component.scss'
})
export class AuditLogsComponent implements OnInit {
  private readonly auditLogService = inject(AuditLogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly t = inject(TranslocoService);
  private readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});

  readonly logs = signal<AuditLog[]>([]);
  readonly totalRecords = signal(0);
  private readonly usernames = signal<string[]>([]);
  rows = 25;
  readonly loading = signal(false);
  selectedAction: string | null = null;
  selectedUsername: string | null = null;
  dateRange: Date[] | null = null;
  expandedRows = new Set<number>();
  autoRefresh = false;
  private autoRefreshSub?: Subscription;

  readonly usernameOptions = computed<UsernameOption[]>(() => {
    this.activeLang();
    return [
      {label: this.t.translate('settingsAuditLogs.allUsers'), value: ''},
      ...this.usernames().map(username => ({label: username, value: username}))
    ];
  });

  readonly actionOptions = computed<ActionOption[]>(() => {
    this.activeLang();
    return [
      {translationKey: 'settingsAuditLogs.allActions', value: ''},
      ...[
        'LOGIN_SUCCESS', 'LOGIN_FAILED', 'USER_CREATED', 'USER_UPDATED', 'USER_DELETED',
        'PASSWORD_CHANGED', 'LIBRARY_CREATED', 'LIBRARY_UPDATED', 'LIBRARY_DELETED',
        'LIBRARY_SCANNED', 'BOOK_UPLOADED', 'BOOK_DELETED', 'PERMISSIONS_CHANGED',
        'METADATA_UPDATED', 'SETTINGS_UPDATED', 'OIDC_CONFIG_CHANGED', 'TASK_EXECUTED',
        'BOOK_SENT', 'SHELF_CREATED', 'SHELF_UPDATED', 'SHELF_DELETED',
        'MAGIC_SHELF_CREATED', 'MAGIC_SHELF_UPDATED', 'MAGIC_SHELF_DELETED',
        'EMAIL_PROVIDER_CREATED', 'EMAIL_PROVIDER_UPDATED', 'EMAIL_PROVIDER_DELETED',
        'OPDS_USER_CREATED', 'OPDS_USER_DELETED', 'OPDS_USER_UPDATED',
        'NAMING_PATTERN_CHANGED', 'LOGIN_RATE_LIMITED', 'REFRESH_RATE_LIMITED',
        'DUPLICATE_BOOKS_MERGED', 'BOOK_FILE_DETACHED', 'AUTHOR_METADATA_UPDATED', 'AUTHOR_DELETED',
      ].map(value => ({translationKey: `settingsAuditLogs.actions.${value}`, value}))
    ].map(option => ({...option, label: this.t.translate(option.translationKey)}));
  });

  currentPage = 0;

  ngOnInit(): void {
    this.restoreFromQueryParams();
    this.loadUsernames();
    this.loadLogs();
  }

  loadUsernames(): void {
    this.auditLogService.getDistinctUsernames().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (usernames) => {
        this.usernames.set(usernames);
      }
    });
  }

  loadLogs(showLoading = true): void {
    if (showLoading) {
      this.loading.set(true);
    }
    const action = this.selectedAction || undefined;
    const username = this.selectedUsername || undefined;
    const from = this.dateRange?.[0] ? this.formatDateTime(this.dateRange[0]) : undefined;
    const to = this.dateRange?.[1] ? this.formatDateTime(this.dateRange[1], true) : undefined;
    this.auditLogService.getAuditLogs(this.currentPage, this.rows, action, username, from, to).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (response) => {
        this.logs.set(response.content);
        this.totalRecords.set(response.page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    this.rows = event.rows ?? this.rows;
    this.currentPage = (event.first ?? 0) / this.rows;
    this.updateQueryParams();
    this.loadLogs();
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.updateQueryParams();
    this.loadLogs();
  }

  onDateRangeChange(): void {
    if (!this.dateRange || (this.dateRange[0] && this.dateRange[1])) {
      this.onFilterChange();
    }
  }

  toggleAutoRefresh(): void {
    this.autoRefresh = !this.autoRefresh;
    if (this.autoRefresh) {
      this.autoRefreshSub?.unsubscribe();
      this.autoRefreshSub = interval(10000)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.loadLogs(false));
    } else {
      this.autoRefreshSub?.unsubscribe();
    }
  }

  clearAllFilters(): void {
    this.selectedAction = null;
    this.selectedUsername = null;
    this.dateRange = null;
    this.currentPage = 0;
    this.updateQueryParams();
    this.loadLogs();
  }

  get hasActiveFilters(): boolean {
    return !!this.selectedAction || !!this.selectedUsername ||
      (this.dateRange != null && this.dateRange.length > 0 && this.dateRange[0] != null);
  }

  toggleDescription(logId: number): void {
    if (this.expandedRows.has(logId)) {
      this.expandedRows.delete(logId);
    } else {
      this.expandedRows.add(logId);
    }
  }

  countryCodeToFlag(code: string): string {
    if (!code || code.length !== 2) return '';
    return [...code.toUpperCase()]
      .map(c => String.fromCodePoint(0x1F1E6 + c.charCodeAt(0) - 65))
      .join('');
  }

  getRelativeTime(dateStr: string): string {
    const now = new Date();
    const date = new Date(dateStr);
    const diffMs = now.getTime() - date.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    const diffHour = Math.floor(diffMin / 60);
    const diffDay = Math.floor(diffHour / 24);
    const formatter = new Intl.RelativeTimeFormat(this.activeLang(), {numeric: 'auto', style: 'short'});

    if (diffMs < 60000) return this.t.translate('settingsAuditLogs.justNow');
    if (diffMin < 60) return formatter.format(-diffMin, 'minute');
    if (diffHour < 24) return formatter.format(-diffHour, 'hour');
    if (diffDay < 30) return formatter.format(-diffDay, 'day');
    return new Intl.DateTimeFormat(this.activeLang(), {dateStyle: 'medium'}).format(date);
  }

  formatAbsoluteTime(dateStr: string): string {
    return new Intl.DateTimeFormat(this.activeLang(), {dateStyle: 'medium', timeStyle: 'medium'}).format(new Date(dateStr));
  }

  formatAction(action: string): string {
    const translationKey = `settingsAuditLogs.actions.${action}`;
    const translated = this.t.translate(translationKey);
    return translated === translationKey
      ? action.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()).replace(/\B\w+/g, c => c.toLowerCase())
      : translated;
  }

  getActionColor(action: string): TagColor {
    return ACTION_COLOR_MAP.get(action) ?? 'gray';
  }

  private restoreFromQueryParams(): void {
    const params = this.route.snapshot.queryParams;
    if (params['page']) this.currentPage = +params['page'];
    if (params['size']) this.rows = +params['size'];
    if (params['action']) this.selectedAction = params['action'];
    if (params['username']) this.selectedUsername = params['username'];
    if (params['from'] || params['to']) {
      const from = params['from'] ? new Date(params['from'] + 'T00:00:00') : null;
      const to = params['to'] ? new Date(params['to'] + 'T00:00:00') : null;
      if (from) this.dateRange = [from, to!];
    }
  }

  private updateQueryParams(): void {
    const queryParams: Record<string, string | null> = {
      page: this.currentPage > 0 ? String(this.currentPage) : null,
      size: this.rows !== 25 ? String(this.rows) : null,
      action: this.selectedAction || null,
      username: this.selectedUsername || null,
      from: this.dateRange?.[0] ? this.formatDate(this.dateRange[0]) : null,
      to: this.dateRange?.[1] ? this.formatDate(this.dateRange[1]) : null,
    };
    this.router.navigate([], {queryParams, queryParamsHandling: 'merge', replaceUrl: true});
  }

  private formatDate(date: Date): string {
    return date.getFullYear() + '-' +
      String(date.getMonth() + 1).padStart(2, '0') + '-' +
      String(date.getDate()).padStart(2, '0');
  }

  private formatDateTime(date: Date, endOfDay = false): string {
    const d = new Date(date);
    if (endOfDay) {
      d.setHours(23, 59, 59);
    } else {
      d.setHours(0, 0, 0);
    }
    return d.getFullYear() + '-' +
      String(d.getMonth() + 1).padStart(2, '0') + '-' +
      String(d.getDate()).padStart(2, '0') + 'T' +
      String(d.getHours()).padStart(2, '0') + ':' +
      String(d.getMinutes()).padStart(2, '0') + ':' +
      String(d.getSeconds()).padStart(2, '0');
  }
}
