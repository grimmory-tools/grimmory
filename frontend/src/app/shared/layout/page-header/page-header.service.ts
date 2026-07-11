import {computed, Injectable, type Signal, signal} from '@angular/core';
import {type TabItem} from '../../ui/tabs/app-tabs.component';

export interface PageHeaderBreadcrumb {
  label: string;
  commands?: readonly unknown[];
}

export interface PageHeader {
  title?: string;
  count?: string;
  breadcrumbs?: readonly PageHeaderBreadcrumb[];
  tabs?: readonly TabItem[];
  selectedTabId?: string;
}

export interface PageHeaderTopbarBackTarget {
  label: string;
  commands: readonly unknown[];
}

export interface PageHeaderMobileTopbar {
  title?: string;
  backTarget?: PageHeaderTopbarBackTarget;
}

export type PageHeaderHandle = Signal<PageHeader | null>;

const EMPTY: PageHeaderHandle = signal(null);

@Injectable({providedIn: 'root'})
export class PageHeaderService {
  private readonly source = signal<PageHeaderHandle>(EMPTY);
  private readonly leadVisibleSource = signal(true);

  readonly header = computed(() => this.source()());
  readonly leadVisible = this.leadVisibleSource.asReadonly();
  readonly mobileTopbar = computed<PageHeaderMobileTopbar>(() => {
    const header = this.header();
    if (!header) return {};

    const parent = header.breadcrumbs?.at(-2);
    const title = header.breadcrumbs?.at(-1)?.label ?? header.title;
    const backTarget = parent?.commands?.length ? {label: parent.label, commands: parent.commands} : undefined;

    return {title, backTarget};
  });

  registerRenderedHeader(header: PageHeaderHandle): PageHeaderHandle {
    this.source.set(header);
    this.leadVisibleSource.set(true);
    return header;
  }

  setLeadVisible(visible: boolean): void {
    this.leadVisibleSource.set(visible);
  }

  release(handle: PageHeaderHandle): void {
    if (this.source() === handle) {
      this.source.set(EMPTY);
      this.leadVisibleSource.set(true);
    }
  }
}
