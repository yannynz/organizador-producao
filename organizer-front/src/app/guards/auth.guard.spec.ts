import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  const state = { url: '/admin/users' } as RouterStateSnapshot;

  function runGuard(data: Record<string, unknown> = {}) {
    const route = { data } as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => authGuard(route, state));
  }

  beforeEach(() => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['isAuthenticated', 'hasRole']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows authenticated users when no role is required', () => {
    authService.isAuthenticated.and.returnValue(true);

    expect(runGuard()).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('allows authenticated users with one of the required roles', () => {
    authService.isAuthenticated.and.returnValue(true);
    authService.hasRole.and.callFake(role => role === 'ADMIN');

    expect(runGuard({ roles: ['ADMIN', 'MANAGER'] })).toBeTrue();
  });

  it('redirects authenticated users without the required role to pedidos', () => {
    authService.isAuthenticated.and.returnValue(true);
    authService.hasRole.and.returnValue(false);

    expect(runGuard({ roles: ['ADMIN'] })).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/pedidos']);
  });

  it('redirects anonymous users to login with returnUrl', () => {
    authService.isAuthenticated.and.returnValue(false);

    expect(runGuard()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/admin/users' } });
  });
});
