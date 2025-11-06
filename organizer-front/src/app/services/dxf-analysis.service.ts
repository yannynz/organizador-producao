import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, throwError } from 'rxjs';
import { environment } from '../enviroment';
import { DxfAnalysis } from '../models/dxf-analysis';

@Injectable({
  providedIn: 'root',
})
export class DxfAnalysisService {
  private readonly baseUrl = `${environment.apiBaseUrl}/dxf-analysis`;

  constructor(private http: HttpClient) {}

  getLatestByOrder(orderNr: string): Observable<DxfAnalysis | null> {
    const encoded = encodeURIComponent(orderNr);
    return this.http.get<DxfAnalysis>(`${this.baseUrl}/order/${encoded}`).pipe(
      catchError((err) => {
        if (err.status === 404) {
          return of(null);
        }
        return throwError(() => err);
      })
    );
  }

  listHistory(orderNr: string, limit = 5): Observable<DxfAnalysis[]> {
    const encoded = encodeURIComponent(orderNr);
    return this.http
      .get<DxfAnalysis[]>(`${this.baseUrl}/order/${encoded}/history`, {
        params: { limit: limit.toString() },
      })
      .pipe(
        catchError((err) => {
          if (err.status === 404) {
            return of([]);
          }
          return throwError(() => err);
        })
      );
  }
}
