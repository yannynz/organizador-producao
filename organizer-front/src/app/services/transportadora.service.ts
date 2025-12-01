import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../enviroment';
import { Transportadora } from '../models/transportadora.model';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class TransportadoraService {
  private apiUrl = (environment.apiBaseUrl || '/api').replace(/\/$/, '') + '/transportadoras';

  constructor(private http: HttpClient) {}

  search(query: string, page: number = 0, size: number = 20): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (query) params = params.set('search', query);
    
    return this.http.get<any>(this.apiUrl, { params });
  }

  getById(id: number): Observable<Transportadora> {
    return this.http.get<Transportadora>(`${this.apiUrl}/${id}`);
  }

  create(transportadora: Transportadora): Observable<Transportadora> {
    return this.http.post<Transportadora>(this.apiUrl, transportadora);
  }

  update(id: number, transportadora: Transportadora): Observable<Transportadora> {
    return this.http.patch<Transportadora>(`${this.apiUrl}/${id}`, transportadora);
  }

  listAll(): Observable<Transportadora[]> {
    return this.http.get<any>(this.apiUrl, { params: new HttpParams().set('size', '1000') })
      .pipe(map(res => res.content));
  }
}
