import { Component, OnInit, ViewChild, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FilesService } from './files.service';
import { ReviewService } from '../review/review.service';
import { ToastService } from '../../core/services/toast.service';
import {
  CodeFile,
  Folder,
  ReviewResponse,
  CodebaseReviewResponse,
  FileTreeItem
} from '../../shared/models/review.model';
import { EditorComponent } from '../review/editor/editor.component';
import { ResultsComponent } from '../review/results/results.component';

@Component({
  selector: 'app-files',
  standalone: true,
  imports: [CommonModule, FormsModule, EditorComponent, ResultsComponent],
  template: `
    <div class="h-[calc(100vh-64px)] flex overflow-hidden">
      <!-- Left Panel: File Explorer -->
      <div class="w-72 bg-gray-900 text-white flex flex-col border-r border-gray-700 flex-shrink-0">
        <!-- Explorer Header -->
        <div class="p-3 border-b border-gray-700 flex items-center justify-between">
          <h2 class="font-semibold text-sm tracking-wider text-gray-300">EXPLORER</h2>
          <div class="flex gap-1">
            <button (click)="showNewFolderDialog()" class="p-1.5 hover:bg-gray-700 rounded transition-colors" title="New Folder">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 13h6m-3-3v6m-9 1V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z"></path>
              </svg>
            </button>
            <button (click)="showNewFileDialog()" class="p-1.5 hover:bg-gray-700 rounded transition-colors" title="New File">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
              </svg>
            </button>
            <label class="p-1.5 hover:bg-gray-700 rounded cursor-pointer transition-colors" title="Upload File(s)">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path>
              </svg>
              <input
                type="file"
                class="hidden"
                (change)="onFileUpload($event)"
                multiple
                accept=".java,.py,.js,.ts,.jsx,.tsx,.cpp,.c,.cs,.go,.rb,.rs,.php,.swift,.kt,.scala,.vue,.svelte,.html,.css,.scss,.json,.yaml,.yml,.md">
            </label>
          </div>
        </div>

        <!-- Bulk Action Bar -->
        @if (selectedFileIds().size > 0) {
          <div class="px-3 py-2 bg-blue-900/40 border-b border-blue-700/50 flex items-center justify-between transition-all">
            <span class="text-xs text-blue-300">{{ selectedFileIds().size }} file{{ selectedFileIds().size !== 1 ? 's' : '' }} selected</span>
            <div class="flex gap-1">
              <button
                (click)="analyzeSelected()"
                [disabled]="isAnalyzing"
                class="px-2 py-1 bg-blue-600 hover:bg-blue-500 disabled:bg-gray-600 text-white text-xs font-medium rounded transition-colors">
                {{ isAnalyzing ? 'Analyzing...' : 'Analyze Selected' }}
              </button>
              <button
                (click)="clearSelection()"
                class="px-2 py-1 bg-gray-600 hover:bg-gray-500 text-white text-xs rounded transition-colors">
                Clear
              </button>
            </div>
          </div>
        }

        <!-- Analyze All Button -->
        @if (allFiles().length > 1 && selectedFileIds().size === 0) {
          <div class="px-3 py-2 border-b border-gray-700">
            <button
              (click)="analyzeAll()"
              [disabled]="isAnalyzing"
              class="w-full px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 disabled:bg-gray-600 text-white text-xs font-medium rounded transition-colors flex items-center justify-center gap-1.5">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"></path>
              </svg>
              Analyze All ({{ allFiles().length }} files)
            </button>
          </div>
        }

        <!-- Drag-and-Drop Zone -->
        @if (isDragging) {
          <div class="m-2 p-4 border-2 border-dashed border-blue-500 bg-blue-900/20 rounded-lg text-center text-blue-300 text-xs">
            Drop files to upload
          </div>
        }

        <!-- File Tree -->
        <div
          class="flex-1 overflow-y-auto p-2"
          (dragover)="onDragOver($event)"
          (dragleave)="onDragLeave($event)"
          (drop)="onDrop($event)">
          @if (loading) {
            <div class="text-gray-400 text-sm p-2 flex items-center gap-2">
              <div class="animate-spin rounded-full h-4 w-4 border-2 border-gray-400 border-t-transparent"></div>
              Loading...
            </div>
          }
          @if (!loading && allFiles().length === 0 && allFolders().length === 0) {
            <div class="text-gray-500 text-sm p-2 text-center py-8">
              <svg class="w-10 h-10 mx-auto mb-2 opacity-40" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"></path>
              </svg>
              <p>No files yet.</p>
              <p class="text-xs mt-1">Create, upload, or drag & drop files.</p>
            </div>
          }

          <!-- Folder list -->
          @for (folder of allFolders(); track folder.id) {
            <div class="file-item group">
              <svg class="file-icon text-yellow-400" fill="currentColor" viewBox="0 0 24 24">
                <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"></path>
              </svg>
              <span class="file-name flex-1">{{ folder.name }}</span>
              <button
                (click)="confirmDeleteFolder(folder)"
                class="opacity-0 group-hover:opacity-100 p-0.5 hover:bg-red-600/30 rounded transition-all"
                title="Delete folder">
                <svg class="w-3.5 h-3.5 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                </svg>
              </button>
            </div>
          }

          <!-- File list -->
          @for (file of allFiles(); track file.id) {
            <div class="file-item group" [class.selected]="selectedFile?.id === file.id" (click)="onFileClick(file)">
              <input type="checkbox" class="file-checkbox" [checked]="selectedFileIds().has(file.id)" (click)="$event.stopPropagation()" (change)="toggleFileSelection(file)">
              <svg class="file-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
              <span class="file-name flex-1">{{ file.name }}</span>
              <button
                (click)="confirmDeleteFile(file); $event.stopPropagation()"
                class="opacity-0 group-hover:opacity-100 p-0.5 hover:bg-red-600/30 rounded transition-all"
                title="Delete file">
                <svg class="w-3.5 h-3.5 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                </svg>
              </button>
            </div>
          }
        </div>

        <!-- New Folder Dialog -->
        @if (showFolderDialog) {
          <div class="p-3 border-t border-gray-700 animate-slide-up">
            <input
              [(ngModel)]="newFolderName"
              placeholder="Folder name"
              class="w-full px-2 py-1.5 bg-gray-700 rounded text-sm border border-gray-600 focus:border-blue-500 focus:outline-none text-white placeholder-gray-400"
              (keyup.enter)="createFolder()">
            <div class="flex gap-2 mt-2">
              <button (click)="createFolder()" class="px-3 py-1 bg-blue-600 hover:bg-blue-500 rounded text-xs transition-colors">Create</button>
              <button (click)="showFolderDialog = false" class="px-3 py-1 bg-gray-600 hover:bg-gray-500 rounded text-xs transition-colors">Cancel</button>
            </div>
          </div>
        }

        <!-- New File Dialog -->
        @if (showFileDialog) {
          <div class="p-3 border-t border-gray-700 animate-slide-up">
            <input
              [(ngModel)]="newFileName"
              placeholder="File name (e.g., App.java)"
              class="w-full px-2 py-1.5 bg-gray-700 rounded text-sm border border-gray-600 focus:border-blue-500 focus:outline-none text-white placeholder-gray-400"
              (keyup.enter)="createFile()">
            <div class="flex gap-2 mt-2">
              <button (click)="createFile()" class="px-3 py-1 bg-blue-600 hover:bg-blue-500 rounded text-xs transition-colors">Create</button>
              <button (click)="showFileDialog = false" class="px-3 py-1 bg-gray-600 hover:bg-gray-500 rounded text-xs transition-colors">Cancel</button>
            </div>
          </div>
        }

        <!-- Delete Confirmation Dialog -->
        @if (showDeleteDialog) {
          <div class="p-3 border-t border-red-900/50 bg-red-950/20 animate-slide-up">
            <p class="text-xs text-gray-300 mb-2">
              Delete <span class="font-medium text-white">{{ deleteTargetName }}</span>?
              @if (deleteTargetType === 'folder') {
                <span class="block text-gray-400 mt-0.5">All contents will also be deleted.</span>
              }
            </p>
            <div class="flex gap-2">
              <button (click)="executeDelete()" class="px-3 py-1 bg-red-600 hover:bg-red-500 rounded text-xs text-white transition-colors">Delete</button>
              <button (click)="cancelDelete()" class="px-3 py-1 bg-gray-600 hover:bg-gray-500 rounded text-xs transition-colors">Cancel</button>
            </div>
          </div>
        }
      </div>

      <!-- Center Panel: Editor -->
      <div class="flex-1 p-2 min-w-0">
        @if (selectedFile) {
          <app-editor
            #editor
            (reviewRequested)="onReview($event)">
          </app-editor>
        } @else {
          <div class="h-full flex items-center justify-center text-gray-400">
            <div class="text-center">
              <svg class="w-16 h-16 mx-auto mb-4 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
              </svg>
              <p class="text-sm">Select a file to edit and review</p>
              <p class="text-xs text-gray-500 mt-1">or select multiple files for codebase analysis</p>
            </div>
          </div>
        }
      </div>

      <!-- Right Panel: Results -->
      <div class="w-[420px] p-2 flex-shrink-0">
        <app-results
          [reviewResult]="currentReviewResult"
          [codebaseResult]="currentCodebaseResult">
        </app-results>
      </div>
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
    .file-item {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.25rem 0.5rem 0.25rem 1rem;
      font-size: 0.75rem;
      color: #9ca3af;
      border-radius: 0.25rem;
      cursor: pointer;
      transition: all 0.15s;
    }
    .file-item:hover {
      background-color: rgba(31, 41, 55, 1);
    }
    .file-item.selected {
      background-color: rgba(30, 58, 138, 0.5);
      color: #93c5fd;
    }
    .file-checkbox {
      width: 0.875rem;
      height: 0.875rem;
      border-radius: 0.25rem;
      border-color: #4b5563;
      background-color: #374151;
      color: #3b82f6;
      cursor: pointer;
      flex-shrink: 0;
    }
    .file-icon {
      width: 1rem;
      height: 1rem;
      flex-shrink: 0;
    }
    .file-name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `]
})
export class FilesComponent implements OnInit {
  @ViewChild('editor') editor!: EditorComponent;

