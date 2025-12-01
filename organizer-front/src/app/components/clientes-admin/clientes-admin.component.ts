import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ClienteService } from '../../services/cliente.service';
import { Cliente } from '../../models/cliente.model';
import { FormsModule } from '@angular/forms';
import { ClienteFormComponent } from '../cliente-form/cliente-form.component';

@Component({
  selector: 'app-clientes-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, ClienteFormComponent],
  templateUrl: './clientes-admin.component.html',
  styleUrls: ['./clientes-admin.component.css']
})
export class ClientesAdminComponent implements OnInit {
  clientes: Cliente[] = [];
  searchQuery = '';
  page = 0;
  
  showForm = false;
  selectedCliente: Cliente | undefined;

  constructor(private service: ClienteService) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.service.search(this.searchQuery, this.page).subscribe(res => {
      this.clientes = res.content;
    });
  }

  search() {
      this.page = 0;
      this.load();
  }

  openNew() {
    this.selectedCliente = { id: 0, nomeOficial: '', ativo: true, origin: 'MANUAL' };
    this.showForm = true;
  }

  openEdit(c: Cliente) {
    this.selectedCliente = { ...c }; // copy
    this.showForm = true;
  }

  onSave(c: Cliente) {
    this.showForm = false;
    this.load();
  }

  onCancel() {
    this.showForm = false;
  }
}
