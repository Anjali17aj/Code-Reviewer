import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { GithubService } from '../github/github.service';

@Component({
  selector: 'app-github-callback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex items-center justify-center min-h-screen">
      <div class="text-center">
        <div *ngIf="!error" class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
        <h2 class="text-xl font-semibold mb-2">
          {{ error ? 'Connection Failed' : 'Connecting to GitHub...' }}
        </h2>
        <p *ngIf="error" class="text-red-600">{{ error }}</p>
        <p *ngIf="!error" class="text-gray-600">Please wait while we connect your account.</p>
      </div>
    </div>
  `
})
export class GithubCallbackComponent implements OnInit {
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private githubService: GithubService
  ) {}

  ngOnInit() {
    const code = this.route.snapshot.queryParamMap.get('code');
    const state = this.route.snapshot.queryParamMap.get('state');

    if (!code || !state) {
      this.error = 'Missing code or state parameter';
      setTimeout(() => this.router.navigate(['/github']), 3000);
      return;
    }

    this.githubService.handleCallback(code, state).subscribe({
      next: () => {
        this.router.navigate(['/github']);
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to connect GitHub account';
        setTimeout(() => this.router.navigate(['/github']), 3000);
      }
    });
  }
}
