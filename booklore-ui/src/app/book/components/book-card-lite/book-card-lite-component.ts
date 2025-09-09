import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {Book} from '../../model/book.model';
import {UrlHelperService} from '../../../utilities/service/url-helper.service';
import {Button} from 'primeng/button';
import {UserService} from '../../../settings/user-management/user.service';
import {Router} from '@angular/router';
import {filter, Subject} from 'rxjs';
import {NgClass} from '@angular/common';
import {BookMetadataHostService} from '../../../utilities/service/book-metadata-host-service';
import {takeUntil} from 'rxjs/operators';
import {TooltipModule} from 'primeng/tooltip';

@Component({
  selector: 'app-book-card-lite-component',
  imports: [
    Button,
    NgClass,
    TooltipModule
  ],
  templateUrl: './book-card-lite-component.html',
  styleUrl: './book-card-lite-component.scss'
})
export class BookCardLiteComponent implements OnInit, OnDestroy {
  @Input() book!: Book;
  @Input() isActive: boolean = false;

  private router = inject(Router);
  protected urlHelper = inject(UrlHelperService);
  private userService = inject(UserService);
  private bookMetadataHostService = inject(BookMetadataHostService);

  private destroy$ = new Subject<void>();

  private metadataCenterViewMode: 'route' | 'dialog' = 'route';
  isHovered: boolean = false;

  ngOnInit(): void {
    this.userService.userState$
      .pipe(
        filter(userState => !!userState),
        takeUntil(this.destroy$)
      )
      .subscribe((user) => {
        this.metadataCenterViewMode = user?.user?.userSettings.metadataCenterViewMode ?? 'route';
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

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
