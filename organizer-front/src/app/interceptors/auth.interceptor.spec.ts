import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: jasmine.SpyObj<AuthService>;
  let notificationService: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['getToken', 'logout']);
    notificationService = jasmine.createSpyObj<NotificationService>('NotificationService', ['showMessage']);
    authService.getToken.and.returnValue('token-vencido');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: NotificationService, useValue: notificationService },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('does not attach Authorization to orders read requests', () => {
    http.get('/api/orders').subscribe();

    const req = httpMock.expectOne('/api/orders');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush([]);
  });

  it('does not attach Authorization to DXF analysis read requests', () => {
    http.get('/api/dxf-analysis/order/NR120430').subscribe();

    const req = httpMock.expectOne('/api/dxf-analysis/order/NR120430');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('keeps Authorization on mutating orders requests', () => {
    http.put('/api/orders/update/1', { id: 1 }).subscribe();

    const req = httpMock.expectOne('/api/orders/update/1');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-vencido');
    req.flush({});
  });

  it('logs out when a protected request returns 401', () => {
    http.get('/api/users/me').subscribe({
      error: () => undefined,
    });

    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-vencido');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(notificationService.showMessage).toHaveBeenCalledWith(
      'Sessão expirada. Por favor, faça login novamente.',
      true
    );
    expect(authService.logout).toHaveBeenCalled();
  });
});
