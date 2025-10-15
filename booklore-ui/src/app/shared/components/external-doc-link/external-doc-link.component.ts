import {Component, Input} from '@angular/core';
import {Tooltip} from 'primeng/tooltip';

export type DocType = 'kobo' | 'opds' | 'metadataManager' | 'koReader' | 'email'
  | 'amazonCookie' | 'fetchConfig' | 'hardcover' | 'taskManagement' | 'fileNamePatterns';

@Component({
  selector: 'app-external-doc-link',
  standalone: true,
  imports: [Tooltip],
  template: `
    <i class="pi pi-external-link external-link-icon"
       [pTooltip]="tooltip"
       [tooltipPosition]="tooltipPosition"
       [style.font-size]="size"
       (click)="openLink()"
       style="cursor: pointer;">
    </i>
  `,
  styles: [`
    .external-link-icon {
      color: #0ea5e9 !important;
    }
  `]
})
export class ExternalDocLinkComponent {
  private readonly BASE_URL = 'https://booklore-app.github.io/booklore-docs/docs';

  private readonly DOC_URLS: Record<DocType, string> = {
    kobo: `${this.BASE_URL}/integration/kobo`,
    opds: `${this.BASE_URL}/integration/opds`,
    metadataManager: `${this.BASE_URL}/metadata/metadata-manager`,
    koReader: `${this.BASE_URL}/integration/koreader`,
    email: `${this.BASE_URL}/email-setup`,
    amazonCookie: `${this.BASE_URL}/metadata/amazon-cookie`,
    hardcover: `${this.BASE_URL}/metadata/hardcover-token`,
    fetchConfig: `${this.BASE_URL}/metadata/metadata-fetch-configuration`,
    taskManagement: `${this.BASE_URL}/tools/task-manager`,
    fileNamePatterns: `${this.BASE_URL}/metadata/file-naming-patterns`,
  };

  @Input() docType!: DocType;
  @Input() tooltip: string = 'View documentation';
  @Input() tooltipPosition: 'top' | 'bottom' | 'left' | 'right' = 'right';
  @Input() size: string = '1rem';

  openLink(): void {
    const url = this.DOC_URLS[this.docType];
    if (url) {
      window.open(url, '_blank');
    }
  }
}
