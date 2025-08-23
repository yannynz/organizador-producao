import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdvancedFiltersComponent } from './advanced-filters.component';

describe('AdvancedFiltersComponent', () => {
  let component: AdvancedFiltersComponent;
  let fixture: ComponentFixture<AdvancedFiltersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdvancedFiltersComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdvancedFiltersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
