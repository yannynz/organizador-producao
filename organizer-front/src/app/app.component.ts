// src/app/app.component.ts
import { Component, OnDestroy, OnInit } from '@angular/core';
import { RouterOutlet, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { WebsocketService, StatusEvent } from './services/websocket.service';
import { Subscription, timer } from 'rxjs';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, RouterModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'organizer-front';
  showFooter = true;

  status?: StatusEvent;
  tooltip = 'Status desconhecido';
  showFileWatcherOfflinePopup = false;
  private sub?: Subscription;
  private pingSub?: Subscription;
  private offlinePopupTimeout?: ReturnType<typeof setTimeout>;
  private offlinePopupDismissed = false;

  constructor(private ws: WebsocketService, public authService: AuthService) {}

  ngOnInit(): void {
    this.authService.loadUserFromToken();
    this.sub = this.ws.watchStatus().subscribe(evt => {
      this.status = evt;
      this.tooltip = evt.online
        ? `Online • ${evt.instanceId ?? ''} • ${evt.latencyMs ?? '?'} ms`
        : `Offline`;
      this.handleFileWatcherStatus(evt.online);
    });

    this.pingSub = timer(300, 30000).subscribe(() => this.ws.sendPingNow());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.pingSub?.unsubscribe();
    this.clearOfflinePopupTimeout();
  }

  get badgeClass() {
    if (this.status?.online === true) return 'bg-success';
    if (this.status?.online === false) return 'bg-danger';
    return 'bg-secondary';
  }

  dismissFileWatcherOfflinePopup(): void {
    this.showFileWatcherOfflinePopup = false;
    this.offlinePopupDismissed = true;
  }

  logout() {
    this.authService.logout();
  }

  private handleFileWatcherStatus(online: boolean): void {
    if (online) {
      this.showFileWatcherOfflinePopup = false;
      this.offlinePopupDismissed = false;
      this.clearOfflinePopupTimeout();
      return;
    }

    if (this.offlinePopupTimeout || this.offlinePopupDismissed || this.showFileWatcherOfflinePopup) {
      return;
    }

    this.offlinePopupTimeout = setTimeout(() => {
      this.offlinePopupTimeout = undefined;
      if (this.status?.online === false && !this.offlinePopupDismissed) {
        this.showFileWatcherOfflinePopup = true;
      }
    }, 90000);
  }

  private clearOfflinePopupTimeout(): void {
    if (this.offlinePopupTimeout) {
      clearTimeout(this.offlinePopupTimeout);
      this.offlinePopupTimeout = undefined;
    }
  }
}