  private toastService = inject(ToastService);

  fileTree: FileTreeItem[] = [];
  allFiles = signal<CodeFile[]>([]);
  allFolders = signal<Folder[]>([]);
  selectedFile: CodeFile | null = null;
  currentReviewResult: ReviewResponse | null = null;
  currentCodebaseResult: CodebaseReviewResponse | null = null;
  loading = true;
  isAnalyzing = false;

  showFolderDialog = false;
  showFileDialog = false;
  newFolderName = '';
  newFileName = '';
  currentFolderId: number | null = null;

  expandedFolders = new Set<string>();
  selectedFileIds = signal<Set<number>>(new Set());

  isDragging = false;
  private dragCounter = 0;

  // Delete dialog state
  showDeleteDialog = false;
  deleteTargetName = '';
  deleteTargetType: 'file' | 'folder' = 'file';
  private deleteTargetId: number | null = null;

  constructor(
    private filesService: FilesService,
    private reviewService: ReviewService
  ) {}

  ngOnInit() {
    this.loadFiles();
  }

  loadFiles() {
    this.loading = true;
    this.filesService.getFileTree().subscribe({
      next: (tree) => {
        this.fileTree = tree || [];
        this.loading = false;
      },
      error: () => {
        this.fileTree = [];
        this.loading = false;
      }
    });
    this.filesService.getFiles().subscribe({
      next: (files) => this.allFiles.set(files),
      error: () => this.allFiles.set([])
    });
    this.filesService.getFolders().subscribe({
      next: (folders) => this.allFolders.set(folders),
      error: () => this.allFolders.set([])
    });
  }

