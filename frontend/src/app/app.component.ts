import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from './layouts/header/header.component';
import { ToastService } from './core/services/toast.service';
import { BackendStatusComponent } from './shared/components/backend-status/backend-status.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, HeaderComponent, BackendStatusComponent],
  template: `
    <div class="min-h-screen flex flex-col">
      <app-header></app-header>
      <main class="flex-1">
        <router-outlet></router-outlet>
      </main>
    </div>

    <!-- Backend Status Indicator -->
    <app-backend-status></app-backend-status>

    <!-- Global Toast Container -->
    <div class="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
      @for (toast of toastService.toasts(); track toast.id) {
        <div
          class="px-4 py-3 rounded-lg shadow-lg transition-all duration-300 animate-slide-up"
          [class.opacity-0]="toast.dismissing"
          [class]="toastService.getBgClass(toast.type)">
          <div class="flex items-center gap-2">
            <svg class="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" [attr.d]="toastService.getIcon(toast.type)"></path>
            </svg>
            <span class="text-sm">{{ toast.message }}</span>
            <button
              (click)="toastService.dismiss(toast.id)"
              class="ml-auto flex-shrink-0 opacity-70 hover:opacity-100 transition-opacity">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .animate-slide-up {
      animation: slideUp 0.2s ease-out;
    }
    @keyframes slideUp {
      from { opacity: 0; transform: translateY(8px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `]
})
export class AppComponent {
  title = 'Code Reviewer';
  toastService = inject(ToastService);
}
