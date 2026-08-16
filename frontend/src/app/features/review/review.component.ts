import { Component, ViewChild, signal, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EditorComponent } from './editor/editor.component';
import { ResultsComponent } from './results/results.component';
import { ReviewService } from './review.service';
import { FilesService } from '../files/files.service';
import { ToastService } from '../../core/services/toast.service';
import { HealthService } from '../../core/services/health.service';
import {
  ReviewDTO,
  ReviewResponse,
  CodebaseReviewResponse,
  CodeFile,
  CodeFileContent,
  ReviewMode
} from '../../shared/models/review.model';

@Component({
  selector: 'app-review',
  standalone: true,
  imports: [CommonModule, FormsModule, EditorComponent, ResultsComponent],
  template: `
    <div class="h-[calc(100vh-64px)] flex flex-col">
      <!-- Mode Toggle Bar -->
      <div class="flex-shrink-0 bg-white border-b border-gray-200 px-4 py-2">
        <div class="max-w-7xl mx-auto flex items-center justify-between">
          <div class="flex items-center gap-1 bg-gray-100 rounded-lg p-1">
            <button
              (click)="setMode('paste')"
              class="px-4 py-1.5 text-sm font-medium rounded-md transition-all duration-200"
              [class]="mode === 'paste'
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-500 hover:text-gray-700'">
              <svg class="w-4 h-4 inline-block mr-1.5 -mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"></path>
              </svg>
              Paste Code
            </button>
            <button
              (click)="setMode('select-files')"
              class="px-4 py-1.5 text-sm font-medium rounded-md transition-all duration-200"
              [class]="mode === 'select-files'
                ? 'bg-white text-gray-900 shadow-sm'
                : 'text-gray-500 hover:text-gray-700'">
              <svg class="w-4 h-4 inline-block mr-1.5 -mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
              </svg>
              Select Files
            </button>
          </div>

          <div class="flex items-center gap-3">
            <!-- Delete button for saved reviews -->
            @if (loadedReviewId) {
              <button
                (click)="confirmDeleteReview()"
                class="px-3 py-1.5 text-sm font-medium text-red-600 hover:text-red-700 hover:bg-red-50 rounded-lg transition-colors flex items-center gap-1.5">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                </svg>
                Delete Review
              </button>
            }

            <!-- Status -->
            @if (isAnalyzing()) {
              <div class="flex items-center gap-2 text-sm text-blue-600">
                <div class="animate-spin rounded-full h-4 w-4 border-2 border-blue-600 border-t-transparent"></div>
                Analyzing...
              </div>
            }
          </div>
        </div>
      </div>

      <!-- Cold Start Notice -->
      @if (healthService.isBackendUp() === false) {
        <div class="flex-shrink-0 bg-amber-50 border-b border-amber-200 px-4 py-2">
          <div class="max-w-7xl mx-auto">
            <p class="text-xs text-amber-800 flex items-center gap-2">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
              First request after idle may take up to 5 minutes due to cold start
            </p>
          </div>
        </div>
      }

      <!-- Main Content Area -->
      <div class="flex-1 flex min-h-0">
        <!-- Paste Code Mode -->
        @if (mode === 'paste') {
          <!-- Left Panel: Editor -->
          <div class="w-1/2 p-2">
            <app-editor
              #editor
              (reviewRequested)="onReview($event)">
            </app-editor>
          </div>

          <!-- Right Panel: Results -->
          <div class="w-1/2 p-2">
            <app-results
              [reviewResult]="currentReviewResult"
              [codebaseResult]="null">
            </app-results>
          </div>
        }

        <!-- Select Files Mode -->
        @if (mode === 'select-files') {
          <!-- Left Panel: File Picker + Editor -->
          <div class="w-1/2 p-2 flex flex-col">
            <!-- File Selection Panel -->
            <div class="bg-white rounded-lg border border-gray-200 overflow-hidden mb-2">
              <!-- Selection Header -->
              <div class="px-4 py-3 border-b border-gray-100 bg-gray-50 flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <h3 class="text-sm font-semibold text-gray-700">Select Files to Review</h3>
                  @if (selectedFileIds.length > 0) {
                    <span class="px-2 py-0.5 bg-blue-100 text-blue-700 text-xs font-medium rounded-full">
                      {{ selectedFileIds.length }} selected
                    </span>
                  }
                </div>
                <div class="flex items-center gap-2">
                  @if (availableFiles.length > 0) {
                    <button
                      (click)="toggleSelectAll()"
                      class="text-xs text-blue-600 hover:text-blue-800 transition-colors">
                      {{ selectedFileIds.length === availableFiles.length ? 'Deselect All' : 'Select All' }}
                    </button>
                  }
                  <button
                    (click)="analyzeSelectedFiles()"
                    [disabled]="selectedFileIds.length === 0 || isAnalyzing()"
                    class="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed text-white text-xs font-medium rounded-lg transition-colors flex items-center gap-1.5">
                    @if (isAnalyzing()) {
                      <div class="animate-spin rounded-full h-3 w-3 border-2 border-white border-t-transparent"></div>
                      Analyzing...
                    } @else {
                      <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
                      </svg>
                      Analyze
                    }
                  </button>
                </div>
              </div>

              <!-- File List -->
              <div class="max-h-64 overflow-y-auto">
                @if (loadingFiles) {
                  <div class="p-4 text-center text-gray-400 text-sm">
                    <div class="animate-spin rounded-full h-5 w-5 border-2 border-gray-400 border-t-transparent mx-auto mb-2"></div>
                    Loading files...
                  </div>
                } @else if (availableFiles.length === 0) {
                  <div class="p-6 text-center text-gray-400">
                    <svg class="w-10 h-10 mx-auto mb-2 opacity-40" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
                    </svg>
                    <p class="text-sm">No files found.</p>
                    <p class="text-xs text-gray-500 mt-1">
                      <a routerLink="/files" class="text-blue-500 hover:underline">Go to Files</a> to create or upload files first.
                    </p>
                  </div>
                } @else {
                  @for (file of availableFiles; track file.id) {
                    <label
                      class="flex items-center gap-3 px-4 py-2.5 hover:bg-gray-50 cursor-pointer transition-colors border-b border-gray-50 last:border-0"
                      [class.bg-blue-50]="selectedFileIds.includes(file.id)">
                      <input
                        type="checkbox"
                        class="w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                        [checked]="selectedFileIds.includes(file.id)"
                        (change)="toggleFileSelection(file.id)">
                      <div class="flex-1 min-w-0">
                        <p class="text-sm font-medium text-gray-700 truncate">{{ file.name }}</p>
                        <p class="text-xs text-gray-400">{{ file.language }} &middot; {{ getLineCount(file) }} lines</p>
                      </div>
                      @if (selectedFileIds.includes(file.id)) {
                        <svg class="w-4 h-4 text-blue-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                        </svg>
                      }
                    </label>
                  }
                }
              </div>
            </div>

            <!-- Inline Code Editor (for single file quick edit) -->
            @if (selectedFileIds.length === 1 && singleFileContent) {
              <div class="flex-1 min-h-0">
                <app-editor
                  #editor
                  (reviewRequested)="onReview($event)">
                </app-editor>
              </div>
            }
            @if (selectedFileIds.length !== 1) {
              <div class="flex-1 flex items-center justify-center text-gray-400 bg-gray-50 rounded-lg border-2 border-dashed border-gray-200">
                <div class="text-center px-4">
                  @if (selectedFileIds.length === 0) {
                    <svg class="w-12 h-12 mx-auto mb-3 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"></path>
                    </svg>
                    <p class="text-sm">Select one file to preview, or multiple files to analyze as a codebase</p>
                  } @else {
                    <svg class="w-12 h-12 mx-auto mb-3 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"></path>
                    </svg>
                    <p class="text-sm font-medium text-gray-600">{{ selectedFileIds.length }} files selected</p>
                    <p class="text-xs text-gray-500 mt-1">Click "Analyze" to review all selected files as a codebase</p>
                  }
                </div>
              </div>
            }
          </div>

          <!-- Right Panel: Results -->
          <div class="w-1/2 p-2">
            <app-results
              [reviewResult]="currentReviewResult"
              [codebaseResult]="currentCodebaseResult">
            </app-results>
          </div>
        }
      </div>
    </div>

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
            Are you sure you want to delete this review? You will be redirected to the history page.
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
export class ReviewComponent implements OnInit {
  @ViewChild('editor') editor!: EditorComponent;

  private toastService = inject(ToastService);
  private router = inject(Router);

  mode: ReviewMode = 'paste';
  currentReviewResult: ReviewResponse | null = null;
  currentCodebaseResult: CodebaseReviewResponse | null = null;

  availableFiles: CodeFile[] = [];
  selectedFileIds: number[] = [];
  singleFileContent: string = '';
  loadingFiles = false;
  isAnalyzing = signal(false);

  // Saved review state
  loadedReviewId: number | null = null;

  // Delete dialog state
  showDeleteDialog = false;

  constructor(
    public healthService: HealthService,
    private reviewService: ReviewService,
    private filesService: FilesService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.healthService.checkHealth();
    // Handle review ID from query params (e.g., /review?id=123)
    this.route.queryParams.subscribe(params => {
      if (params['id']) {
        this.loadReviewById(+params['id']);
      }
    });
  }

  loadReviewById(id: number) {
    this.isAnalyzing.set(true);
    this.loadedReviewId = id;
    this.reviewService.getReview(id).subscribe({
      next: (review) => {
        this.currentReviewResult = review.reviewResult || null;
        this.isAnalyzing.set(false);
      },
      error: () => {
        this.loadedReviewId = null;
        this.isAnalyzing.set(false);
        this.toastService.error('Failed to load review details');
      }
    });
  }

  setMode(newMode: ReviewMode) {
    this.mode = newMode;
    if (newMode === 'select-files' && this.availableFiles.length === 0) {
      this.loadFiles();
    }
    // Clear results when switching modes
    this.currentReviewResult = null;
    this.currentCodebaseResult = null;
  }

  loadFiles() {
    this.loadingFiles = true;
    this.filesService.getFiles().subscribe({
      next: (files) => {
        this.availableFiles = files;
        this.loadingFiles = false;
      },
      error: () => {
        this.availableFiles = [];
        this.loadingFiles = false;
      }
    });
  }

  toggleFileSelection(fileId: number) {
    const index = this.selectedFileIds.indexOf(fileId);
    if (index >= 0) {
      this.selectedFileIds.splice(index, 1);
    } else {
      this.selectedFileIds.push(fileId);
    }

    // Load single file content for preview if exactly 1 selected
    if (this.selectedFileIds.length === 1) {
      const file = this.availableFiles.find(f => f.id === this.selectedFileIds[0]);
      if (file) {
        this.singleFileContent = file.content;
      }
    } else {
      this.singleFileContent = '';
    }
  }

  toggleSelectAll() {
    if (this.selectedFileIds.length === this.availableFiles.length) {
      this.selectedFileIds = [];
      this.singleFileContent = '';
    } else {
      this.selectedFileIds = this.availableFiles.map(f => f.id);
      this.singleFileContent = '';
    }
  }

  analyzeSelectedFiles() {
    if (this.selectedFileIds.length === 0) return;

    this.isAnalyzing.set(true);
    this.currentReviewResult = null;
    this.currentCodebaseResult = null;

    this.reviewService.analyzeCodebase(this.selectedFileIds).subscribe({
      next: (result) => {
        this.currentCodebaseResult = result;
        this.isAnalyzing.set(false);
        const totalIssues = result.issues?.length || 0;
        this.toastService.success(`Review complete: ${totalIssues} issue${totalIssues !== 1 ? 's' : ''} across ${result.totalFiles} files`);
      },
      error: (err) => {
        this.isAnalyzing.set(false);
        this.toastService.error(err.error?.message || 'Codebase analysis failed. Please try again.');
      }
    });
  }

  onReview(event: { code: string; language: string }) {
    this.isAnalyzing.set(true);
    this.currentCodebaseResult = null;

    this.reviewService.analyzeCode(event.code, event.language).subscribe({
      next: (result) => {
        this.currentReviewResult = result.reviewResult || null;
        this.isAnalyzing.set(false);
        this.editor?.setLoading(false);
        this.toastService.success('Review completed successfully!');
      },
      error: (err) => {
        this.isAnalyzing.set(false);
        this.editor?.setLoading(false);
        this.toastService.error(err.error?.message || 'Review failed. Please try again.');
      }
    });
  }

  getLineCount(file: CodeFile): number {
    if (!file.content) return 0;
    return file.content.split('\n').length;
  }

  // --- Delete functionality ---

  confirmDeleteReview() {
    this.showDeleteDialog = true;
  }

  executeDelete() {
    if (!this.loadedReviewId) return;

    this.reviewService.deleteReview(this.loadedReviewId).subscribe({
      next: () => {
        this.toastService.success('Review deleted');
        this.router.navigate(['/history']);
      },
      error: (err) => {
        this.toastService.error(err.error?.message || 'Failed to delete review');
        this.cancelDelete();
      }
    });
  }

  cancelDelete() {
    this.showDeleteDialog = false;
  }
}
