import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HistoryService, PaginatedResponse } from './history.service';
import { ReviewDTO } from '../../shared/models/review.model';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- Header -->
      <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900">Review History</h1>
        <p class="mt-2 text-gray-600">View your past code reviews and track improvements over time.</p>
      </div>

      <!-- Filters -->
      <div class="mb-6 bg-gray-50 rounded-lg p-4 border border-gray-200">
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <!-- Language Filter -->
          <div>
            <label for="language-filter" class="block text-sm font-medium text-gray-700 mb-1">
              Language
            </label>
            <select
              id="language-filter"
              [(ngModel)]="selectedLanguage"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
            >
              <option value="">All Languages</option>
              <option value="javascript">JavaScript</option>
              <option value="typescript">TypeScript</option>
              <option value="python">Python</option>
              <option value="java">Java</option>
              <option value="csharp">C#</option>
              <option value="cpp">C++</option>
              <option value="go">Go</option>
              <option value="rust">Rust</option>
              <option value="php">PHP</option>
              <option value="ruby">Ruby</option>
            </select>
          </div>

          <!-- Assessment Filter -->
          <div>
            <label for="assessment-filter" class="block text-sm font-medium text-gray-700 mb-1">
              Assessment
            </label>
            <select
              id="assessment-filter"
              [(ngModel)]="selectedAssessment"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
            >
              <option value="">All Assessments</option>
              <option value="good">Good</option>
              <option value="needs_improvement">Needs Improvement</option>
              <option value="poor">Poor</option>
            </select>
          </div>

          <!-- Start Date -->
          <div>
            <label for="start-date" class="block text-sm font-medium text-gray-700 mb-1">
              Start Date
            </label>
            <input
              id="start-date"
              type="date"
              [(ngModel)]="startDate"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
            >
          </div>

          <!-- End Date -->
          <div>
            <label for="end-date" class="block text-sm font-medium text-gray-700 mb-1">
              End Date
            </label>
            <input
              id="end-date"
              type="date"
              [(ngModel)]="endDate"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
            >
          </div>
        </div>

        <!-- Filter Actions -->
        <div class="flex items-center gap-2 mt-4">
          <button
            (click)="applyFilters()"
            class="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
          >
            Apply Filters
          </button>
          <button
            (click)="clearFilters()"
            class="px-4 py-2 bg-gray-200 hover:bg-gray-300 text-gray-700 text-sm font-medium rounded-lg transition-colors"
          >
            Clear Filters
          </button>
        </div>
      </div>

      <!-- Loading State -->
      @if (isLoading()) {
        <div class="flex justify-center items-center py-12">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
          <span class="ml-3 text-gray-600">Loading review history...</span>
        </div>
      } @else if (error()) {
        <!-- Error State -->
        <div class="bg-red-50 border border-red-200 rounded-lg p-4">
          <div class="flex">
            <svg class="w-5 h-5 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
            </svg>
            <div class="ml-3">
              <h3 class="text-sm font-medium text-red-800">Error loading review history</h3>
              <p class="mt-1 text-sm text-red-700">{{ error() }}</p>
              <button
                (click)="loadHistory()"
                class="mt-2 text-sm font-medium text-red-600 hover:text-red-500"
              >
                Try again
              </button>
            </div>
          </div>
        </div>
      } @else if (reviews().length === 0) {
        <!-- Empty State -->
        <div class="text-center py-12 bg-gray-50 rounded-xl border-2 border-dashed border-gray-300">
          <svg class="w-16 h-16 text-gray-400 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
          </svg>
          <h3 class="text-lg font-medium text-gray-900 mb-2">No reviews found</h3>
          <p class="text-gray-600 mb-4">
            @if (hasActiveFilters()) {
              No reviews match your current filters. Try adjusting or clearing them.
            } @else {
              You haven't created any code reviews yet. Start by reviewing some code!
            }
          </p>
          <a
            routerLink="/review"
            class="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            Create your first review
          </a>
        </div>
      } @else {
        <!-- Reviews Table -->
        <div class="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <div class="overflow-x-auto">
            <table class="min-w-full divide-y divide-gray-200">
              <thead class="bg-gray-50">
                <tr>
                  <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Language
                  </th>
                  <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Source Type
                  </th>
                  <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Rating
                  </th>
                  <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Issues
                  </th>
                  <th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Date
                  </th>
                  <th scope="col" class="relative px-6 py-3">
                    <span class="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody class="bg-white divide-y divide-gray-200">
                @for (review of reviews(); track review.id) {
                  <tr class="hover:bg-gray-50 transition-colors">
                    <td class="px-6 py-4 whitespace-nowrap">
                      <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                        {{ review.language }}
                      </span>
                    </td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {{ review.sourceType }}
                    </td>
                    <td class="px-6 py-4 whitespace-nowrap">
                      @if (review.overallRating) {
                        <span [class]="getRatingClass(review.overallRating)">
                          {{ review.overallRating | titlecase }}
                        </span>
                      } @else {
                        <span class="text-gray-400">N/A</span>
                      }
                    </td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      <div class="flex items-center space-x-2">
                        @if (review.criticalCount > 0) {
                          <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-800">
                            {{ review.criticalCount }} critical
                          </span>
                        }
                        @if (review.warningCount > 0) {
                          <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-yellow-100 text-yellow-800">
                            {{ review.warningCount }} warning{{ review.warningCount !== 1 ? 's' : '' }}
                          </span>
                        }
                        @if (review.suggestionCount > 0) {
                          <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800">
                            {{ review.suggestionCount }} suggestion{{ review.suggestionCount !== 1 ? 's' : '' }}
                          </span>
                        }
                      </div>
                    </td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {{ formatDate(review.createdAt) }}
                    </td>
                    <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                      <div class="flex items-center justify-end gap-2">
                        <button
                          (click)="viewReview(review.id)"
                          class="text-blue-600 hover:text-blue-900 transition-colors"
                        >
                          View
                        </button>
                        <span class="text-gray-300">|</span>
                        <button
                          (click)="confirmDeleteReview(review)"
                          class="text-red-500 hover:text-red-700 transition-colors"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- Pagination -->
          @if (totalPages() > 1) {
            <div class="bg-white px-4 py-3 flex items-center justify-between border-t border-gray-200 sm:px-6">
              <div class="flex-1 flex justify-between sm:hidden">
                <button
                  (click)="goToPage(currentPage() - 1)"
                  [disabled]="currentPage() === 0"
                  class="relative inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Previous
                </button>
                <button
                  (click)="goToPage(currentPage() + 1)"
                  [disabled]="currentPage() === totalPages() - 1"
                  class="ml-3 relative inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Next
                </button>
              </div>
              <div class="hidden sm:flex-1 sm:flex sm:items-center sm:justify-between">
                <div>
                  <p class="text-sm text-gray-700">
                    Showing
                    <span class="font-medium">{{ getStartIndex() + 1 }}</span>
                    to
                    <span class="font-medium">{{ getEndIndex() }}</span>
                    of
                    <span class="font-medium">{{ totalElements() }}</span>
                    results
                  </p>
                </div>
                <div>
                  <nav class="relative z-0 inline-flex rounded-md shadow-sm -space-x-px" aria-label="Pagination">
                    <button
                      (click)="goToPage(currentPage() - 1)"
                      [disabled]="currentPage() === 0"
                      class="relative inline-flex items-center px-2 py-2 rounded-l-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <span class="sr-only">Previous</span>
                      <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
                      </svg>
                    </button>
                    @for (page of getPageNumbers(); track page) {
                      <button
                        (click)="goToPage(page)"
                        [class]="page === currentPage() ? 'z-10 bg-blue-50 border-blue-500 text-blue-600' : 'bg-white border-gray-300 text-gray-500 hover:bg-gray-50'"
                        class="relative inline-flex items-center px-4 py-2 border text-sm font-medium"
                      >
                        {{ page + 1 }}
                      </button>
                    }
                    <button
                      (click)="goToPage(currentPage() + 1)"
                      [disabled]="currentPage() === totalPages() - 1"
                      class="relative inline-flex items-center px-2 py-2 rounded-r-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <span class="sr-only">Next</span>
                      <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                      </svg>
                    </button>
                  </nav>
                </div>
              </div>
            </div>
          }
        </div>
      }

      <!-- Delete Confirmation Dialog -->
      @if (showDeleteDialog) {
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div class="bg-white rounded-xl shadow-2xl max-w-sm w-full mx-4 p-6">
            <div class="flex items-center gap-3 mb-4">
              <div class="flex-shrink-0 w-10 h-10 rounded-full bg-red-100 flex items-center justify-center">
                <svg class="w-5 h-5 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z"></path>
                </svg>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-gray-900">Delete Review</h3>
                <p class="text-sm text-gray-500">This action cannot be undone.</p>
              </div>
            </div>
            <p class="text-sm text-gray-600 mb-6">
              Are you sure you want to delete this {{ deleteTargetType }}?
              @if (deleteTargetName) {
                <span class="font-medium text-gray-900">"{{ deleteTargetName }}"</span>
              }
            </p>
            <div class="flex items-center justify-end gap-3">
              <button
                (click)="cancelDelete()"
                class="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
              >
                Cancel
              </button>
              <button
                (click)="executeDelete()"
                class="px-4 py-2 text-sm font-medium text-white bg-red-600 hover:bg-red-700 rounded-lg transition-colors"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: []
})
export class HistoryComponent implements OnInit {
  private toastService = inject(ToastService);

