import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { environment } from '../enviroment';
import { AuthResponse, User, UserRole } from '../models/user.model';
import { jwtDecode } from 'jwt-decode';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = environment.apiBaseUrl + '/auth';
  private tokenKey = 'auth_token';
  private userSubject = new BehaviorSubject<User | null>(null);
  public user$ = this.userSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    if (isPlatformBrowser(this.platformId)) {
      this.loadUserFromToken();
    }
  }

  login(credentials: {email: string, password: string}, rememberMe: boolean = false): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, credentials).pipe(
      tap(response => {
        this.handleAuthResponse(response, rememberMe);
      })
    );
  }

  register(user: {name: string, email: string, password: string}): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, user).pipe(
      tap(response => {
        this.handleAuthResponse(response, false); // Default to session storage or let user login again? usually auto-login
        // Let's assume standard session behavior (no "remember me" checkbox on register usually)
      })
    );
  }

  private handleAuthResponse(response: AuthResponse, rememberMe: boolean) {
    if (isPlatformBrowser(this.platformId)) {
      if (rememberMe) {
          localStorage.setItem(this.tokenKey, response.token);
      } else {
          sessionStorage.setItem(this.tokenKey, response.token);
      }
      this.loadUserFromToken();
    }
  }

  logout() {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem(this.tokenKey);
      sessionStorage.removeItem(this.tokenKey);
    }
    this.userSubject.next(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    if (isPlatformBrowser(this.platformId)) {
      return localStorage.getItem(this.tokenKey) || sessionStorage.getItem(this.tokenKey);
    }
    return null;
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
        const decoded: any = jwtDecode(token);
        const now = Date.now() / 1000;
        return decoded.exp > now;
    } catch (e) {
        return false;
    }
  }

  hasRole(role: UserRole | string): boolean {
    const user = this.userSubject.value;
    if (!user) return false;
    return user.role === role;
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/forgot-password`, { email });
  }

  validateResetToken(token: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.apiUrl}/validate-token?token=${token}`);
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/reset-password`, { token, newPassword });
  }

  refreshUserProfile(): void {
      const token = this.getToken();
      if (!token) return;

      // URL hack: replace /auth with /users/me
      const meUrl = this.apiUrl.replace('/auth', '/users/me');

      this.http.get<User>(meUrl).subscribe({
          next: (user) => {
              this.userSubject.next(user);
          },
          error: (err) => {
             console.error('Failed to refresh user profile', err);
          }
      });
  }

  private loadUserFromToken() {
    const token = this.getToken();
    if (token) {
      try {
        const decoded: any = jwtDecode(token);
        const user: User = {
            id: decoded.id,
            name: decoded.name,
            email: decoded.sub,
            role: decoded.role as UserRole
        };
        this.userSubject.next(user);
        this.refreshUserProfile();
      } catch (e) {
        console.error('Invalid token', e);
        this.logout();
      }
    }
  }
}
