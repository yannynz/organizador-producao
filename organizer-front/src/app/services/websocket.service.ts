import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RxStompService } from '@stomp/ng2-stompjs';
import { RxStompConfig } from '@stomp/rx-stomp';
import { environment } from '../enviroment';
import { map } from 'rxjs/operators';

export interface StatusEvent {
  kind: string;          // "filewatcher"
  online: boolean;
  latencyMs?: number | null;
  lastChecked?: string;
  lastSeenTs?: string;
  instanceId?: string;
  version?: string;
  source?: string;       // "rpc"
}

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private rxStompService: RxStompService;

  constructor() {
    this.rxStompService = new RxStompService();
    this.rxStompService.configure(this.myRxStompConfig());
    this.rxStompService.activate();
  }

  private myRxStompConfig(): RxStompConfig {
    return {
      brokerURL: environment.wsUrl,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 20000,
      reconnectDelay: 200,
      debug: (msg: string): void => {
        console.log(new Date(), msg);
      },
    };
  }

  public watchOrders(): Observable<any> {
    return this.rxStompService.watch('/topic/orders');
  }

  public watchPriorities(): Observable<any> {
    return this.rxStompService.watch('/topic/prioridades');
 }

  public watchDxfAnalysis(): Observable<any> {
    return this.rxStompService
      .watch('/topic/dxf-analysis')
      .pipe(map((msg) => JSON.parse(msg.body)));
  }

  public sendCreateOrder(order: any): void {
    this.rxStompService.publish({ destination: '/app/orders/create', body: JSON.stringify(order) });
  }

  public sendUpdateOrder(order: any): void {
    this.rxStompService.publish({ destination: '/app/orders/update', body: JSON.stringify(order) });
  }

  public sendDeleteOrder(orderId: number): void {
    this.rxStompService.publish({ destination: `/orders/delete/${orderId}` });
  }

  public watchStatus(): Observable<StatusEvent> {
    return this.rxStompService
      .watch('/topic/status')               // broker /topic conforme Spring
      .pipe(map(msg => JSON.parse(msg.body) as StatusEvent));
  }

  public sendPingNow(): void {
    // dispara o ping on-demand; o backend publicará o resultado em /topic/status
    this.rxStompService.publish({ destination: '/app/status/ping-now', body: '{}' });
  }
}
