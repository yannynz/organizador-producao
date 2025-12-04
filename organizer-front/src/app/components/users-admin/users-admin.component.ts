import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '../../services/user.service';
import { User, UserRole } from '../../models/user.model';

@Component({
  selector: 'app-users-admin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './users-admin.component.html',
  styleUrls: ['./users-admin.component.css']
})
export class UsersAdminComponent implements OnInit {
  users: User[] = [];
  userForm: FormGroup;
  isEditing = false;
  selectedUserId: number | null = null;
  roles = Object.values(UserRole);

  constructor(
    private userService: UserService,
    private formBuilder: FormBuilder
  ) {
    this.userForm = this.formBuilder.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: [''],
      role: [UserRole.OPERADOR, Validators.required],
      active: [true]
    });
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers() {
    this.userService.getAll().subscribe(users => {
      this.users = users;
    });
  }

  openCreateModal() {
    this.isEditing = false;
    this.selectedUserId = null;
    this.userForm.reset({ role: UserRole.OPERADOR, active: true });
    this.userForm.get('password')?.setValidators(Validators.required);
    this.userForm.get('password')?.updateValueAndValidity();
    const modal = new (window as any).bootstrap.Modal(document.getElementById('userModal')!);
    modal.show();
  }

  openEditModal(user: User) {
    this.isEditing = true;
    this.selectedUserId = user.id;
    this.userForm.patchValue({
        name: user.name,
        email: user.email,
        role: user.role,
        active: true // API doesn't return active status in basic User model yet, assuming true or need to update model
    });
    this.userForm.get('password')?.clearValidators();
    this.userForm.get('password')?.updateValueAndValidity();
    const modal = new (window as any).bootstrap.Modal(document.getElementById('userModal')!);
    modal.show();
  }

  closeModal() {
    const modalElement = document.getElementById('userModal');
    if (modalElement) {
      const modalInstance = new (window as any).bootstrap.Modal(modalElement);
      modalInstance.hide();
    }
  }

  saveUser() {
    if (this.userForm.invalid) return;

    const userData = this.userForm.value;
    
    if (this.isEditing && this.selectedUserId) {
      this.userService.update(this.selectedUserId, userData).subscribe(() => {
        this.loadUsers();
        this.closeModal();
      });
    } else {
      this.userService.create(userData).subscribe(() => {
        this.loadUsers();
        this.closeModal();
      });
    }
  }

  deleteUser(id: number) {
    if (confirm('Tem certeza que deseja excluir este usuário?')) {
      this.userService.delete(id).subscribe(() => {
        this.loadUsers();
      });
    }
  }
}