  toggleFolder(name: string) {
    if (this.expandedFolders.has(name)) {
      this.expandedFolders.delete(name);
    } else {
      this.expandedFolders.add(name);
    }
  }

  onFileClick(file: CodeFile) {
    this.selectedFile = file;
    this.currentReviewResult = null;
    this.currentCodebaseResult = null;
  }

  toggleFileSelection(file: CodeFile) {
    const current = new Set(this.selectedFileIds());
    if (current.has(file.id)) {
      current.delete(file.id);
    } else {
      current.add(file.id);
    }
    this.selectedFileIds.set(current);
  }

  clearSelection() {
    this.selectedFileIds.set(new Set());
  }

  analyzeSelected() {
    const ids = Array.from(this.selectedFileIds());
    if (ids.length === 0) return;
    this.isAnalyzing = true;
    this.currentCodebaseResult = null;
    this.currentReviewResult = null;

    this.reviewService.analyzeCodebase(ids).subscribe({
      next: (result) => {
        this.currentCodebaseResult = result;
        this.isAnalyzing = false;
        const totalIssues = result.issues?.length || 0;
        this.toastService.success('Review completed: ' + totalIssues + ' issues found');
      },
      error: (err) => {
        this.isAnalyzing = false;
        this.toastService.error(err.error?.message || 'Codebase analysis failed. Please try again.');
      }
    });
  }

