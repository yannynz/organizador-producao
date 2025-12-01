import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Cliente } from '../../models/cliente.model';
import { ClienteService } from '../../services/cliente.service';
import { TransportadoraService } from '../../services/transportadora.service';
import { Transportadora } from '../../models/transportadora.model';

@Component({
  selector: 'app-cliente-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cliente-form.component.html',
  styleUrls: ['./cliente-form.component.css']
})
export class ClienteFormComponent implements OnInit {
  @Input() cliente: Cliente = {
    id: 0,
    nomeOficial: '',
    ativo: true,
    origin: 'MANUAL'
  };
  @Output() save = new EventEmitter<Cliente>();
  @Output() cancel = new EventEmitter<void>();

  transportadoras: Transportadora[] = [];

  constructor(
    private service: ClienteService,
    private transportadoraService: TransportadoraService
  ) {}

  ngOnInit() {
    this.transportadoraService.listAll().subscribe(list => {
      this.transportadoras = list;
    });
    if (!this.cliente.enderecos) {
      this.cliente.enderecos = [];
    }
  }

  addEndereco() {
    this.cliente.enderecos?.push({
      isDefault: this.cliente.enderecos.length === 0, // First one is default
      origin: 'MANUAL'
    });
  }

  removeEndereco(index: number) {
    this.cliente.enderecos?.splice(index, 1);
  }

  setDefault(index: number) {
    this.cliente.enderecos?.forEach((e, i) => e.isDefault = (i === index));
  }

  submit() {
    // Bind relationship if ID is set (manual binding might be needed if backend expects object)
    // Assuming backend accepts ID or object. If ID, we might need to adjust model.
    // Cliente model has `transportadoraId`.
    if (this.cliente.id) {
      this.service.update(this.cliente.id, this.cliente).subscribe(c => this.save.emit(c));
    } else {
      this.service.create(this.cliente).subscribe(c => this.save.emit(c));
    }
  }
}
