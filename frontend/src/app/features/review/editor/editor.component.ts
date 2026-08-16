import { Component, EventEmitter, Output, OnDestroy, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

declare const monaco: any;

@Component({
  selector: 'app-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="flex flex-col h-full bg-gray-900 rounded-lg overflow-hidden">
      <!-- Toolbar -->
      <div class="flex items-center justify-between px-4 py-2 bg-gray-800 border-b border-gray-700">
        <div class="flex items-center gap-3">
          <label class="text-gray-400 text-sm">Language:</label>
          <select 
            [(ngModel)]="selectedLanguage"
            (ngModelChange)="onLanguageChange()"
            class="bg-gray-700 text-white text-sm rounded px-3 py-1.5 border border-gray-600 focus:border-blue-500 focus:outline-none">
            <option *ngFor="let lang of languages" [value]="lang.value">{{ lang.label }}</option>
          </select>
        </div>
        <button 
          (click)="onReview()"
          [disabled]="!code || isAnalyzing"
          class="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg transition-colors flex items-center gap-2">
          <svg *ngIf="!isAnalyzing" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
          </svg>
          <svg *ngIf="isAnalyzing" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
          {{ isAnalyzing ? 'Analyzing...' : 'Review Code' }}
        </button>
      </div>
      
      <!-- Editor Container -->
      <div #editorContainer class="flex-1 min-h-0"></div>
      
      <!-- Status Bar -->
      <div class="px-4 py-1.5 bg-gray-800 border-t border-gray-700 flex items-center justify-between text-xs text-gray-500">
        <span>{{ selectedLanguage | titlecase }} | {{ lineCount }} lines | {{ charCount }} characters</span>
        <span>{{ code ? 'Ready' : 'Enter code to review' }}</span>
      </div>
    </div>
  `,
  styles: [`:host { display: block; height: 100%; }`]
})
export class EditorComponent implements AfterViewInit, OnDestroy {
  @ViewChild('editorContainer', { static: true }) editorContainer!: ElementRef;
  @Output() reviewRequested = new EventEmitter<{ code: string; language: string }>();

  editor: any = null;
  code = '';
  selectedLanguage = 'javascript';
  isAnalyzing = false;
  lineCount = 0;
  charCount = 0;
  monacoLoaded = false;

  languages = [
    { value: 'java', label: 'Java' },
    { value: 'python', label: 'Python' },
    { value: 'javascript', label: 'JavaScript' },
    { value: 'typescript', label: 'TypeScript' },
    { value: 'cpp', label: 'C++' },
    { value: 'go', label: 'Go' },
    { value: 'csharp', label: 'C#' },
    { value: 'ruby', label: 'Ruby' },
    { value: 'rust', label: 'Rust' }
  ];

  ngAfterViewInit() {
    this.loadMonaco();
  }

  ngOnDestroy() {
    this.editor?.dispose();
  }

  private loadMonaco() {
    // Check if Monaco is already loaded
    if (typeof monaco !== 'undefined') {
      this.monacoLoaded = true;
      this.initEditor();
      return;
    }

    // Load Monaco from CDN
    const script = document.createElement('script');
    script.src = 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs/loader.min.js';
    script.onload = () => {
      (window as any).require.config({ paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs' } });
      (window as any).require(['vs/editor/editor.main'], () => {
        this.monacoLoaded = true;
        this.initEditor();
      });
    };
    document.head.appendChild(script);
  }

  private initEditor() {
    if (!this.monacoLoaded || !this.editorContainer) return;

    this.editor = monaco.editor.create(this.editorContainer.nativeElement, {
      value: '// Paste your code here...\n',
      language: this.selectedLanguage,
      theme: 'vs-dark',
      minimap: { enabled: false },
      fontSize: 14,
      lineNumbers: 'on',
      scrollBeyondLastLine: false,
      automaticLayout: true,
      tabSize: 2,
      wordWrap: 'on',
      padding: { top: 16, bottom: 16 }
    });

    this.editor.onDidChangeModelContent(() => {
      this.code = this.editor?.getValue() || '';
      this.updateStats();
    });

    this.updateStats();
  }

  onLanguageChange() {
    if (this.editor) {
      const model = this.editor.getModel();
      if (model) {
        monaco.editor.setModelLanguage(model, this.selectedLanguage);
      }
    }
  }

  private updateStats() {
    const model = this.editor?.getModel();
    if (model) {
      this.lineCount = model.getLineCount();
      this.charCount = model.getValueLength();
    }
  }

  onReview() {
    if (this.code && !this.isAnalyzing) {
      this.isAnalyzing = true;
      this.reviewRequested.emit({ code: this.code, language: this.selectedLanguage });
    }
  }

  setLoading(loading: boolean) {
    this.isAnalyzing = loading;
  }
}
