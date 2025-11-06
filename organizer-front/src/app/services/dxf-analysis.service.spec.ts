import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DxfAnalysisService } from './dxf-analysis.service';
import { DxfAnalysis } from '../models/dxf-analysis';

describe('DxfAnalysisService', () => {
  let service: DxfAnalysisService;
  let httpMock: HttpTestingController;

  const baseUrl = '/api/dxf-analysis';

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });

    service = TestBed.inject(DxfAnalysisService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('returns latest analysis for order', () => {
    const mockAnalysis = { analysisId: 'a1' } as unknown as DxfAnalysis;

    service.getLatestByOrder('123').subscribe((result) => {
      expect(result).toEqual(mockAnalysis);
    });

    const req = httpMock.expectOne(`${baseUrl}/order/123`);
    expect(req.request.method).toBe('GET');
    req.flush(mockAnalysis);
  });

  it('maps 404 to null when latest analysis not found', () => {
    service.getLatestByOrder('999').subscribe((result) => {
      expect(result).toBeNull();
    });

    const req = httpMock.expectOne(`${baseUrl}/order/999`);
    req.flush({}, { status: 404, statusText: 'Not Found' });
  });

  it('propagates non-404 errors for latest analysis', () => {
    service.getLatestByOrder('500').subscribe({
      next: () => fail('should error'),
      error: (err) => expect(err.status).toBe(500),
    });

    const req = httpMock.expectOne(`${baseUrl}/order/500`);
    req.flush({}, { status: 500, statusText: 'Server Error' });
  });

  it('fetches history with provided limit', () => {
    const mockHistory = [{ analysisId: 'h1' }] as unknown as DxfAnalysis[];

    service.listHistory('123', 3).subscribe((result) => {
      expect(result).toEqual(mockHistory);
    });

    const req = httpMock.expectOne((request) => request.url === `${baseUrl}/order/123/history` && request.params.get('limit') === '3');
    expect(req.request.method).toBe('GET');
    req.flush(mockHistory);
  });

  it('returns empty array when history is not found', () => {
    service.listHistory('321', 5).subscribe((result) => {
      expect(result).toEqual([]);
    });

    const req = httpMock.expectOne(`${baseUrl}/order/321/history?limit=5`);
    req.flush([], { status: 404, statusText: 'Not Found' });
  });
});