  // State signals
  reviews = signal<ReviewDTO[]>([]);
  isLoading = signal<boolean>(false);
  error = signal<string | null>(null);
  currentPage = signal<number>(0);
  pageSize = signal<number>(10);
  totalElements = signal<number>(0);
  totalPages = signal<number>(0);

  // Filter state
  selectedLanguage = '';
  selectedAssessment = '';
  startDate = '';
  endDate = '';

  // Delete dialog state
  showDeleteDialog = false;
  deleteTargetName = '';
  deleteTargetType = 'review';
  private deleteTargetId: number | null = null;

  constructor(
    private historyService: HistoryService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadHistory();
  }

  loadHistory(): void {
    this.isLoading.set(true);
    this.error.set(null);

    this.historyService.getHistory(
      this.currentPage(),
      this.pageSize(),
      this.selectedLanguage || undefined,
      this.selectedAssessment || undefined,
      this.startDate || undefined,
      this.endDate || undefined
    ).subscribe({
      next: (response: PaginatedResponse<ReviewDTO>) => {
        this.reviews.set(response.content);
        this.totalElements.set(response.totalElements);
        this.totalPages.set(response.totalPages);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Error loading review history:', err);
        this.error.set('Failed to load review history. Please try again.');
        this.isLoading.set(false);
      }
    });
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.loadHistory();
  }

