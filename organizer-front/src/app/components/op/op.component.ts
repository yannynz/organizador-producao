import { Component, OnDestroy, OnInit } from '@angular/core';
import { OpService } from '../../services/op.service';
import { Subscription } from 'rxjs';
import { WebsocketService } from '../../services/websocket.service';
import { orders } from '../../models/orders';
@Component({
  selector: 'op-component',
  templateUrl: './op.component.html'
})
export class OpComponent implements OnInit, OnDestroy {
  orders: orders[] = [];
  subs: Subscription[] = [];

  constructor(private op: OpService, private ws: WebsocketService) {}

  ngOnInit() {
    // carga inicial via REST
    this.op.getOrders().subscribe(data => this.orders = data);

    // atualizações em tempo real
    this.subs.push(
      this.ws.watchOrders().subscribe((msg) => {
        // sua app hoje envia tanto lista quanto evento {type,data}; trate os dois jeitos:
        if (Array.isArray(msg)) {
          this.orders = msg;
        } else if (msg?.type === 'update' && msg?.data) {
          const idx = this.orders.findIndex(o => o.id === msg.data.id);
          if (idx >= 0) this.orders[idx] = msg.data; else this.orders.unshift(msg.data);
        }
      })
    );
  }

  abrirOp(nr: string) {
    this.op.openOpPdf(nr);
  }

  ngOnDestroy() {
    this.subs.forEach(s => s.unsubscribe());
  }
}

