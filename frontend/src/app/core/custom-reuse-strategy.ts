import {Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, BaseRouteReuseStrategy, Params} from '@angular/router';

function paramsMatch(a: Params, b: Params): boolean {
  const aKeys = Object.keys(a);
  const bKeys = Object.keys(b);
  return aKeys.length === bKeys.length && aKeys.every(key => a[key] === b[key]);
}

@Injectable({
  providedIn: 'root',
})
export class CustomReuseStrategy extends BaseRouteReuseStrategy {
  override shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
    return future.routeConfig === curr.routeConfig && paramsMatch(future.params, curr.params);
  }
}
