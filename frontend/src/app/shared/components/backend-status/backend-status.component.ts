import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HealthService } from '../../../core/services/health.service';

@Component({
  selector: 'app-backend-status',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fixed bottom-4 right-4 z-50">
      <div 
        class="flex items-center gap-2 px-3 py-2 rounded-full text-xs font-medium shadow-lg"
        [class]="healthService.isBackendUp() === true 
          ? 'bg-green-100 text-green-800 border border-green-200' 
          : healthService.isBackendUp() === false
            ? 'bg-amber-100 text-amber-800 border border-amber-200'
            : 'bg-gray-100 text-gray-600 border border-gray-200'">
        
        @if (healthService.isBackendUp() === null) {
          <!-- Checking... -->
          <div class="w-2 h-2 rounded-full bg-gray-400 animate-pulse"></div>
          <span>Checking backend...</span>
        } @else if (healthService.isBackendUp() === true) {
          <!-- Healthy -->
          <div class="w-2 h-2 rounded-full bg-green-500"></div>
          <span>Backend: Healthy</span>
        } @else {
          <!-- Starting/Cold -->
          <div class="w-2 h-2 rounded-full bg-amber-500 animate-pulse"></div>
          <span>Backend: Starting...</span>
        }
      </div>
    </div>
  `
})
export class BackendStatusComponent {
  constructor(public healthService: HealthService) {}
}
