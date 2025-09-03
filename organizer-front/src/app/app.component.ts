// src/app/app.component.ts
import { Component, OnDestroy, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { WebsocketService, StatusEvent } from './services/websocket.service';
import { Subscription, timer } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'organizer-front';
  showFooter = true;

  status?: StatusEvent;
  tooltip = 'Status desconhecido';
  private sub?: Subscription;
  private lastOnline?: boolean;

  constructor(private ws: WebsocketService) {}

  ngOnInit(): void {
    this.sub = this.ws.watchStatus().subscribe(evt => {
      this.status = evt;
      this.tooltip = evt.online
        ? `Online • ${evt.instanceId ?? ''} • ${evt.latencyMs ?? '?'} ms`
        : `Offline`;
      this.lastOnline = evt.online;
    });

    timer(300).subscribe(() => this.ws.sendPingNow());
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  get badgeClass() {
    if (this.status?.online === true) return 'bg-success';
    if (this.status?.online === false) return 'bg-danger';
    return 'bg-secondary';
  }
}

