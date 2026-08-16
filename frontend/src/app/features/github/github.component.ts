import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GithubService } from './github.service';
import { Repo, GithubPR } from '../../shared/models/review.model';

@Component({
  selector: 'app-github',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="max-w-6xl mx-auto px-4 py-8">
      <!-- Not Connected -->
      <div *ngIf="!isConnected" class="text-center py-16">
        <div class="w-20 h-20 bg-gray-100 rounded-2xl flex items-center justify-center mx-auto mb-6">
          <svg class="w-12 h-12 text-gray-600" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
          </svg>
        </div>
        <h1 class="text-3xl font-bold text-gray-900 mb-4">Connect GitHub</h1>
        <p class="text-gray-600 mb-8">Link your GitHub account to review pull requests directly.</p>
        <button (click)="connectGithub()"
                class="px-6 py-3 bg-gray-900 hover:bg-gray-800 text-white font-medium rounded-lg transition-colors">
          Connect with GitHub
        </button>
      </div>

      <!-- Connected - Repo List -->
      <div *ngIf="isConnected && !selectedRepo">
        <h1 class="text-2xl font-bold text-gray-900 mb-6">Your Repositories</h1>
        <div class="grid gap-4">
          <div *ngFor="let repo of repos"
               (click)="selectRepo(repo)"
               class="p-4 bg-white border rounded-lg hover:border-blue-500 cursor-pointer transition-colors">
            <div class="flex items-center justify-between">
              <div>
                <h3 class="font-semibold text-gray-900">{{ repo.name }}</h3>
                <p class="text-sm text-gray-500">{{ repo.full_name }}</p>
              </div>
              <div class="text-right text-sm text-gray-500">
                <span *ngIf="repo.language">&#9679; {{ repo.language }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Selected Repo - PR List -->
      <div *ngIf="isConnected && selectedRepo && !selectedPR">
        <button (click)="selectedRepo = null" class="text-blue-600 hover:underline mb-4">&larr; Back to repos</button>
        <h1 class="text-2xl font-bold text-gray-900 mb-6">{{ selectedRepo.name }} — Pull Requests</h1>
        <div class="grid gap-4">
          <div *ngFor="let pr of pullRequests"
               (click)="selectPR(pr)"
               class="p-4 bg-white border rounded-lg hover:border-blue-500 cursor-pointer transition-colors">
            <div class="flex items-start justify-between">
              <div>
                <h3 class="font-semibold text-gray-900">#{{ pr.number }} {{ pr.title }}</h3>
                <p class="text-sm text-gray-500">{{ pr.headBranch }} → {{ pr.baseBranch }}</p>
              </div>
              <span class="px-2 py-1 text-xs rounded"
                    [ngClass]="pr.state === 'open' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-700'">
                {{ pr.state }}
              </span>
            </div>
          </div>
          <div *ngIf="pullRequests.length === 0" class="text-gray-500 text-center py-8">
            No open pull requests found.
          </div>
        </div>
      </div>

      <!-- PR Review Results -->
      <div *ngIf="selectedPR">
        <button (click)="selectedPR = null; reviewResults = []" class="text-blue-600 hover:underline mb-4">&larr; Back to PRs</button>
        <h1 class="text-2xl font-bold text-gray-900 mb-2">PR #{{ selectedPR.number }} Review</h1>
        <p class="text-gray-600 mb-6">{{ selectedPR.title }}</p>

        <button (click)="reviewPR()" [disabled]="isReviewing"
                class="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white rounded-lg mb-6">
          {{ isReviewing ? 'Reviewing...' : 'Start Review' }}
        </button>

        <div *ngIf="reviewResults" class="space-y-4">
          <div *ngFor="let result of reviewResults" class="bg-white border rounded-lg p-4">
            <h3 class="font-mono text-sm font-semibold mb-2">{{ result.filename }}</h3>
            <div class="text-sm">
              <span class="font-medium">Assessment:</span>
              <span [ngClass]="{'text-green-600': result.review?.overallRating === 'good', 'text-yellow-600': result.review?.overallRating === 'needs_improvement', 'text-red-600': result.review?.overallRating === 'poor'}">
                {{ result.review?.overallRating }}
              </span>
            </div>
            <p class="text-sm text-gray-600 mt-2">{{ result.review?.summary }}</p>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class GithubComponent implements OnInit {
  isConnected = false;
  repos: Repo[] = [];
  selectedRepo: Repo | null = null;
  pullRequests: GithubPR[] = [];
  selectedPR: GithubPR | null = null;
  reviewResults: any[] = [];
  isReviewing = false;

  constructor(private githubService: GithubService) {}

  ngOnInit() {
    this.checkConnection();
  }

  checkConnection() {
    this.githubService.getRepos().subscribe({
      next: (repos) => {
        this.isConnected = true;
        this.repos = repos;
      },
      error: () => this.isConnected = false
    });
  }

  connectGithub() {
    this.githubService.getAuthUrl().subscribe({
      next: (res) => window.location.href = res.url
    });
  }

  selectRepo(repo: Repo) {
    this.selectedRepo = repo;
    const [owner, name] = repo.full_name.split('/');
    this.githubService.getPullRequests(owner, name).subscribe({
      next: (prs) => this.pullRequests = prs
    });
  }

  selectPR(pr: GithubPR) {
    this.selectedPR = pr;
    this.reviewResults = [];
  }

  reviewPR() {
    if (!this.selectedRepo || !this.selectedPR) return;
    this.isReviewing = true;
    const [owner, name] = this.selectedRepo.full_name.split('/');

    this.githubService.reviewPR(owner, name, this.selectedPR.number).subscribe({
      next: (result) => {
        this.reviewResults = result.fileReviews;
        this.isReviewing = false;
      },
      error: () => this.isReviewing = false
    });
  }
}
