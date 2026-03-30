import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {Book} from '../../../../book/model/book.model';
import {SidecarMetadata, SidecarService, SidecarSyncStatus} from '../../../service/sidecar.service';
import {SidecarViewerComponent} from './sidecar-viewer.component';

describe('SidecarViewerComponent', () => {
  const getSyncStatus = vi.fn();
  const getSidecarContent = vi.fn();
  const exportToSidecar = vi.fn();
  const importFromSidecar = vi.fn();
  const messageAdd = vi.fn();
  const translate = vi.fn((key: string) => `translated:${key}`);

  function createBook(id: number): Book {
    return {
      id,
      title: `Book ${id}`,
      libraryId: 1,
      libraryName: 'Library',
    } as Book;
  }

  function createMetadata(): SidecarMetadata {
    return {
      version: '1',
      generatedAt: '2026-03-26T00:00:00Z',
      generatedBy: 'test',
      metadata: {title: 'Example'},
      cover: {source: 'embedded', path: '/covers/example.jpg'},
    };
  }

  beforeEach(() => {
    vi.restoreAllMocks();
    getSyncStatus.mockReset();
    getSidecarContent.mockReset();
    exportToSidecar.mockReset();
    importFromSidecar.mockReset();
    messageAdd.mockReset();
    translate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        {
          provide: SidecarService,
          useValue: {getSyncStatus, getSidecarContent, exportToSidecar, importFromSidecar},
        },
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('resets its state when the selected book is cleared', () => {
    const component = TestBed.runInInjectionContext(() => new SidecarViewerComponent());
    component.currentBookId = 7;
    component.sidecarContent = createMetadata();
    component.syncStatus = 'IN_SYNC';
    component.loading = true;
    component.error = 'failed';

    component.book = null;

    expect(component.currentBookId).toBeNull();
    expect(component.sidecarContent).toBeNull();
    expect(component.syncStatus).toBe('NOT_APPLICABLE');
    expect(component.loading).toBe(false);
    expect(component.error).toBeNull();
  });

  it('loads sync status and content when a sidecar exists for the selected book', () => {
    const metadata = createMetadata();
    getSyncStatus.mockReturnValue(of({status: 'IN_SYNC'}));
    getSidecarContent.mockReturnValue(of(metadata));

    const component = TestBed.runInInjectionContext(() => new SidecarViewerComponent());
    component.book = createBook(7);

    expect(getSyncStatus).toHaveBeenCalledWith(7);
    expect(getSidecarContent).toHaveBeenCalledWith(7);
    expect(component.currentBookId).toBe(7);
    expect(component.syncStatus).toBe('IN_SYNC');
    expect(component.sidecarContent).toEqual(metadata);
    expect(component.loading).toBe(false);
  });

  it('skips content loading when the sidecar is missing or not applicable', () => {
    const component = TestBed.runInInjectionContext(() => new SidecarViewerComponent());
    getSyncStatus.mockReturnValue(of({status: 'MISSING'}));

    component.book = createBook(3);

    expect(getSidecarContent).not.toHaveBeenCalled();
    expect(component.syncStatus).toBe('MISSING');
    expect(component.sidecarContent).toBeNull();
    expect(component.loading).toBe(false);

    getSyncStatus.mockReturnValue(of({status: 'NOT_APPLICABLE'}));
    component.book = createBook(4);

    expect(getSidecarContent).not.toHaveBeenCalled();
    expect(component.syncStatus).toBe('NOT_APPLICABLE');
    expect(component.loading).toBe(false);
  });

  it('falls back to NOT_APPLICABLE when sync status lookup fails', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    getSyncStatus.mockReturnValue(throwError(() => new Error('boom')));

    const component = TestBed.runInInjectionContext(() => new SidecarViewerComponent());
    component.book = createBook(5);

    expect(component.syncStatus).toBe('NOT_APPLICABLE');
    expect(component.sidecarContent).toBeNull();
    expect(component.loading).toBe(false);
    expect(consoleError).toHaveBeenCalledOnce();
  });

  it('suppresses console noise for missing sidecar content but logs other load failures', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const component = TestBed.runInInjectionContext(() => new SidecarViewerComponent());

    getSyncStatus.mockReturnValue(of({status: 'OUTDATED'}));
    getSidecarContent.mockReturnValue(throwError(() => ({status: 404})));
    component.book = createBook(6);

    expect(component.sidecarContent).toBeNull();
    expect(component.loading).toBe(false);
    expect(consoleError).not.toHaveBeenCalled();

    getSidecarContent.mockReturnValue(throwError(() => ({status: 500})));
    component.loadSidecarData(6);

    expect(consoleError).toHaveBeenCalledOnce();
  });

  it('exports to sidecar, shows a success toast, and reloads the current book data', () => {
    exportToSidecar.mockReturnValue(of({message: 'ok'}));

    const component = TestBed.runInInjectionContext(() => new SidecarViewerComponent());
    const reload = vi.spyOn(component, 'loadSidecarData').mockImplementation(() => undefined);
    component.currentBookId = 9;

    component.exportToSidecar();

    expect(exportToSidecar).toHaveBeenCalledWith(9);
    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'translated:metadata.sidecar.toast.exportSuccessSummary',
      detail: 'translated:metadata.sidecar.toast.exportSuccessDetail',
    });
    expect(reload).toHaveBeenCalledWith(9);
    expect(component.exporting).toBe(false);
  });

  it('shows an error toast when export fails and ignores export without a selected book', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    exportToSidecar.mockReturnValue(throwError(() => new Error('export failed')));

    const component = TestBed.runInInjectionContext(() => new SidecarViewerComponent());
    component.exportToSidecar();

    expect(exportToSidecar).not.toHaveBeenCalled();

    component.currentBookId = 11;
    component.exportToSidecar();

    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'translated:metadata.sidecar.toast.exportFailedSummary',
      detail: 'translated:metadata.sidecar.toast.exportFailedDetail',
    });
    expect(component.exporting).toBe(false);
    expect(consoleError).toHaveBeenCalledOnce();
  });

  it('imports from sidecar, shows success or error toasts, and reloads when needed', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    importFromSidecar.mockReturnValueOnce(of({message: 'ok'})).mockReturnValueOnce(throwError(() => new Error('import failed')));

    const component = TestBed.runInInjectionContext(() => new SidecarViewerComponent());
    const reload = vi.spyOn(component, 'loadSidecarData').mockImplementation(() => undefined);

    component.currentBookId = 12;
    component.importFromSidecar();

    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'translated:metadata.sidecar.toast.importSuccessSummary',
      detail: 'translated:metadata.sidecar.toast.importSuccessDetail',
    });
    expect(reload).toHaveBeenCalledWith(12);
    expect(component.importing).toBe(false);

    component.importFromSidecar();

    expect(messageAdd).toHaveBeenLastCalledWith({
      severity: 'error',
      summary: 'translated:metadata.sidecar.toast.importFailedSummary',
      detail: 'translated:metadata.sidecar.toast.importFailedDetail',
    });
    expect(component.importing).toBe(false);
    expect(consoleError).toHaveBeenCalledOnce();
  });

  it('maps sync status severities and labels across all branches', () => {
    const component = TestBed.runInInjectionContext(() => new SidecarViewerComponent());
    const cases: [SidecarSyncStatus | 'UNKNOWN', string, string][] = [
      ['IN_SYNC', 'success', 'translated:metadata.sidecar.syncStatusInSync'],
      ['OUTDATED', 'warn', 'translated:metadata.sidecar.syncStatusOutdated'],
      ['CONFLICT', 'danger', 'translated:metadata.sidecar.syncStatusConflict'],
      ['MISSING', 'secondary', 'translated:metadata.sidecar.syncStatusMissing'],
      ['NOT_APPLICABLE', 'info', 'translated:metadata.sidecar.syncStatusNA'],
      ['UNKNOWN', 'info', 'translated:metadata.sidecar.syncStatusUnknown'],
    ];

    for (const [status, severity, label] of cases) {
      component.syncStatus = status as SidecarSyncStatus;
      expect(component.getSyncStatusSeverity()).toBe(severity);
      expect(component.getSyncStatusLabel()).toBe(label);
    }
  });
});
