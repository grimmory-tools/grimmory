import {Component, inject, Input, OnInit, ViewChild} from '@angular/core';
import {Book, BookMetadata} from '../../../model/book.model';
import {Button} from 'primeng/button';
import {MenuModule} from 'primeng/menu';
import {MenuItem, MessageService} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {ShelfAssignerComponent} from '../../shelf-assigner/shelf-assigner.component';
import {BookService} from '../../../service/book.service';
import {CheckboxModule} from 'primeng/checkbox';
import {FormsModule} from '@angular/forms';
import {MetadataFetchOptionsComponent} from '../../../../metadata/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {MetadataRefreshType} from '../../../../metadata/model/request/metadata-refresh-type.enum';
import {MetadataRefreshRequest} from '../../../../metadata/model/request/metadata-refresh-request.model';
import {UrlHelperService} from '../../../../utilities/service/url-helper.service';
import {NgClass, NgIf} from '@angular/common';
import {UserService} from '../../../../settings/user-management/user.service';
import {filter} from 'rxjs';
import {EmailService} from '../../../../settings/email/email.service';
import {TieredMenu} from 'primeng/tieredmenu';
import {BookSenderComponent} from '../../book-sender/book-sender.component';
import {Router} from '@angular/router';

@Component({
  selector: 'app-book-card',
  templateUrl: './book-card.component.html',
  styleUrls: ['./book-card.component.scss'],
  imports: [Button, MenuModule, CheckboxModule, FormsModule, NgIf, NgClass, TieredMenu],
  standalone: true
})
export class BookCardComponent implements OnInit {
  @Input() book!: Book;
  @Input() isCheckboxEnabled: boolean = false;
  @Input() onBookSelect?: (bookId: number, selected: boolean) => void;
  @Input() isSelected: boolean = false;
  @Input() bottomBarHidden: boolean = false;
  @Input() readButtonHidden: boolean = false;

  items: MenuItem[] | undefined;
  isHovered: boolean = false;
  isImageLoaded: boolean = false;

  private bookService = inject(BookService);
  private dialogService = inject(DialogService);
  private userService = inject(UserService);
  private emailService = inject(EmailService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  protected urlHelper = inject(UrlHelperService);

  private userPermissions: any;


  ngOnInit(): void {
    this.userService.userData$
      .pipe(filter(userData => !!userData))
      .subscribe((userData) => {
        this.userPermissions = userData.permissions;
        this.initMenu();
      });
  }

  onImageLoad(): void {
    this.isImageLoaded = true;
  }

  readBook(book: Book): void {
    this.bookService.readBook(book.id);
  }

  toggleSelection(selected: boolean): void {
    if (this.isCheckboxEnabled) {
      this.isSelected = selected;
      if (this.onBookSelect) {
        this.onBookSelect(this.book.id, selected);
      }
    }
  }

  private initMenu() {
    this.items = [
      {
        label: 'Assign Shelf',
        icon: 'pi pi-folder',
        command: () => this.openShelfDialog()
      },
      {
        label: 'View Details',
        icon: 'pi pi-info-circle',
        command: () => {
          setTimeout(() => {
            this.router.navigate(['/book', this.book.id], {
              queryParams: {tab: 'view'}
            })
          }, 150);
        },
      },
      ...this.getPermissionBasedMenuItems(),
    ];
  }

  private getPermissionBasedMenuItems(): MenuItem[] {
    const items: MenuItem[] = [];

    if (this.hasEditMetadataPermission()) {
      items.push(
        {
          label: 'Match Book',
          icon: 'pi pi-sparkles',
          command: () => {
            setTimeout(() => {
              this.router.navigate(['/book', this.book.id], {
                queryParams: {tab: 'match'}
              })
            }, 150);
          },
        },
        {
          label: 'Quick Refresh',
          icon: 'pi pi-bolt',
          command: () => {
            const metadataRefreshRequest: MetadataRefreshRequest = {
              quick: true,
              refreshType: MetadataRefreshType.BOOKS,
              bookIds: [this.book.id],
            };
            this.bookService.autoRefreshMetadata(metadataRefreshRequest).subscribe();
          },
        },
        {
          label: 'Granular Refresh',
          icon: 'pi pi-database',
          command: () => {
            this.dialogService.open(MetadataFetchOptionsComponent, {
              header: 'Metadata Refresh Options',
              modal: true,
              closable: true,
              data: {
                bookIds: [this.book!.id],
                metadataRefreshType: MetadataRefreshType.BOOKS,
              },
            });
          },
        }
      );
    }

    if (this.hasDownloadPermission()) {
      items.push({
        label: 'Download',
        icon: 'pi pi-download',
        command: () => {
          this.bookService.downloadFile(this.book.id);
        },
      });
    }

    if (this.hasEmailBookPermission()) {
      items.push(
        {
          label: 'Send Book',
          icon: 'pi pi-envelope',
          items: [{
            label: 'Quick Send',
            icon: 'pi pi-envelope',
            command: () => {
              this.emailService.emailBookQuick(this.book.id).subscribe({
                next: () => {
                  this.messageService.add({
                    severity: 'info',
                    summary: 'Success',
                    detail: 'The book sending has been scheduled.',
                  });
                },
                error: (err) => {
                  const errorMessage = err?.error?.message || 'An error occurred while sending the book.';
                  this.messageService.add({
                    severity: 'error',
                    summary: 'Error',
                    detail: errorMessage,
                  });
                },
              });
            }
          },
            {
              label: 'Custom Send',
              icon: 'pi pi-envelope',
              command: () => {
                this.dialogService.open(BookSenderComponent, {
                  header: 'Send Book to Email',
                  modal: true,
                  closable: true,
                  style: {
                    position: 'absolute',
                    top: '15%',
                  },
                  data: {
                    bookId: this.book.id,
                  }
                });
              }
            }
          ]
        });
    }

    return items;
  }

  private openShelfDialog(): void {
    this.dialogService.open(ShelfAssignerComponent, {
      header: `Update Book's Shelves`,
      modal: true,
      closable: true,
      contentStyle: {overflow: 'auto'},
      baseZIndex: 10,
      style: {
        position: 'absolute',
        top: '15%',
      },
      data: {
        book: this.book,
      },
    });
  }

  openBookInfo(book: Book): void {
    this.router.navigate(['/book', book.id], {
      queryParams: {tab: 'view'}
    });
  }

  private hasEditMetadataPermission(): boolean {
    return this.userPermissions?.canEditMetadata ?? false;
  }

  private hasDownloadPermission(): boolean {
    return this.userPermissions?.canDownload ?? false;
  }

  private hasEmailBookPermission(): boolean {
    return this.userPermissions?.canEmailBook ?? false;
  }

  isMetadataFullyLocked(metadata: BookMetadata): boolean {
    return Object.keys(metadata)
      .filter(key => key.endsWith('Locked'))
      .every(key => metadata[key] === true);
  }
}
