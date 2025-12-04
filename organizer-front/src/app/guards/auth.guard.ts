import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    const requiredRoles = route.data?.['roles'] as Array<string>;
    if (requiredRoles) {
        const hasPermission = requiredRoles.some(role => authService.hasRole(role));
        if (!hasPermission) {
            router.navigate(['/pedidos']);
            return false;
        }
    }
    return true;
  }

  router.navigate(['/login'], { queryParams: { returnUrl: state.url }});
  return false;
};
