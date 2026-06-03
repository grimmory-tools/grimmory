import {provideZonelessChangeDetection, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MessageService} from 'primeng/api';
import {Subject} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookService} from '../../../book/service/book.service';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {ContentRestriction, ContentRestrictionMode, ContentRestrictionType} from '../content-restriction.model';
import {ContentRestrictionService} from '../content-restriction.service';
import {ContentRestrictionsEditorComponent} from './content-restrictions-editor.component';

describe('ContentRestrictionsEditorComponent', () => {
  let fixture: ComponentFixture<ContentRestrictionsEditorComponent>;
  let restrictionResponses: Map<number, Subject<ContentRestriction[]>>;
  let addResponses: Map<number, Subject<ContentRestriction>>;
  let deleteResponses: Map<number, Subject<void>>;
  const getUserRestrictions = vi.fn();
  const addRestriction = vi.fn();
  const deleteRestriction = vi.fn();
  const messageAdd = vi.fn();

  beforeEach(async () => {
    restrictionResponses = new Map<number, Subject<ContentRestriction[]>>();
    addResponses = new Map<number, Subject<ContentRestriction>>();
    deleteResponses = new Map<number, Subject<void>>();
    getUserRestrictions
      .mockReset()
      .mockImplementation((userId: number) => restrictionResponseFor(userId).asObservable());
    addRestriction
      .mockReset()
      .mockImplementation((userId: number) => addResponseFor(userId).asObservable());
    deleteRestriction
      .mockReset()
      .mockImplementation((userId: number) => deleteResponseFor(userId).asObservable());
    messageAdd.mockReset();

    await TestBed.configureTestingModule({
      imports: [ContentRestrictionsEditorComponent, getTranslocoModule()],
      providers: [
        provideZonelessChangeDetection(),
        {
          provide: ContentRestrictionService,
          useValue: {
            getUserRestrictions,
            addRestriction,
            deleteRestriction,
          },
        },
        {
          provide: BookService,
          useValue: {
            uniqueMetadata: signal({
              categories: [],
              tags: ['Loaded tag'],
              moods: [],
              authors: [],
              publishers: [],
              series: [],
            }),
          },
        },
        {
          provide: MessageService,
          useValue: {add: messageAdd},
        },
      ],
      })
      .compileComponents();

    fixture = TestBed.createComponent(ContentRestrictionsEditorComponent);
    fixture.componentRef.setInput('userId', 42);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    for (const response of restrictionResponses.values()) {
      response.complete();
    }
    for (const response of addResponses.values()) {
      response.complete();
    }
    for (const response of deleteResponses.values()) {
      response.complete();
    }
    TestBed.resetTestingModule();
  });

  it('renders restrictions loaded from the service', async () => {
    expect(fixture.nativeElement.querySelector('.empty-state')).not.toBeNull();

    restrictionResponseFor(42).next([createRestriction('Loaded tag')]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getUserRestrictions).toHaveBeenCalledWith(42);
    expect(fixture.nativeElement.querySelector('.chip-value').textContent.trim()).toBe('Loaded tag');
  });

  it('ignores stale restriction responses after the user changes', async () => {
    fixture.componentRef.setInput('userId', 43);
    fixture.detectChanges();

    restrictionResponseFor(42).next([createRestriction('Old tag')]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.chip-value')).toBeNull();

    restrictionResponseFor(43).next([createRestriction('Current tag', 43)]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getUserRestrictions).toHaveBeenCalledWith(42);
    expect(getUserRestrictions).toHaveBeenCalledWith(43);
    expect(fixture.nativeElement.querySelector('.chip-value').textContent.trim()).toBe('Current tag');
  });

  it('clears displayed restrictions when the active load fails', async () => {
    const emitted: ContentRestriction[][] = [];
    fixture.componentInstance.restrictionsChanged.subscribe(restrictions => emitted.push(restrictions));

    restrictionResponseFor(42).next([createRestriction('Old tag')]);
    await fixture.whenStable();
    fixture.detectChanges();
    emitted.length = 0;
    messageAdd.mockClear();

    fixture.componentRef.setInput('userId', 43);
    fixture.detectChanges();

    restrictionResponseFor(43).error(new Error('load failed'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.chip-value')).toBeNull();
    expect(emitted).toEqual([[]]);
    expect(messageAdd).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
    }));
  });

  it('ignores stale add responses after the user changes', async () => {
    const emitted: ContentRestriction[][] = [];
    fixture.componentInstance.restrictionsChanged.subscribe(restrictions => emitted.push(restrictions));
    fixture.componentInstance.newRestriction = {
      restrictionType: ContentRestrictionType.TAG,
      mode: ContentRestrictionMode.EXCLUDE,
      value: 'Loaded tag',
    };

    fixture.componentInstance.addRestriction();
    fixture.componentRef.setInput('userId', 43);
    fixture.detectChanges();

    addResponseFor(42).next(createRestriction('Loaded tag'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(addRestriction).toHaveBeenCalledWith(42, expect.objectContaining({
      userId: 42,
      value: 'Loaded tag',
    }));
    expect(fixture.nativeElement.querySelector('.chip-value')).toBeNull();
    expect(emitted).toEqual([]);
    expect(messageAdd).not.toHaveBeenCalled();
  });

  it('ignores stale delete responses after the user changes', async () => {
    const emitted: ContentRestriction[][] = [];
    fixture.componentInstance.restrictionsChanged.subscribe(restrictions => emitted.push(restrictions));

    restrictionResponseFor(42).next([createRestriction('Old tag')]);
    await fixture.whenStable();
    fixture.detectChanges();
    emitted.length = 0;
    messageAdd.mockClear();

    fixture.componentInstance.removeRestriction(createRestriction('Old tag'));
    fixture.componentRef.setInput('userId', 43);
    fixture.detectChanges();

    deleteResponseFor(42).next();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(deleteRestriction).toHaveBeenCalledWith(42, 1);
    expect(fixture.nativeElement.querySelector('.chip-value').textContent.trim()).toBe('Old tag');
    expect(emitted).toEqual([]);
    expect(messageAdd).not.toHaveBeenCalled();

    restrictionResponseFor(43).next([createRestriction('Current tag', 43)]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.chip-value').textContent.trim()).toBe('Current tag');
  });

  function restrictionResponseFor(userId: number): Subject<ContentRestriction[]> {
    let response = restrictionResponses.get(userId);
    if (!response) {
      response = new Subject<ContentRestriction[]>();
      restrictionResponses.set(userId, response);
    }
    return response;
  }

  function addResponseFor(userId: number): Subject<ContentRestriction> {
    let response = addResponses.get(userId);
    if (!response) {
      response = new Subject<ContentRestriction>();
      addResponses.set(userId, response);
    }
    return response;
  }

  function deleteResponseFor(userId: number): Subject<void> {
    let response = deleteResponses.get(userId);
    if (!response) {
      response = new Subject<void>();
      deleteResponses.set(userId, response);
    }
    return response;
  }
});

function createRestriction(value: string, userId = 42): ContentRestriction {
  return {
    id: 1,
    userId,
    restrictionType: ContentRestrictionType.TAG,
    mode: ContentRestrictionMode.EXCLUDE,
    value,
  };
}
