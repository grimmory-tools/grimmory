import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { getTranslocoModule } from '../../../../../core/testing/transloco-testing';
import { UrlHelperService } from '../../../../../shared/service/url-helper.service';
import { BookMetadataManageService } from '../../../service/book-metadata-manage.service';
import { BookService } from '../../../service/book.service';
import { ReadStatusHelper } from '../../../helpers/read-status.helper';
import { UserService } from '../../../../settings/user-management/user.service';

import { BookTableComponent } from './book-table.component';

describe('BookTableComponent', () => {
  let fixture: ComponentFixture<BookTableComponent>;
  let component: BookTableComponent;
  let innerWidthSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    innerWidthSpy = vi.spyOn(window, 'innerWidth', 'get');

    TestBed.configureTestingModule({
      imports: [BookTableComponent, getTranslocoModule()],
      providers: [
        { provide: UrlHelperService, useValue: { getBookUrl: vi.fn(), filterBooksBy: vi.fn() } },
        { provide: BookService, useValue: { getBooksByIds: vi.fn(() => []) } },
        { provide: BookMetadataManageService, useValue: { toggleAllLock: vi.fn() } },
        { provide: MessageService, useValue: { add: vi.fn() } },
        { provide: UserService, useValue: { currentUser: vi.fn(() => null) } },
        {
          provide: ReadStatusHelper,
          useValue: {
            getReadStatusIcon: vi.fn(() => 'pi pi-book'),
            getReadStatusClass: vi.fn(() => 'status-unset'),
            getReadStatusTooltip: vi.fn(() => 'Unread'),
            shouldShowStatusIcon: vi.fn(() => true),
          },
        },
      ],
    });

    TestBed.overrideComponent(BookTableComponent, { set: { template: '' } });

    fixture = TestBed.createComponent(BookTableComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('uses the mobile scroll height for both component state and virtual scrollers', () => {
    innerWidthSpy.mockReturnValue(768);
    const wrapper = document.createElement('div');
    wrapper.className = 'p-virtualscroller';
    fixture.nativeElement.appendChild(wrapper);

    fixture.detectChanges();
    component.ngOnChanges({});

    expect(component.scrollHeight).toBe('calc(var(--page-available-height) - 63px)');
    expect(wrapper.style.height).toBe('calc(var(--page-available-height) - 63px)');
  });

  it('uses the desktop scroll height for both component state and virtual scrollers', () => {
    innerWidthSpy.mockReturnValue(1024);
    const wrapper = document.createElement('div');
    wrapper.className = 'p-virtualscroller';
    fixture.nativeElement.appendChild(wrapper);

    fixture.detectChanges();
    component.ngOnChanges({});

    expect(component.scrollHeight).toBe('calc(var(--page-available-height) - 65px)');
    expect(wrapper.style.height).toBe('calc(var(--page-available-height) - 65px)');
  });
});
