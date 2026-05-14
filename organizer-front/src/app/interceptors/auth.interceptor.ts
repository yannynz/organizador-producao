import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';
import { catchError, throwError } from 'rxjs';

const PUBLIC_READ_ENDPOINTS = ['/api/orders', '/api/dxf-analysis'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const notificationService = inject(NotificationService);
  const token = authService.getToken();

  let request = req;

  if (token && !isPublicReadRequest(req.method, req.url)) {
    request = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }
  
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        notificationService.showMessage('Sessão expirada. Por favor, faça login novamente.', true);
        authService.logout();
      } else if (error.status === 403) {
        // Mantém o usuário logado; apenas informa que a ação não é permitida.
        notificationService.showMessage('Ação não permitida para seu perfil.', true);
      }
      return throwError(() => error);
    })
  );
};

function isPublicReadRequest(method: string, url: string): boolean {
  const normalizedMethod = method.toUpperCase();
  if (normalizedMethod !== 'GET' && normalizedMethod !== 'HEAD') {
    return false;
  }

  const pathname = getPathname(url);
  return PUBLIC_READ_ENDPOINTS.some(endpoint =>
    pathname === endpoint || pathname.startsWith(`${endpoint}/`)
  );
}

function getPathname(url: string): string {
  try {
    return new URL(url, 'http://localhost').pathname;
  } catch {
    return url.split('?')[0];
  }
}
