import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './reset-password.component.html',
  styleUrls: ['./reset-password.component.css']
})
export class ResetPasswordComponent implements OnInit {
  token: string = '';
  password: string = '';
  confirmPassword: string = '';
  message: string = '';
  error: string = '';
  isLoading: boolean = false;
  isTokenValid: boolean = false;
  checkingToken: boolean = true;

  constructor(
    private route: ActivatedRoute,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit() {
    // Inscreve-se nas mudanças de parâmetros para garantir captura após redirecionamentos
    this.route.queryParams.subscribe(params => {
      this.token = params['token'] || '';
      console.log('ResetPasswordComponent: Token capturado:', this.token);

      if (!this.token) {
        // Se não achou no subscribe, tenta fallback no snapshot (caso raro de timing)
        this.token = this.route.snapshot.queryParamMap.get('token') || '';
      }

      if (!this.token) {
        this.error = 'Link inválido (token não encontrado). Verifique se copiou o link completo.';
        this.checkingToken = false;
        return;
      }

      // Só valida se ainda não validou (para evitar múltiplas chamadas se params mudarem)
      if (this.checkingToken) {
        this.validateToken();
      }
    });
  }

  validateToken() {
    this.authService.validateResetToken(this.token).subscribe({
      next: (isValid) => {
        this.isTokenValid = isValid;
        if (!isValid) {
          this.error = 'Este link de recuperação é inválido ou expirou.';
        }
        this.checkingToken = false;
      },
      error: (err) => {
        console.error('Erro ao validar token:', err);
        this.error = 'Erro ao conectar com o servidor para validar o link.';
        this.checkingToken = false;
      }
    });
  }

  onSubmit() {
    if (this.password !== this.confirmPassword) {
      this.error = 'As senhas não coincidem.';
      return;
    }

    this.isLoading = true;
    this.error = '';
    this.message = '';

    this.authService.resetPassword(this.token, this.password).subscribe({
      next: () => {
        this.message = 'Senha alterada com sucesso! Redirecionando para o login...';
        this.isLoading = false;
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 3000);
      },
      error: (err) => {
        this.error = 'Erro ao redefinir senha. Tente novamente.';
        this.isLoading = false;
      }
    });
  }
}
