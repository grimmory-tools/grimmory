import {Injectable} from '@angular/core';
import {ReadStatus} from '../model/book.model';

@Injectable({
  providedIn: 'root'
})
export class ReadStatusHelper {

  getReadStatusIcon(readStatus: ReadStatus | undefined): string {
    if (!readStatus) return 'pi pi-book';
    switch (readStatus) {
      case ReadStatus.READ:
        return 'pi pi-check';
      case ReadStatus.READING:
        return 'pi pi-play';
      case ReadStatus.RE_READING:
        return 'pi pi-refresh';
      case ReadStatus.PARTIALLY_READ:
        return 'pi pi-clock';
      case ReadStatus.PAUSED:
        return 'pi pi-pause';
      case ReadStatus.ABANDONED:
        return 'pi pi-times';
      case ReadStatus.WONT_READ:
        return 'pi pi-ban';
      case ReadStatus.UNREAD:
      case ReadStatus.UNSET:
      default:
        return 'pi pi-book';
    }
  }

  getReadStatusClass(readStatus: ReadStatus | undefined): string {
    if (!readStatus) return 'status-unset';
    switch (readStatus) {
      case ReadStatus.READ:
        return 'status-read';
      case ReadStatus.READING:
        return 'status-reading';
      case ReadStatus.RE_READING:
        return 'status-re-reading';
      case ReadStatus.PARTIALLY_READ:
        return 'status-partially-read';
      case ReadStatus.PAUSED:
        return 'status-paused';
      case ReadStatus.ABANDONED:
        return 'status-abandoned';
      case ReadStatus.WONT_READ:
        return 'status-wont-read';
      case ReadStatus.UNREAD:
      case ReadStatus.UNSET:
      default:
        return 'status-unset';
    }
  }

  getReadStatusTooltip(readStatus: ReadStatus | undefined): string {
    if (!readStatus) return 'Unset';
    switch (readStatus) {
      case ReadStatus.READ:
        return 'Read';
      case ReadStatus.READING:
        return 'Currently Reading';
      case ReadStatus.RE_READING:
        return 'Re-reading';
      case ReadStatus.PARTIALLY_READ:
        return 'Partially Read';
      case ReadStatus.PAUSED:
        return 'Paused';
      case ReadStatus.ABANDONED:
        return 'Abandoned';
      case ReadStatus.WONT_READ:
        return 'Won\'t Read';
      case ReadStatus.UNREAD:
        return 'Unread';
      case ReadStatus.UNSET:
      default:
        return 'Unset';
    }
  }

  shouldShowStatusIcon(_readStatus: ReadStatus | undefined): boolean {
    return true;
  }
}

