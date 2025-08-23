import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DeliveredListComponent } from '../delivered-list/tabs/delivered-list/delivered-list.component';
import { PipelineBoardComponent } from './tabs/pipeline-board/pipeline-board.component';

@Component({
  selector: 'app-delivered-admin',
  standalone: true,
  imports: [CommonModule, DeliveredListComponent, PipelineBoardComponent],
  templateUrl: './delivered-admin.component.html',
  styleUrls: ['./delivered-admin.component.css']
})
export class DeliveredAdminComponent implements OnInit {
  tab: 'delivered' | 'pipeline' = 'delivered';
  ngOnInit(): void {}
}