  analyzeAll() {
    const allIds = this.allFiles().map(f => f.id);
    if (allIds.length === 0) return;
    this.isAnalyzing = true;
    this.currentCodebaseResult = null;
    this.currentReviewResult = null;

    this.reviewService.analyzeCodebase(allIds).subscribe({
      next: (result) => {
        this.currentCodebaseResult = result;
        this.isAnalyzing = false;
        const totalIssues = result.issues?.length || 0;
        this.toastService.success('Review completed: ' + totalIssues + ' issues found');
      },
      error: (err) => {
        this.isAnalyzing = false;
        this.toastService.error(err.error?.message || 'Codebase analysis failed. Please try again.');
      }
    });
  }

  onReview(event: { code: string; language: string }) {
    if (this.selectedFile) {
      this.filesService.updateFile(this.selectedFile.id, undefined, event.code).subscribe({
        error: () => {
          // Silently fail file save - review still proceeds
        }
      });
    }
    this.currentCodebaseResult = null;
    this.reviewService.analyzeCode(event.code, event.language).subscribe({
      next: (result) => {
        this.currentReviewResult = result.reviewResult || null;
        this.editor?.setLoading(false);
      },
      error: (err) => {
        this.editor?.setLoading(false);
        this.toastService.error(err.error?.message || 'Review failed. Please try again.');
      }
    });
  }

  showNewFolderDialog() {
    this.showFolderDialog = true;
    this.newFolderName = '';
  }

  showNewFileDialog() {
    this.showFileDialog = true;
    this.newFileName = '';
  }

  createFolder() {
    if (this.newFolderName.trim()) {
      this.filesService.createFolder(this.newFolderName.trim(), this.currentFolderId || undefined).subscribe(() => {
        this.showFolderDialog = false;
        this.loadFiles();
        this.toastService.success('Folder created');
      });
    }
  }

  createFile() {
    if (this.newFileName.trim()) {
      const language = this.detectLanguage(this.newFileName);
      this.filesService.createFile(this.newFileName.trim(), language, '', this.currentFolderId || undefined).subscribe((file) => {
        this.showFileDialog = false;
        this.loadFiles();
        this.selectFile(file);
        this.toastService.success('File created');
      });
    }
  }

  private selectFile(file: CodeFile) {
    this.selectedFile = file;
    this.currentReviewResult = null;
    this.currentCodebaseResult = null;
  }

