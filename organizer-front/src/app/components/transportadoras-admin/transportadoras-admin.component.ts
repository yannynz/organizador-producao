import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TransportadoraService } from '../../services/transportadora.service';
import { Transportadora } from '../../models/transportadora.model';
import { FormsModule } from '@angular/forms';
import { TransportadoraFormComponent } from '../transportadora-form/transportadora-form.component';

@Component({
  selector: 'app-transportadoras-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, TransportadoraFormComponent],
  templateUrl: './transportadoras-admin.component.html',
  styleUrls: ['./transportadoras-admin.component.css']
})
export class TransportadorasAdminComponent implements OnInit {
  transportadoras: Transportadora[] = [];
  searchQuery = '';
  page = 0;
  
  showForm = false;
  selectedItem: Transportadora | undefined;

  constructor(private service: TransportadoraService) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.service.search(this.searchQuery, this.page).subscribe(res => {
      this.transportadoras = res.content;
    });
  }

  search() {
      this.page = 0;
      this.load();
  }

  openNew() {
    this.selectedItem = { id: 0, nomeOficial: '', ativo: true };
    this.showForm = true;
  }

  openEdit(t: Transportadora) {
    this.selectedItem = { ...t };
    this.showForm = true;
  }

  onSave(t: Transportadora) {
    this.showForm = false;
    this.load();
  }

  onCancel() {
    this.showForm = false;
  }
}
