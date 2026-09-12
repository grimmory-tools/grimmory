import {DestroyRef, inject} from '@angular/core';

export interface BrowseSelectionShortcutsDeps {
  enabled: () => boolean;
  active: () => boolean;
  suspended?: () => boolean;
  clear: () => void;
  selectAll: () => void;
  onEscapeWhileInactive?: () => void;
  clickAwayExempt?: string;
}

export function installBrowseSelectionShortcuts(deps: BrowseSelectionShortcutsDeps): void {
  const destroyRef = inject(DestroyRef);

  const exempt = deps.clickAwayExempt;
  if (exempt !== undefined) {
    const onClick = (event: MouseEvent): void => {
      const target = event.target;
      if (deps.active() && target instanceof Element && target.closest(exempt) === null) {
        deps.clear();
      }
    };
    document.addEventListener('click', onClick);
    destroyRef.onDestroy(() => document.removeEventListener('click', onClick));
  }

  const onKeydown = (event: KeyboardEvent): void => {
    if (event.key === 'Escape'
      && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey) {
      if (deps.suspended?.()) {
        return;
      }
      if (deps.active()) {
        deps.clear();
        return;
      }
      deps.onEscapeWhileInactive?.();
      return;
    }

    if (event.key.toLowerCase() === 'a'
      && event.ctrlKey !== event.metaKey && !event.shiftKey && !event.altKey) {
      const target = event.target;
      const typing = target instanceof HTMLElement && (
        target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable
      );
      if (typing || !deps.enabled()) {
        return;
      }
      event.preventDefault();
      deps.selectAll();
    }
  };

  document.addEventListener('keydown', onKeydown);
  destroyRef.onDestroy(() => document.removeEventListener('keydown', onKeydown));
}
