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

/**
 * Creates a copy of an HTTP Request to disable the service worker capture &
 * copy FormData `File`s for web browser engines which have bugs that prevent
 * file uploads.
 *
 * @see https://bugs.webkit.org/show_bug.cgi?id=319985
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

  return request;
}