  onFileUpload(event: any) {
    const files: FileList = event.target.files;
    if (!files || files.length === 0) return;

    const uploadObservables = Array.from(files).map(file =>
      this.filesService.uploadFile(file, this.currentFolderId || undefined)
    );

    let completed = 0;
    let failed = 0;
    uploadObservables.forEach(obs => obs.subscribe({
      next: (uploaded) => {
        completed++;
        if (completed === 1) this.selectFile(uploaded);
        if (completed + failed === uploadObservables.length) {
          this.loadFiles();
          if (failed === 0) {
            this.toastService.success(completed + ' file' + (completed !== 1 ? 's' : '') + ' uploaded');
          } else if (completed === 0) {
            this.toastService.error('Upload failed. The server may be starting up - please try again.');
          } else {
            this.toastService.warning(completed + ' file' + (completed !== 1 ? 's' : '') + ' uploaded, ' + failed + ' failed');
          }
        }
      },
      error: (err) => {
        completed++;
        failed++;
        console.error('Upload failed:', err);
        if (completed + failed === uploadObservables.length) {
          this.loadFiles();
          if (failed === uploadObservables.length) {
            this.toastService.error('Upload failed. The server may be starting up - please try again.');
          } else {
            this.toastService.warning(completed + ' file' + (completed !== 1 ? 's' : '') + ' uploaded, ' + failed + ' failed');
          }
        }
      }
    }));

    event.target.value = '';
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragCounter++;
    this.isDragging = true;
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragCounter--;
    if (this.dragCounter <= 0) {
      this.isDragging = false;
      this.dragCounter = 0;
    }
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
    this.dragCounter = 0;

    const files = event.dataTransfer?.files;
    if (!files || files.length === 0) return;

    const uploadObservables = Array.from(files).map(file =>
      this.filesService.uploadFile(file, this.currentFolderId || undefined)
    );

    let completed = 0;
    let failed = 0;
    uploadObservables.forEach(obs => obs.subscribe({
      next: (uploaded) => {
        completed++;
        if (completed === 1) this.selectFile(uploaded);
        if (completed + failed === uploadObservables.length) {
          this.loadFiles();
          if (failed === 0) {
            this.toastService.success(completed + ' file' + (completed !== 1 ? 's' : '') + ' uploaded');
          } else if (completed === 0) {
            this.toastService.error('Upload failed. The server may be starting up - please try again.');
          } else {
            this.toastService.warning(completed + ' file' + (completed !== 1 ? 's' : '') + ' uploaded, ' + failed + ' failed');
          }
        }
      },
      error: (err) => {
        completed++;
        failed++;
        console.error('Upload failed:', err);
        if (completed + failed === uploadObservables.length) {
          this.loadFiles();
          if (failed === uploadObservables.length) {
            this.toastService.error('Upload failed. The server may be starting up - please try again.');
          } else {
            this.toastService.warning(completed + ' file' + (completed !== 1 ? 's' : '') + ' uploaded, ' + failed + ' failed');
          }
        }
      }
    }));
  }
        if (completed === uploadObservables.length) {
          this.loadFiles();
          this.toastService.error('Some files failed to upload');
        }
      }
    }));
  }

  private detectLanguage(filename: string): string {
    const ext = filename.split('.').pop()?.toLowerCase();
    const map: Record<string, string> = {
      java: 'java', py: 'python', js: 'javascript', ts: 'typescript',
      jsx: 'javascript', tsx: 'typescript', cpp: 'cpp', c: 'c',
      cs: 'csharp', go: 'go', rb: 'ruby', rs: 'rust', kt: 'kotlin',
      scala: 'scala', swift: 'swift', vue: 'javascript', php: 'php',
      html: 'html', css: 'css', scss: 'scss', json: 'json'
    };
    return map[ext || ''] || 'plaintext';
  }

  // --- Delete functionality ---

  confirmDeleteFile(file: CodeFile) {
    this.deleteTargetType = 'file';
    this.deleteTargetName = file.name;
    this.deleteTargetId = file.id;
    this.showDeleteDialog = true;
  }

  confirmDeleteFolder(folder: Folder) {
    this.deleteTargetType = 'folder';
    this.deleteTargetName = folder.name;
    this.deleteTargetId = folder.id;
    this.showDeleteDialog = true;
  }

  executeDelete() {
    if (this.deleteTargetId === null) return;

    if (this.deleteTargetType === 'file') {
      this.filesService.deleteFile(this.deleteTargetId).subscribe({
        next: () => {
          this.toastService.success('File deleted');
          if (this.selectedFile?.id === this.deleteTargetId) {
            this.selectedFile = null;
            this.currentReviewResult = null;
            this.currentCodebaseResult = null;
          }
          this.loadFiles();
        },
        error: (err) => {
          this.toastService.error(err.error?.message || 'Failed to delete file');
        }
      });
    } else {
      this.filesService.deleteFolder(this.deleteTargetId).subscribe({
        next: () => {
          this.toastService.success('Folder deleted');
          this.loadFiles();
        },
        error: (err) => {
          this.toastService.error(err.error?.message || 'Failed to delete folder');
        }
      });
    }

    this.cancelDelete();
  }

  cancelDelete() {
    this.showDeleteDialog = false;
    this.deleteTargetName = '';
    this.deleteTargetId = null;
  }
}
