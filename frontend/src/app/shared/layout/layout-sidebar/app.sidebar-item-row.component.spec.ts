import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';

import {LayoutService} from '../layout.service';
import {AppSidebarItemRowComponent} from './app.sidebar-item-row.component';

describe('AppSidebarItemRowComponent', () => {
  let fixture: ComponentFixture<AppSidebarItemRowComponent>;
  let component: AppSidebarItemRowComponent;
  const currentPath = signal('/');

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppSidebarItemRowComponent],
      providers: [{provide: LayoutService, useValue: {currentPath}}],
    });
    TestBed.overrideComponent(AppSidebarItemRowComponent, {set: {template: ''}});
    fixture = TestBed.createComponent(AppSidebarItemRowComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('index', 0);
    fixture.componentRef.setInput('parentKey', 'home-0');
    fixture.componentRef.setInput('item', {id: 'dashboard', label: 'Dashboard', routerLink: ['/dashboard']});
  });

  it('reports route as active when the path matches', () => {
    currentPath.set('/dashboard');
    expect(component.isRouteActive()).toBe(true);
  });

  it('reports route as inactive when the path differs', () => {
    currentPath.set('/library/1/books');
    expect(component.isRouteActive()).toBe(false);
  });
});
