import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReviewIssue,
  ReviewResponse,
  CodebaseReviewResponse,
  FileBreakdown
} from '../../../shared/models/review.model';

@Component({
  selector: 'app-results',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex flex-col h-full bg-white rounded-lg overflow-hidden border border-gray-200">
      <!-- Header -->
      <div class="px-4 py-3 border-b border-gray-200 bg-gray-50 flex-shrink-0">
        <h2 class="text-lg font-semibold text-gray-800">Review Results</h2>
        @if (codebaseResult) {
          <p class="text-xs text-gray-500 mt-0.5">
            {{ codebaseResult.totalFiles }} files analyzed
          </p>
        }
      </div>

      <!-- No Results -->
      @if (!reviewResult && !codebaseResult) {
        <div class="flex-1 flex items-center justify-center text-gray-400">
          <div class="text-center">
            <svg class="w-16 h-16 mx-auto mb-4 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
            </svg>
            <p class="text-sm">Run a review to see results</p>
          </div>
        </div>
      }

      <!-- Single File Results -->
      @if (reviewResult && !codebaseResult) {
        <div class="flex-1 overflow-y-auto p-4 space-y-4">
          <!-- Overall Assessment -->
          <div class="flex items-center gap-3 p-4 rounded-lg transition-colors" [ngClass]="getAssessmentClass()">
            <span class="text-2xl">{{ getAssessmentIcon() }}</span>
            <div>
              <p class="font-semibold">{{ reviewResult.overallAssessment | titlecase }}</p>
              <p class="text-sm opacity-75">{{ reviewResult.summary }}</p>
            </div>
          </div>

          <!-- Severity Counts -->
          <div class="grid grid-cols-3 gap-3">
            <div class="p-3 bg-red-50 rounded-lg border border-red-200">
              <p class="text-2xl font-bold text-red-600">{{ criticalCount }}</p>
              <p class="text-xs text-red-500">Critical</p>
            </div>
            <div class="p-3 bg-yellow-50 rounded-lg border border-yellow-200">
              <p class="text-2xl font-bold text-yellow-600">{{ warningCount }}</p>
              <p class="text-xs text-yellow-500">Warnings</p>
            </div>
            <div class="p-3 bg-blue-50 rounded-lg border border-blue-200">
              <p class="text-2xl font-bold text-blue-600">{{ suggestionCount }}</p>
              <p class="text-xs text-blue-500">Suggestions</p>
            </div>
          </div>

          <!-- Issues List -->
          <div class="space-y-2">
            <h3 class="font-medium text-gray-700 text-sm">Issues ({{ issues.length }})</h3>
            @for (issue of issues; let i = $index; track i) {
              <div
                class="border rounded-lg overflow-hidden transition-colors"
                [ngClass]="getIssueBorderClass(issue.severity)">
                <button
                  (click)="toggleIssue(i)"
                  class="w-full px-4 py-3 flex items-center justify-between text-left hover:bg-gray-50 transition-colors">
                  <div class="flex items-center gap-3 min-w-0">
                    <span class="px-2 py-0.5 text-xs font-medium rounded flex-shrink-0" [ngClass]="getSeverityBadgeClass(issue.severity)">
                      {{ issue.severity | uppercase }}
                    </span>
                    <span class="text-sm text-gray-600 truncate">{{ issue.category }}</span>
                    @if (issue.line) {
                      <span class="text-xs text-gray-400 flex-shrink-0">Ln {{ issue.line }}</span>
                    }
                  </div>
                  <svg
                    class="w-5 h-5 text-gray-400 transition-transform duration-200 flex-shrink-0 ml-2"
                    [class.rotate-180]="expandedIssues.has(i)"
                    fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
                  </svg>
                </button>

                @if (expandedIssues.has(i)) {
                  <div class="px-4 pb-4 border-t border-gray-100 animate-slide-down">
                    <p class="mt-3 text-sm text-gray-700">{{ issue.message }}</p>
                    @if (issue.suggestion) {
                      <div class="mt-3 p-3 bg-gray-50 rounded-lg">
                        <p class="text-xs font-medium text-gray-500 mb-1">Suggested Fix:</p>
                        <pre class="text-sm text-gray-700 whitespace-pre-wrap font-mono">{{ issue.suggestion }}</pre>
                      </div>
                    }
                  </div>
                }
              </div>
            }
          </div>
        </div>
      }

      <!-- Codebase / Multi-file Results -->
      @if (codebaseResult) {
        <div class="flex-1 overflow-y-auto">
          <!-- Summary Cards -->
          <div class="p-4 space-y-4">
            <!-- Overall Assessment -->
            <div class="flex items-center gap-3 p-4 rounded-lg" [ngClass]="getCodebaseAssessmentClass()">
              <span class="text-2xl">{{ getCodebaseAssessmentIcon() }}</span>
              <div>
                <p class="font-semibold">{{ codebaseResult.overallAssessment | titlecase }}</p>
                <p class="text-sm opacity-75">{{ codebaseResult.summary }}</p>
              </div>
            </div>

            <!-- Summary Stats -->
            <div class="grid grid-cols-2 gap-3">
              <div class="p-3 bg-gray-50 rounded-lg border border-gray-200 text-center">
                <p class="text-2xl font-bold text-gray-700">{{ codebaseResult.totalFiles }}</p>
                <p class="text-xs text-gray-500">Files Analyzed</p>
              </div>
              <div class="p-3 bg-yellow-50 rounded-lg border border-yellow-200 text-center">
                <p class="text-2xl font-bold text-yellow-600">{{ codebaseResult.issues.length }}</p>
                <p class="text-xs text-yellow-500">Total Issues</p>
              </div>
            </div>
          </div>

          <!-- File Breakdowns -->
          @if (codebaseResult.fileBreakdowns && codebaseResult.fileBreakdowns.length > 0) {
            <div class="px-4 pb-4">
              <h3 class="text-sm font-semibold text-gray-700 mb-3">File Breakdown</h3>
              <div class="space-y-2">
                @for (fb of codebaseResult.fileBreakdowns; track fb.fileId) {
                  <div class="border border-gray-200 rounded-lg overflow-hidden">
                    <div
                      class="px-4 py-3 flex items-center justify-between cursor-pointer hover:bg-gray-50 transition-colors"
                      (click)="toggleFileExpand(fb.filePath)">
                      <div class="flex items-center gap-3 min-w-0">
                        <svg
                          class="w-4 h-4 text-gray-400 transition-transform duration-200 flex-shrink-0"
                          [class.rotate-90]="expandedFiles.has(fb.filePath)"
                          fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                        </svg>
                        <svg class="w-4 h-4 text-gray-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
                        </svg>
                        <span class="font-mono text-sm text-gray-700 truncate">{{ fb.filePath }}</span>
                        <span class="text-xs text-gray-400">{{ fb.language }}</span>
                      </div>
                      <div class="flex items-center gap-2 flex-shrink-0">
                        <span class="text-xs text-gray-500">{{ fb.issueCount }} issue{{ fb.issueCount !== 1 ? 's' : '' }}</span>
                        <span class="px-1.5 py-0.5 text-xs font-medium rounded"
                              [ngClass]="getAssessmentBadgeClass(fb.assessment)">
                          {{ fb.assessment | titlecase }}
                        </span>
                      </div>
                    </div>
                  </div>
                }
              </div>
            </div>
          } @else if (codebaseResult.totalFiles > 0) {
            <div class="px-4 pb-4">
              <h3 class="text-sm font-semibold text-gray-700 mb-3">File Breakdown</h3>
              <div class="bg-gray-50 border border-gray-200 rounded-lg p-4 text-center">
                <svg class="w-8 h-8 mx-auto mb-2 text-gray-400 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
                </svg>
                <p class="text-sm text-gray-500">Per-file breakdown is not available for this review.</p>
                <p class="text-xs text-gray-400 mt-1">Cross-file issues are listed below.</p>
              </div>
            </div>
          }

          <!-- All Issues -->
          @if (codebaseResult.issues && codebaseResult.issues.length > 0) {
            <div class="p-4 space-y-2">
              <h3 class="font-medium text-gray-700 text-sm">All Issues ({{ codebaseResult.issues.length }})</h3>
              @for (issue of codebaseResult.issues; let i = $index; track i) {
                <div
                  class="border rounded-lg overflow-hidden transition-colors"
                  [ngClass]="getIssueBorderClass(issue.severity)">
                  <button
                    (click)="toggleIssue(i)"
                    class="w-full px-4 py-3 flex items-center justify-between text-left hover:bg-gray-50 transition-colors">
                    <div class="flex items-center gap-3 min-w-0">
                      <span class="px-2 py-0.5 text-xs font-medium rounded flex-shrink-0" [ngClass]="getSeverityBadgeClass(issue.severity)">
                        {{ issue.severity | uppercase }}
                      </span>
                      <span class="text-sm text-gray-600 truncate">{{ issue.category }}</span>
                      @if (issue.line) {
                        <span class="text-xs text-gray-400 flex-shrink-0">Ln {{ issue.line }}</span>
                      }
                    </div>
                    <svg
                      class="w-5 h-5 text-gray-400 transition-transform duration-200 flex-shrink-0 ml-2"
                      [class.rotate-180]="expandedIssues.has(i)"
                      fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
                    </svg>
                  </button>

                  @if (expandedIssues.has(i)) {
                    <div class="px-4 pb-4 border-t border-gray-100 animate-slide-down">
                      <p class="mt-3 text-sm text-gray-700">{{ issue.message }}</p>
                      @if (issue.suggestion) {
                        <div class="mt-3 p-3 bg-gray-50 rounded-lg">
                          <p class="text-xs font-medium text-gray-500 mb-1">Suggested Fix:</p>
                          <pre class="text-sm text-gray-700 whitespace-pre-wrap font-mono">{{ issue.suggestion }}</pre>
                        </div>
                      }
                    </div>
                  }
                </div>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; height: 100%; }
    .animate-slide-down {
      animation: slideDown 0.2s ease-out;
    }
    @keyframes slideDown {
      from { opacity: 0; transform: translateY(-4px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `]
})
export class ResultsComponent implements OnChanges {
  @Input() reviewResult: ReviewResponse | null = null;
  @Input() codebaseResult: CodebaseReviewResponse | null = null;

  expandedIssues = new Set<number>();
  expandedFiles = new Set<string>();

  issues: ReviewIssue[] = [];
  criticalCount = 0;
  warningCount = 0;
  suggestionCount = 0;

  ngOnChanges(changes: SimpleChanges) {
    if (changes['reviewResult'] && this.reviewResult) {
      this.issues = this.reviewResult.issues || [];
      this.computeCounts();
      this.codebaseResult = null;
    }
    if (changes['codebaseResult'] && this.codebaseResult) {
      this.reviewResult = null;
      this.issues = this.codebaseResult.issues || [];
      this.computeCounts();
    }
  }

  private computeCounts() {
    this.criticalCount = this.issues.filter(i => i.severity === 'critical' || i.severity === 'error').length;
    this.warningCount = this.issues.filter(i => i.severity === 'warning').length;
    this.suggestionCount = this.issues.filter(i => i.severity === 'suggestion' || i.severity === 'info').length;
  }

  toggleIssue(index: number) {
    if (this.expandedIssues.has(index)) {
      this.expandedIssues.delete(index);
    } else {
      this.expandedIssues.add(index);
    }
  }

  toggleFileExpand(filePath: string) {
    if (this.expandedFiles.has(filePath)) {
      this.expandedFiles.delete(filePath);
    } else {
      this.expandedFiles.add(filePath);
    }
  }

  getAssessmentClass(): string {
    switch (this.reviewResult?.overallAssessment?.toLowerCase()) {
      case 'good': return 'bg-green-100 text-green-800';
      case 'needs_improvement': return 'bg-yellow-100 text-yellow-800';
      case 'poor': return 'bg-red-100 text-red-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  }

  getAssessmentIcon(): string {
    switch (this.reviewResult?.overallAssessment?.toLowerCase()) {
      case 'good': return '\u2705';
      case 'needs_improvement': return '\u26A0\uFE0F';
      case 'poor': return '\u274C';
      default: return '\uD83D\uDCCB';
    }
  }

  getCodebaseAssessmentClass(): string {
    switch (this.codebaseResult?.overallAssessment?.toLowerCase()) {
      case 'good': return 'bg-green-100 text-green-800';
      case 'needs_improvement': return 'bg-yellow-100 text-yellow-800';
      case 'poor': return 'bg-red-100 text-red-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  }

  getCodebaseAssessmentIcon(): string {
    switch (this.codebaseResult?.overallAssessment?.toLowerCase()) {
      case 'good': return '\u2705';
      case 'needs_improvement': return '\u26A0\uFE0F';
      case 'poor': return '\u274C';
      default: return '\uD83D\uDCCB';
    }
  }

  getSeverityBadgeClass(severity: string): string {
    switch (severity?.toLowerCase()) {
      case 'critical': case 'error': return 'bg-red-100 text-red-700';
      case 'warning': return 'bg-yellow-100 text-yellow-700';
      case 'suggestion': case 'info': return 'bg-blue-100 text-blue-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  }

  getIssueBorderClass(severity: string): string {
    switch (severity?.toLowerCase()) {
      case 'critical': case 'error': return 'border-red-200';
      case 'warning': return 'border-yellow-200';
      case 'suggestion': case 'info': return 'border-blue-200';
      default: return 'border-gray-200';
    }
  }

  getAssessmentBadgeClass(assessment: string): string {
    switch (assessment?.toLowerCase()) {
      case 'good': return 'bg-green-100 text-green-700';
      case 'needs_improvement': return 'bg-yellow-100 text-yellow-700';
      case 'poor': return 'bg-red-100 text-red-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  }
}
