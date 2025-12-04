import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DeliveredListComponent } from '../delivered-list/tabs/delivered-list/delivered-list.component';
import { PipelineBoardComponent } from './tabs/pipeline-board/pipeline-board.component';
import { ClientesAdminComponent } from '../clientes-admin/clientes-admin.component';
import { TransportadorasAdminComponent } from '../transportadoras-admin/transportadoras-admin.component';
import { UsersAdminComponent } from '../users-admin/users-admin.component';
import { AuthService } from '../../services/auth.service';
import { UserRole } from '../../models/user.model';

@Component({
  selector: 'app-delivered-admin',
  standalone: true,
  imports: [
    CommonModule, 
    DeliveredListComponent, 
    PipelineBoardComponent, 
    ClientesAdminComponent, 
    TransportadorasAdminComponent,
    UsersAdminComponent
  ],
  templateUrl: './delivered-admin.component.html',
  styleUrls: ['./delivered-admin.component.css']
})
export class DeliveredAdminComponent implements OnInit {
  tab: 'delivered' | 'pipeline' | 'clientes' | 'transportadoras' | 'users' = 'delivered';
  isAdmin = false;

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.isAdmin = this.authService.hasRole(UserRole.ADMIN);
  }
}

