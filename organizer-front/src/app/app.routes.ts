import { Routes } from '@angular/router';
import { OrdersComponent } from './components/orders/orders.component';
import { DeliveryComponent } from './components/delivery/delivery.component';
import { DeliveredComponent } from './components/delivered/delivered.component';
import { DeliveryReturnComponent } from './components/delivery-return/delivery-return.component';
import { MontagemComponent } from "./components/montagem/montagem.component";
import { DeliveredAdminComponent } from './components/delivered-admin/delivered-admin.component';
import { RubberComponent } from './components/rubber/rubber.component';
import { OpComponent } from './components/op/op.component';
import { LoginComponent } from './components/login/login.component';
import { authGuard } from './guards/auth.guard';
import { ForgotPasswordComponent } from './components/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './components/reset-password/reset-password.component';
import { RegisterComponent } from './components/register/register.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: 'pedidos', component: OrdersComponent },
  { path: 'entrega', component: DeliveryComponent, canActivate: [authGuard] },
  { path: 'entregues', component: DeliveredComponent, canActivate: [authGuard] },
  { path: 'entregaVolta', component: DeliveryReturnComponent, canActivate: [authGuard] },
  { path: 'montagem', component: MontagemComponent, canActivate: [authGuard] },
  { path: 'admin', component: DeliveredAdminComponent, canActivate: [authGuard], data: { roles: ['ADMIN', 'DESENHISTA'] } },
  { path: 'borracha', component: RubberComponent, canActivate: [authGuard] },
  { path: 'op', component: OpComponent, canActivate: [authGuard] },
  { path: '', redirectTo: '/pedidos', pathMatch: 'full' },
];