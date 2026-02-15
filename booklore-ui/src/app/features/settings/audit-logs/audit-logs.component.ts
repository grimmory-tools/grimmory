import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TableLazyLoadEvent, TableModule} from 'primeng/table';
import {Select} from 'primeng/select';
import {DatePicker} from 'primeng/datepicker';
import {FormsModule} from '@angular/forms';
import {TranslocoDirective} from '@jsverse/transloco';
import {AuditLog, AuditLogService} from './audit-log.service';

interface ActionOption {
  label: string;
  value: string;
}

interface UsernameOption {
  label: string;
  value: string;
}

@Component({
  selector: 'app-audit-logs',
  standalone: true,
  imports: [CommonModule, TableModule, Select, DatePicker, FormsModule, TranslocoDirective],
  templateUrl: './audit-logs.component.html',
  styleUrl: './audit-logs.component.scss'
})
export class AuditLogsComponent implements OnInit {
  private readonly auditLogService = inject(AuditLogService);

  logs: AuditLog[] = [];
  totalRecords = 0;
  rows = 25;
  loading = false;
  selectedAction: string | null = null;
  selectedUsername: string | null = null;
  dateRange: Date[] | null = null;

  usernameOptions: UsernameOption[] = [{label: 'All Users', value: ''}];

  actionOptions: ActionOption[] = [
    {label: 'All Actions', value: ''},
    {label: 'Login Success', value: 'LOGIN_SUCCESS'},
    {label: 'Login Failed', value: 'LOGIN_FAILED'},
    {label: 'User Created', value: 'USER_CREATED'},
    {label: 'User Updated', value: 'USER_UPDATED'},
    {label: 'User Deleted', value: 'USER_DELETED'},
    {label: 'Password Changed', value: 'PASSWORD_CHANGED'},
    {label: 'Library Created', value: 'LIBRARY_CREATED'},
    {label: 'Library Updated', value: 'LIBRARY_UPDATED'},
    {label: 'Library Deleted', value: 'LIBRARY_DELETED'},
    {label: 'Library Scanned', value: 'LIBRARY_SCANNED'},
    {label: 'Book Uploaded', value: 'BOOK_UPLOADED'},
    {label: 'Book Deleted', value: 'BOOK_DELETED'},
    {label: 'Permissions Changed', value: 'PERMISSIONS_CHANGED'},
    {label: 'Metadata Updated', value: 'METADATA_UPDATED'},
    {label: 'Settings Updated', value: 'SETTINGS_UPDATED'},
    {label: 'OIDC Config Changed', value: 'OIDC_CONFIG_CHANGED'},
    {label: 'Task Executed', value: 'TASK_EXECUTED'},
    {label: 'Book Sent', value: 'BOOK_SENT'},
    {label: 'Shelf Created', value: 'SHELF_CREATED'},
    {label: 'Shelf Updated', value: 'SHELF_UPDATED'},
    {label: 'Shelf Deleted', value: 'SHELF_DELETED'},
    {label: 'Magic Shelf Created', value: 'MAGIC_SHELF_CREATED'},
    {label: 'Magic Shelf Updated', value: 'MAGIC_SHELF_UPDATED'},
    {label: 'Magic Shelf Deleted', value: 'MAGIC_SHELF_DELETED'},
    {label: 'Email Provider Created', value: 'EMAIL_PROVIDER_CREATED'},
    {label: 'Email Provider Updated', value: 'EMAIL_PROVIDER_UPDATED'},
    {label: 'Email Provider Deleted', value: 'EMAIL_PROVIDER_DELETED'},
    {label: 'OPDS User Created', value: 'OPDS_USER_CREATED'},
    {label: 'OPDS User Deleted', value: 'OPDS_USER_DELETED'},
    {label: 'OPDS User Updated', value: 'OPDS_USER_UPDATED'},
    {label: 'Naming Pattern Changed', value: 'NAMING_PATTERN_CHANGED'},
  ];

  private currentPage = 0;

  ngOnInit(): void {
    this.loadUsernames();
    this.loadLogs();
  }

  loadUsernames(): void {
    this.auditLogService.getDistinctUsernames().subscribe({
      next: (usernames) => {
        this.usernameOptions = [
          {label: 'All Users', value: ''},
          ...usernames.map(u => ({label: u, value: u}))
        ];
      }
    });
  }

  loadLogs(): void {
    this.loading = true;
    const action = this.selectedAction || undefined;
    const username = this.selectedUsername || undefined;
    const from = this.dateRange?.[0] ? this.formatDateTime(this.dateRange[0]) : undefined;
    const to = this.dateRange?.[1] ? this.formatDateTime(this.dateRange[1], true) : undefined;
    this.auditLogService.getAuditLogs(this.currentPage, this.rows, action, username, from, to).subscribe({
      next: (response) => {
        this.logs = response.content;
        this.totalRecords = response.page.totalElements;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    this.currentPage = (event.first ?? 0) / (event.rows ?? this.rows);
    this.loadLogs();
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadLogs();
  }

  onDateRangeChange(): void {
    if (!this.dateRange || (this.dateRange[0] && this.dateRange[1])) {
      this.onFilterChange();
    }
  }

  formatAction(action: string): string {
    return action.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()).replace(/\B\w+/g, c => c.toLowerCase());
  }

  getActionClass(action: string): string {
    if (action.includes('FAILED') || action.includes('DELETED')) return 'action-danger';
    if (action.includes('CREATED') || action.includes('SUCCESS') || action.includes('UPLOADED')) return 'action-success';
    if (action.includes('OIDC') || action.includes('PERMISSIONS')) return 'action-warning';
    return 'action-info';
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
