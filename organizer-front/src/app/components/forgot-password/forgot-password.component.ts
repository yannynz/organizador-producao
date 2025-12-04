import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.css']
})
export class ForgotPasswordComponent {
  email: string = '';
  message: string = '';
  error: string = '';
  isLoading: boolean = false;

  constructor(private authService: AuthService, private router: Router) {}

  onSubmit() {
    this.isLoading = true;
    this.message = '';
    this.error = '';

    this.authService.forgotPassword(this.email).subscribe({
      next: () => {
        this.message = 'Se o e-mail estiver cadastrado, você receberá instruções em breve.';
        this.isLoading = false;
      },
      error: (err) => {
        this.error = 'Erro ao processar solicitação. Tente novamente.';
        this.isLoading = false;
      }
    });
  }
}
