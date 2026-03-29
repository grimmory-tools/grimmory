import {ChangeDetectorRef, SimpleChange} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {Subject, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import type {AuthorDetails, AuthorSummary} from '../../model/author.model';
import {AuthorService} from '../../service/author.service';
import {AuthorCardComponent} from './author-card.component';

describe('AuthorCardComponent', () => {
  let getAuthorThumbnailUrl: ReturnType<typeof vi.fn>;
  let quickMatchAuthor: ReturnType<typeof vi.fn>;
  let messageService: Pick<MessageService, 'add'>;
  let translate: ReturnType<typeof vi.fn>;
  let markForCheck: ReturnType<typeof vi.fn>;

  const baseAuthor: AuthorSummary = {
    id: 9,
    name: 'Ada Lovelace',
    asin: 'B00ADA',
    bookCount: 12,
    hasPhoto: true,
  };

  const createComponent = (overrides?: {
    author?: Partial<AuthorSummary>;
    canQuickMatch?: boolean;
    canDelete?: boolean;
    isSelected?: boolean;
    index?: number;
    cacheBuster?: number;
  }) => {
    const component = TestBed.runInInjectionContext(() => new AuthorCardComponent());
    component.author = {
      ...baseAuthor,
      ...overrides?.author,
    };
    component.canQuickMatch = overrides?.canQuickMatch ?? false;
    component.canDelete = overrides?.canDelete ?? false;
    component.isSelected = overrides?.isSelected ?? false;
    component.index = overrides?.index ?? 0;
    component.cacheBuster = overrides?.cacheBuster ?? 0;
    return component;
  };

  beforeEach(() => {
    getAuthorThumbnailUrl = vi.fn((authorId: number, cacheBuster?: number) => `/authors/${authorId}/thumbnail?t=${cacheBuster ?? 'none'}`);
    quickMatchAuthor = vi.fn();
    messageService = {
      add: vi.fn(),
    };
    translate = vi.fn((key: string) => key);
    markForCheck = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthorService,
          useValue: {
            getAuthorThumbnailUrl,
            quickMatchAuthor,
          },
        },
        {
          provide: MessageService,
          useValue: messageService,
        },
        {
          provide: TranslocoService,
          useValue: {
            translate,
          },
        },
        {
          provide: ChangeDetectorRef,
          useValue: {
            markForCheck,
          },
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('resets photo state only for meaningful author changes and later cache busters', () => {
    const component = createComponent();

    component.hasPhoto = false;
    component.ngOnChanges({
      author: new SimpleChange(baseAuthor, {...baseAuthor, name: 'Augusta Ada'}, false),
    });

    expect(component.hasPhoto).toBe(false);

    component.ngOnChanges({
      author: new SimpleChange(baseAuthor, {...baseAuthor, asin: 'B00NEW', hasPhoto: false}, false),
    });

    expect(component.hasPhoto).toBe(false);

    component.hasPhoto = false;
    component.ngOnChanges({
      cacheBuster: new SimpleChange(0, 1, false),
    });

    expect(component.hasPhoto).toBe(true);
  });

  it('short-circuits clicks that start inside the menu button container', () => {
    const component = createComponent();
    const emitSpy = vi.spyOn(component.cardClick, 'emit');

    const menuContainer = document.createElement('div');
    menuContainer.className = 'menu-button-container';
    const button = document.createElement('button');
    menuContainer.appendChild(button);

    const event = new Event('click');
    const stopPropagationSpy = vi.spyOn(event, 'stopPropagation');
    Object.defineProperty(event, 'target', {
      configurable: true,
      value: button,
    });

    component.onCardClick(event);

    expect(stopPropagationSpy).not.toHaveBeenCalled();
    expect(emitSpy).not.toHaveBeenCalled();
  });

  it('treats modifier clicks as selection toggles instead of opening the author', () => {
    const component = createComponent({isSelected: false, index: 3});
    const checkboxEmitSpy = vi.spyOn(component.checkboxClick, 'emit');
    const cardEmitSpy = vi.spyOn(component.cardClick, 'emit');

    const target = document.createElement('div');
    const event = new MouseEvent('click', {ctrlKey: true});
    const preventDefaultSpy = vi.spyOn(event, 'preventDefault');
    const stopPropagationSpy = vi.spyOn(event, 'stopPropagation');
    Object.defineProperty(event, 'target', {
      configurable: true,
      value: target,
    });

    component.onCardClick(event);

    expect(preventDefaultSpy).toHaveBeenCalledOnce();
    expect(stopPropagationSpy).toHaveBeenCalledOnce();
    expect(cardEmitSpy).not.toHaveBeenCalled();
    expect(checkboxEmitSpy).toHaveBeenCalledWith({
      index: 3,
      author: component.author,
      selected: true,
      shiftKey: false,
    });
  });

  it('emits cardClick for normal clicks outside the menu controls', () => {
    const component = createComponent();
    const emitSpy = vi.spyOn(component.cardClick, 'emit');

    const event = new Event('click');
    const stopPropagationSpy = vi.spyOn(event, 'stopPropagation');
    Object.defineProperty(event, 'target', {
      configurable: true,
      value: document.createElement('div'),
    });

    component.onCardClick(event);

    expect(stopPropagationSpy).toHaveBeenCalledOnce();
    expect(emitSpy).toHaveBeenCalledWith(component.author);
  });

  it('captures shift state from checkbox clicks and reuses it for selection events', () => {
    const component = createComponent({index: 5});
    const emitSpy = vi.spyOn(component.checkboxClick, 'emit');

    const mouseEvent = new MouseEvent('click', {shiftKey: true});
    const stopPropagationSpy = vi.spyOn(mouseEvent, 'stopPropagation');

    component.captureMouseEvent(mouseEvent);
    component.toggleSelection({checked: true} as never);

    expect(stopPropagationSpy).toHaveBeenCalledOnce();
    expect(emitSpy).toHaveBeenCalledWith({
      index: 5,
      author: component.author,
      selected: true,
      shiftKey: true,
    });
  });

  it('lazily initializes the menu once, marks for check, and toggles the Prime menu each time', () => {
    const component = createComponent({canQuickMatch: true, canDelete: true});
    const menu = {
      toggle: vi.fn(),
    };
    const event = new Event('click');

    component.onMenuToggle(event, menu as never);
    component.onMenuToggle(event, menu as never);

    expect(markForCheck).toHaveBeenCalledOnce();
    expect(menu.toggle).toHaveBeenCalledTimes(2);
    expect(component.items.map(item => item.label)).toEqual([
      'authorBrowser.card.menu.viewAuthor',
      'authorBrowser.card.menu.editAuthor',
      'authorBrowser.card.menu.quickMatch',
      'authorBrowser.card.menu.deleteAuthor',
    ]);
  });

  it('quick-matches through the lazy menu command and reports success state', () => {
    const quickMatch$ = new Subject<AuthorDetails>();
    quickMatchAuthor.mockReturnValue(quickMatch$);

    const component = createComponent({canQuickMatch: true});
    const emitSpy = vi.spyOn(component.quickMatched, 'emit');

    component.onMenuToggle(new Event('click'), {toggle: vi.fn()} as never);
    const quickMatchItem = component.items.find(item => item.label === 'authorBrowser.card.menu.quickMatch');

    quickMatchItem?.command?.({originalEvent: new Event('click'), item: quickMatchItem});

    expect(quickMatchAuthor).toHaveBeenCalledWith(9);
    expect(component.quickMatching).toBe(true);

    quickMatch$.next({
      id: 9,
      name: 'Ada Lovelace',
      asin: 'B00MATCH',
      nameLocked: false,
      descriptionLocked: false,
      asinLocked: true,
      photoLocked: false,
    });
    quickMatch$.complete();

    expect(component.quickMatching).toBe(false);
    expect(component.author).toEqual({
      ...baseAuthor,
      asin: 'B00MATCH',
      hasPhoto: true,
    });
    expect(component.hasPhoto).toBe(true);
    expect(emitSpy).toHaveBeenCalledWith(component.author);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'authorBrowser.toast.quickMatchSuccessSummary',
      detail: 'authorBrowser.toast.quickMatchSuccessDetail',
    });

    component.onMenuToggle(new Event('click'), {toggle: vi.fn()} as never);
    expect(markForCheck).toHaveBeenCalledTimes(2);
  });

  it('clears quickMatching and reports an error toast when quick match fails', () => {
    quickMatchAuthor.mockReturnValue(throwError(() => new Error('boom')));

    const component = createComponent({canQuickMatch: true});
    const emitSpy = vi.spyOn(component.quickMatched, 'emit');

    component.onMenuToggle(new Event('click'), {toggle: vi.fn()} as never);
    const quickMatchItem = component.items.find(item => item.label === 'authorBrowser.card.menu.quickMatch');

    quickMatchItem?.command?.({originalEvent: new Event('click'), item: quickMatchItem});

    expect(component.quickMatching).toBe(false);
    expect(emitSpy).not.toHaveBeenCalled();
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'authorBrowser.toast.quickMatchFailedSummary',
      detail: 'authorBrowser.toast.quickMatchFailedDetail',
    });
  });
});
