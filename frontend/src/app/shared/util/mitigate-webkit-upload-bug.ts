import {HttpRequest} from '@angular/common/http';

const IOS_PLATFORMS = [
  'iPad Simulator',
  'iPhone Simulator',
  'iPod Simulator',
  'iPad',
  'iPhone',
  'iPod'
];

function isIOSWebkit() {
  return IOS_PLATFORMS.includes(navigator.platform)
    // iPad on iOS >=13
    || (navigator.userAgent.includes("Mac") && "ontouchend" in document)
}

function isControlledByServiceWorker() {
  return 'serviceWorker' in navigator && navigator.serviceWorker.controller != null;
}

/**
 * Creates a copy of an HTTP Request that skips the service worker, and copies
 * FormData `File`s for web browser engines which have bugs that prevent file
 * uploads.
 *
 * Uploads routed through the service worker get no upload progress, and
 * browsers abort them once the transfer runs for about a minute, so every
 * upload made while a service worker controls the page bypasses it.
 *
 * @see https://bugs.webkit.org/show_bug.cgi?id=319985
 * @see https://github.com/grimmory-tools/grimmory/issues/3012
 *
 * @param request Target request
 * @return HttpRequest<FormData> Either the original target request, or a mutated request.
 */
export function mitigateWebkitUploadBug(request: HttpRequest<FormData>): HttpRequest<FormData> {
  if (isIOSWebkit()) {
    const body = new FormData();

    if (request.body != null) {
      // If there is a request body, we need to make a copy of every
      // file included in the request body via `File.slice`
      for (const key of request.body.keys()) {
        const values = request.body.getAll(key);
        for (const value of values) {
          if (typeof value == 'string') {
            body.append(key, value);
          } else {
            body.append(key, value.slice(0, value.size, value.type), value.name);
          }
        }
      }
    }

    return request.clone({
      headers: request.headers.append('ngsw-bypass', '1'),
      body: request.body ? body : null,
    });
  }

  if (isControlledByServiceWorker()) {
    return request.clone({
      headers: request.headers.append('ngsw-bypass', '1'),
    });
  }

  return request;
}
