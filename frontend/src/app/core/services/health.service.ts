import { Injectable, signal, runInInjectionContext, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { timeout } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class HealthService {
  private backendUp = signal<boolean | null>(null);
  
  constructor(private http: HttpClient) {
    // Auto-check health on service initialization
    this.checkHealth();
  }

  get isBackendUp() {
    return this.backendUp;
  }

  checkHealth(): void {
    // If we already know status, don't check again
    if (this.backendUp() !== null) return;

    this.http.get<{ status: string }>(`${environment.apiUrl}/health`)
      .pipe(timeout(5000)) // 5 second timeout
      .subscribe({
        next: () => {
          this.backendUp.set(true);
        },
        error: () => {
          this.backendUp.set(false);
        }
      });
  }
}
