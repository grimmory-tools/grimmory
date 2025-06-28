import {Component, inject, Input, OnInit} from '@angular/core';
import {Book} from '../../model/book.model';
import {UrlHelperService} from '../../../utilities/service/url-helper.service';
import {Button} from 'primeng/button';
import {BookMetadataCenterComponent} from '../../metadata/book-metadata-center/book-metadata-center.component';
import {BookService} from '../../service/book.service';
import {DialogService} from 'primeng/dynamicdialog';
import {UserService} from '../../../settings/user-management/user.service';
import {EmailService} from '../../../settings/email/email.service';
import {ConfirmationService, MessageService} from 'primeng/api';
import {Router} from '@angular/router';
import {filter} from 'rxjs';
import {NgClass} from '@angular/common';
import {BookMetadataHostService} from '../../../book-metadata-host-service';

@Component({
  selector: 'app-book-card-lite-component',
  imports: [
    Button,
    NgClass
  ],
  templateUrl: './book-card-lite-component.html',
  styleUrl: './book-card-lite-component.scss'
})
export class BookCardLiteComponent implements OnInit {
  @Input() book!: Book;

  private router = inject(Router);
  protected urlHelper = inject(UrlHelperService);
  private userService = inject(UserService);
  private bookMetadataHostService = inject(BookMetadataHostService);

  private metadataCenterViewMode: 'route' | 'dialog' = 'route';
  isHovered: boolean = false;

  ngOnInit(): void {
    this.userService.userState$
      .pipe(filter(user => !!user))
      .subscribe((user) => {
        this.metadataCenterViewMode = user?.userSettings.metadataCenterViewMode ?? 'route';
      });
  }

  openBookInfo(book: Book): void {
    if (this.metadataCenterViewMode === 'route') {
      this.router.navigate(['/book', book.id], {
        queryParams: {tab: 'view'}
      });
    } else {
      this.bookMetadataHostService.requestBookSwitch(book.id);
    }
  }
}
