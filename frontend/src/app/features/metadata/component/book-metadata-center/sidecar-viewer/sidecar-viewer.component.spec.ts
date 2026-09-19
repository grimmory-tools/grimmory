import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MessageService} from '@openng/optimus-ui/api';
import {TranslocoService} from '@jsverse/transloco';
import {provideTanStackQuery, queryOptions, QueryClient} from '@tanstack/angular-query-experimental';
import {BehaviorSubject, of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {Book} from '../../../../book/model/book.model';
import {SidecarService} from '../../../service/sidecar.service';
import {SidecarMetadata, SidecarSyncStatus} from '../../../sources/sidecar.models';
import {metadataSourceKeys, MetadataSourceQueryService} from '../../../sources/metadata-source-query.service';
import {SidecarViewerComponent} from './sidecar-viewer.component';

describe('SidecarViewerComponent', () => {
  let status: SidecarSyncStatus;
  let statusError: Error | null;
  let contentError: Error | null;
  let content: SidecarMetadata;
  let queryClient: QueryClient;
  let fixture: ComponentFixture<SidecarViewerComponent>;
  let langChanges: BehaviorSubject<string>;
  const messageAdd = vi.fn();
  const exportToSidecar = vi.fn(() => of({message: 'ok'}));
  const importFromSidecar = vi.fn(() => of({message: 'ok'}));
  const contentQuery = vi.fn(async () => {
    if (contentError) throw contentError;
    return content;
  });
  const sidecarStatus = vi.fn((bookId: number) => queryOptions({
    queryKey: metadataSourceKeys.sidecarStatus(bookId),
    queryFn: async () => {
      if (statusError) throw statusError;
      return {status};
    },
    retry: false,
  }));
  const sidecar = vi.fn((bookId: number) => queryOptions({
    queryKey: metadataSourceKeys.sidecar(bookId),
    queryFn: contentQuery,
    retry: false,
  }));
  const translate = vi.fn((key: string) => `${langChanges.value === 'en' ? 'translated' : langChanges.value}:${key}`);

  const book = (id: number): Book => ({id, libraryId: 1, libraryName: 'Library'});

  beforeEach(() => {
    langChanges = new BehaviorSubject('en');
    status = 'IN_SYNC';
    statusError = null;
    contentError = null;
    content = {
      version: '1',
      generatedAt: '2026-03-26T00:00:00Z',
      generatedBy: 'test',
      metadata: {title: 'Example'},
      cover: {source: 'embedded', path: '/covers/example.jpg'},
    };
    messageAdd.mockReset();
    sidecarStatus.mockClear();
    sidecar.mockClear();
    contentQuery.mockClear();
    exportToSidecar.mockReset();
    exportToSidecar.mockReturnValue(of({message: 'ok'}));
    importFromSidecar.mockReset();
    importFromSidecar.mockReturnValue(of({message: 'ok'}));
    queryClient = new QueryClient();

    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(queryClient),
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: SidecarService, useValue: {exportToSidecar, importFromSidecar}},
        {provide: MetadataSourceQueryService, useValue: {sidecarStatus, sidecar}},
        {
          provide: TranslocoService,
          useValue: {
            config: {reRenderOnLangChange: false},
            langChanges$: langChanges,
            getActiveLang: () => langChanges.value,
            selectTranslate: (key: string) => of(translate(key)),
            _loadDependencies: () => of(undefined),
            translate,
          },
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  async function createComponent(bookId = 7): Promise<SidecarViewerComponent> {
    fixture = TestBed.createComponent(SidecarViewerComponent);
    fixture.componentRef.setInput('book', book(bookId));
    await fixture.whenStable();
    await queryClient.fetchQuery(sidecarStatus(bookId)).catch(() => undefined);
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve, 0));
    await fixture.whenStable();
    if (status !== 'MISSING' && status !== 'NOT_APPLICABLE' && statusError === null) {
      await queryClient.fetchQuery(sidecar(bookId)).catch(() => undefined);
      await fixture.whenStable();
      await new Promise(resolve => setTimeout(resolve, 0));
      await fixture.whenStable();
    }
    return fixture.componentInstance;
  }

  it('shows sidecar status and content and updates the label when the language changes', async () => {
    const component = await createComponent();

    expect(component.syncStatus()).toBe('IN_SYNC');
    expect(component.sidecarContent()).toEqual(content);
    expect(component.syncStatusLabel()).toBe('translated:metadata.sidecar.syncStatusInSync');

    langChanges.next('de');
    await fixture.whenStable();

    expect(component.syncStatusLabel()).toBe('de:metadata.sidecar.syncStatusInSync');
    expect(component.syncStatus()).toBe('IN_SYNC');
  });

  it('skips sidecar content for missing and not-applicable statuses', async () => {
    status = 'MISSING';
    const missing = await createComponent(3);
    expect(missing.sidecarContent()).toBeNull();
    expect(contentQuery).not.toHaveBeenCalled();

    status = 'NOT_APPLICABLE';
    const notApplicable = await createComponent(4);
    expect(notApplicable.sidecarContent()).toBeNull();
    expect(contentQuery).not.toHaveBeenCalled();
  });

  it('maps every status to its severity and label', async () => {
    const cases: [SidecarSyncStatus, ReturnType<SidecarViewerComponent['syncStatusSeverity']>, string][] = [
      ['IN_SYNC', 'success', 'translated:metadata.sidecar.syncStatusInSync'],
      ['OUTDATED', 'warn', 'translated:metadata.sidecar.syncStatusOutdated'],
      ['CONFLICT', 'danger', 'translated:metadata.sidecar.syncStatusConflict'],
      ['MISSING', 'secondary', 'translated:metadata.sidecar.syncStatusMissing'],
      ['NOT_APPLICABLE', 'info', 'translated:metadata.sidecar.syncStatusNA'],
    ];

    for (const [index, [nextStatus, severity, label]] of cases.entries()) {
      status = nextStatus;
      const component = await createComponent(index + 20);
      expect(component.syncStatusSeverity()).toBe(severity);
      expect(component.syncStatusLabel()).toBe(label);
    }
  });

  it('shows a status read failure and retries it without claiming the sidecar is inapplicable', async () => {
    statusError = new Error('Status unavailable');
    const component = await createComponent();

    expect(component.syncStatus()).toBeNull();
    expect(component.failed()).toBe(true);
    expect(contentQuery).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('metadata.sidecar.readFailed');

    statusError = null;
    component.retryRead();
    await vi.waitFor(() => expect(component.sidecarContent()).toEqual(content));
    await fixture.whenStable();
    expect(component.failed()).toBe(false);
    expect(component.syncStatus()).toBe('IN_SYNC');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows a content read failure separately from missing sidecar data', async () => {
    contentError = new Error('Content unavailable');
    const component = await createComponent();

    expect(component.syncStatus()).toBe('IN_SYNC');
    expect(component.failed()).toBe(true);
    expect(component.sidecarContent()).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('metadata.sidecar.readFailed');
  });

  it('exports with a success toast and invalidates the book sidecar key', async () => {
    const component = await createComponent(12);
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    component.exportToSidecar();

    expect(messageAdd).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    expect(invalidate).toHaveBeenCalledWith({queryKey: metadataSourceKeys.sidecar(12)});
  });

  it('reports an export error', async () => {
    exportToSidecar.mockReturnValue(throwError(() => new Error('export failed')));
    const component = await createComponent(12);

    component.exportToSidecar();

    expect(messageAdd).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
  });

  it('imports with a success toast and invalidates the book sidecar key', async () => {
    const component = await createComponent(12);
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    component.importFromSidecar();

    expect(messageAdd).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    expect(invalidate).toHaveBeenCalledWith({queryKey: metadataSourceKeys.sidecar(12)});
  });

  it('reports an import error', async () => {
    importFromSidecar.mockReturnValue(throwError(() => new Error('import failed')));
    const component = await createComponent(12);

    component.importFromSidecar();

    expect(messageAdd).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
  });
});
