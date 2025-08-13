import { Routes } from '@angular/router';
import { OrdersComponent } from './components/orders/orders.component';
import { DeliveryComponent } from './components/delivery/delivery.component';
import { DeliveredComponent } from './components/delivered/delivered.component';
import { DeliveryReturnComponent } from './components/delivery-return/delivery-return.component';
import { MontagemComponent} from "./components/montagem/montagem.component";

export const routes: Routes = [
    { path: 'pedidos', component:OrdersComponent},
    { path: 'entrega', component:DeliveryComponent},
    { path: 'entregues', component:DeliveredComponent},
    { path: 'entregaVolta', component:DeliveryReturnComponent},
    { path: 'montagem', component:MontagemComponent},
    { path: '', redirectTo: '/pedidos', pathMatch: 'full' },
  ];
