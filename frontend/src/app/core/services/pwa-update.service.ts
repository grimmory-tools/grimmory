import { inject, Service } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs/operators';

@Service()
export class PwaUpdateService {
    private readonly swUpdate = inject(SwUpdate);

    constructor() {
        if (this.swUpdate.isEnabled) {
            this.swUpdate.versionUpdates
                .pipe(filter((evt): evt is VersionReadyEvent => evt.type === 'VERSION_READY'))
                .subscribe((evt) => {
                    console.info('New version available. Refreshing...');
                    const hash = evt.latestVersion.hash;
                    if (this.shouldReload(`version_${hash}`)) {
                        window.location.reload();
                    }
                });

            this.swUpdate.unrecoverable.subscribe(event => {
                console.error('Service Worker entered unrecoverable state:', event.reason);
                if (this.shouldReload(`unrecoverable_${event.reason}`)) {
                    window.location.reload();
                }
            });
        }
    }

    private shouldReload(reason: string): boolean {
        const key = 'pwa_reload_guard';
        const now = Date.now();
        try {
            const guardStr = sessionStorage.getItem(key);
            const guard = guardStr ? JSON.parse(guardStr) : null;

            if (guard?.reason === reason && now - guard.timestamp < 10000) {
                console.error(`PWA reload guard triggered for reason: ${reason}. Stopped reloading to avoid loop.`);
                return false;
            }

            sessionStorage.setItem(key, JSON.stringify({ reason, timestamp: now }));
            return true;
        } catch (e) {
            console.warn('PWA reload guard could not access sessionStorage', e);
            return true;
        }
    }
}