  clearFilters(): void {
    this.selectedLanguage = '';
    this.selectedAssessment = '';
    this.startDate = '';
    this.endDate = '';
    this.currentPage.set(0);
    this.loadHistory();
  }

  hasActiveFilters(): boolean {
    return !!(this.selectedLanguage || this.selectedAssessment || this.startDate || this.endDate);
  }

  onLanguageFilterChange(): void {
    this.currentPage.set(0);
    this.loadHistory();
  }

  viewReview(id: number): void {
    this.router.navigate(['/review'], { queryParams: { id } });
  }

  // --- Delete functionality ---

  confirmDeleteReview(review: ReviewDTO) {
    this.deleteTargetType = 'review';
    this.deleteTargetName = review.overallRating
      ? `${review.language} review (${review.overallRating})`
      : `${review.language} review`;
    this.deleteTargetId = review.id;
    this.showDeleteDialog = true;
  }

  executeDelete() {
    if (this.deleteTargetId === null) return;

    this.historyService.deleteReview(this.deleteTargetId).subscribe({
      next: () => {
        this.toastService.success('Review deleted');
        this.loadHistory();
      },
      error: (err) => {
        this.toastService.error(err.error?.message || 'Failed to delete review');
      }
    });

    this.cancelDelete();
  }

  cancelDelete() {
    this.showDeleteDialog = false;
    this.deleteTargetName = '';
    this.deleteTargetId = null;
  }

  // --- Pagination ---

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) {
      this.currentPage.set(page);
      this.loadHistory();
    }
  }

  getPageNumbers(): number[] {
    const pages: number[] = [];
    const maxVisiblePages = 5;
    let startPage = Math.max(0, this.currentPage() - Math.floor(maxVisiblePages / 2));
    let endPage = Math.min(this.totalPages() - 1, startPage + maxVisiblePages - 1);

    if (endPage - startPage + 1 < maxVisiblePages) {
      startPage = Math.max(0, endPage - maxVisiblePages + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
      pages.push(i);
    }
    return pages;
  }

  getStartIndex(): number {
    return this.currentPage() * this.pageSize();
  }

  getEndIndex(): number {
    return Math.min((this.currentPage() + 1) * this.pageSize(), this.totalElements());
  }

  getRatingClass(rating: string): string {
    const baseClass = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium';
    switch (rating.toLowerCase()) {
      case 'excellent':
        return `${baseClass} bg-green-100 text-green-800`;
      case 'good':
        return `${baseClass} bg-blue-100 text-blue-800`;
      case 'needs_improvement':
        return `${baseClass} bg-yellow-100 text-yellow-800`;
      case 'poor':
        return `${baseClass} bg-red-100 text-red-800`;
      default:
        return `${baseClass} bg-gray-100 text-gray-800`;
    }
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
