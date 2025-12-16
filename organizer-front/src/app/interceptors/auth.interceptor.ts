import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const notificationService = inject(NotificationService);
  const token = authService.getToken();

  let request = req;

  if (token) {
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
