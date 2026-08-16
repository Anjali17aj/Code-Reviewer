import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info' | 'warning';
  dismissing: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private counter = 0;
  private readonly DISMISS_DELAY = 4000;
  private readonly ANIMATION_DELAY = 300;

  toasts = signal<Toast[]>([]);

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'error');
  }

  info(message: string): void {
    this.show(message, 'info');
  }

  warning(message: string): void {
    this.show(message, 'warning');
  }

  private show(message: string, type: Toast['type']): void {
    const toast: Toast = {
      id: ++this.counter,
      message,
      type,
      dismissing: false
    };

    this.toasts.update(current => [...current, toast]);

    setTimeout(() => this.dismiss(toast.id), this.DISMISS_DELAY);
  }

  dismiss(id: number): void {
    this.toasts.update(current =>
      current.map(t => t.id === id ? { ...t, dismissing: true } : t)
    );

    setTimeout(() => {
      this.toasts.update(current => current.filter(t => t.id !== id));
    }, this.ANIMATION_DELAY);
  }

  getIcon(type: Toast['type']): string {
    switch (type) {
      case 'success': return 'M5 13l4 4L19 7';
      case 'error': return 'M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z';
      case 'info': return 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z';
      case 'warning': return 'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z';
    }
  }

  getBgClass(type: Toast['type']): string {
    switch (type) {
      case 'success': return 'bg-emerald-600 text-white';
      case 'error': return 'bg-red-600 text-white';
      case 'info': return 'bg-blue-600 text-white';
      case 'warning': return 'bg-amber-500 text-white';
    }
  }
}
