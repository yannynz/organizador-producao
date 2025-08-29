import { Routes } from '@angular/router';
import { OrdersComponent } from './components/orders/orders.component';
import { DeliveryComponent } from './components/delivery/delivery.component';
import { DeliveredComponent } from './components/delivered/delivered.component';
import { DeliveryReturnComponent } from './components/delivery-return/delivery-return.component';
import { MontagemComponent } from "./components/montagem/montagem.component";
import { DeliveredAdminComponent } from './components/delivered-admin/delivered-admin.component';
import { RubberComponent } from './components/rubber/rubber.component';
import { OpComponent } from './components/op/op.component';

export const routes: Routes = [
  { path: 'pedidos', component: OrdersComponent },
  { path: 'entrega', component: DeliveryComponent },
  { path: 'entregues', component: DeliveredComponent },
  { path: 'entregaVolta', component: DeliveryReturnComponent },
  { path: 'montagem', component: MontagemComponent },
  { path: 'admin', component: DeliveredAdminComponent },
  { path: 'borracha', component: RubberComponent },
  { path: 'op', component: OpComponent },
  { path: '', redirectTo: '/pedidos', pathMatch: 'full' },
];
