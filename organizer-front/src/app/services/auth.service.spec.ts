import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { environment } from '../enviroment';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: jasmine.SpyObj<Router>;

  const tokenKey = 'auth_token';

  function makeToken(payload: Record<string, unknown>): string {
    const encode = (value: object) =>
      btoa(JSON.stringify(value))
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');

    return `${encode({ alg: 'none', typ: 'JWT' })}.${encode(payload)}.signature`;
  }

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: Router, useValue: router },
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('stores login token in localStorage when remember me is enabled and refreshes profile', () => {
    const token = makeToken({
      id: 4,
      name: 'CLI',
      sub: 'cli@test.com',
      role: 'OPERATOR',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });

    service.login({ email: 'cli@test.com', password: '123456' }, true).subscribe();

    const login = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(login.request.method).toBe('POST');
    login.flush({ token });

    const me = httpMock.expectOne(`${environment.apiBaseUrl}/users/me`);
    me.flush({ id: 4, name: 'CLI', email: 'cli@test.com', role: 'OPERATOR' });

    expect(localStorage.getItem(tokenKey)).toBe(token);
    expect(sessionStorage.getItem(tokenKey)).toBeNull();
    expect(service.isAuthenticated()).toBeTrue();
    expect(service.hasRole('OPERATOR')).toBeTrue();
  });

  it('stores register token in sessionStorage by default', () => {
    const token = makeToken({
      id: 8,
      name: 'Novo',
      sub: 'novo@test.com',
      role: 'ADMIN',
      exp: Math.floor(Date.now() / 1000) + 3600,
    });

    service.register({ name: 'Novo', email: 'novo@test.com', password: '123456' }).subscribe();

    const register = httpMock.expectOne(`${environment.apiBaseUrl}/auth/register`);
    expect(register.request.method).toBe('POST');
    register.flush({ token });

    const me = httpMock.expectOne(`${environment.apiBaseUrl}/users/me`);
    me.flush({ id: 8, name: 'Novo', email: 'novo@test.com', role: 'ADMIN' });

    expect(sessionStorage.getItem(tokenKey)).toBe(token);
    expect(localStorage.getItem(tokenKey)).toBeNull();
    expect(service.hasRole('ADMIN')).toBeTrue();
  });

  it('rejects expired or malformed tokens', () => {
    sessionStorage.setItem(tokenKey, makeToken({ exp: Math.floor(Date.now() / 1000) - 10 }));
    expect(service.isAuthenticated()).toBeFalse();

    sessionStorage.setItem(tokenKey, 'invalid-token');
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('logout clears stored tokens and navigates to login', () => {
    localStorage.setItem(tokenKey, 'a');
    sessionStorage.setItem(tokenKey, 'b');

    service.logout();

    expect(localStorage.getItem(tokenKey)).toBeNull();
    expect(sessionStorage.getItem(tokenKey)).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
