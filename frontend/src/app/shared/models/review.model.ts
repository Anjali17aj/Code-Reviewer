export interface User {
  id: number;
  email: string;
  name: string;
  githubId?: number;
  avatarUrl?: string;
  createdAt: string;
}

export interface Review {
  id: number;
  userId: number;
  language: string;
  sourceType: string;
  codeInput: string;
  reviewResult?: string;
  overallRating?: string;
  criticalCount: number;
  warningCount: number;
  suggestionCount: number;
  createdAt: string;
}

export interface ReviewIssue {
  line: number;
  column: number;
  severity: 'critical' | 'warning' | 'suggestion' | 'error' | 'info';
  category?: string;
  message: string;
  suggestion?: string;
  rule?: string;
}

export interface ReviewResponse {
  overallAssessment: string;
  issues: ReviewIssue[];
  summary: string;
}

export interface ReviewResult {
  issues: ReviewIssue[];
  summary: string;
  overallRating: 'excellent' | 'good' | 'needs_improvement' | 'poor';
  metrics: {
    readability: number;
    maintainability: number;
    performance: number;
    security: number;
  };
}

export interface CodeFile {
  id: number;
  userId: number;
  folderId?: number;
  name: string;
  language: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface Folder {
  id: number;
  userId: number;
  parentId?: number;
  name: string;
  createdAt: string;
}

export interface Repo {
  id: number;
  name: string;
  full_name: string;
  description?: string;
  html_url: string;
  language?: string;
  stargazers_count: number;
  forks_count: number;
  updated_at: string;
}

export interface GithubBranch {
  name: string;
  'default': boolean;
}

export interface GithubPR {
  number: number;
  title: string;
  state: string;
  html_url: string;
  headBranch: string;
  baseBranch: string;
  body: string;
}

export interface PRFileReview {
  filename: string;
  review: ReviewDTO;
}

export interface ApiResponse<T> {
  data: T;
  message?: string;
  success: boolean;
}

/**
 * ReviewDTO represents the data transfer object for a code review.
 * Matches the backend Review entity structure.
 */
export interface ReviewDTO {
  id: number;
  language: string;
  sourceType: string;
  codeInput: string;
  reviewResult?: ReviewResponse;
  overallRating?: string;
  criticalCount: number;
  warningCount: number;
  suggestionCount: number;
  createdAt: string;
}

// --- Multi-file / Codebase Review interfaces ---

/** Request body for codebase-level review (list of stored file IDs). */
export interface CodebaseReviewRequest {
  fileIds: number[];
}

/** Per-file breakdown returned by codebase analysis (from backend CodebaseReviewResponse.FileBreakdown). */
export interface FileBreakdown {
  fileId: number;
  filePath: string;
  language: string;
  issueCount: number;
  assessment: string;
}

/** Response returned by the codebase analysis endpoint.
 *  Matches the backend CodebaseReviewResponse which extends ReviewResponse.
 */
export interface CodebaseReviewResponse {
  overallAssessment: string;
  summary: string;
  issues: ReviewIssue[];
  fileBreakdowns: FileBreakdown[];
  totalFiles: number;
}

/** Represents a code file with its content for client-side analysis. */
export interface CodeFileContent {
  name: string;
  content: string;
  language: string;
}

/** Links a review to a specific file. */
export interface ReviewFile {
  id: number;
  reviewId: number;
  fileId: number;
  filePath: string;
}

/** A named group of files for codebase review. */
export interface CodebaseGroup {
  id: number;
  userId: number;
  name: string;
  description?: string;
  files: CodeFile[];
}

/** A single item in the file tree returned by getFileTree(). */
export interface FileTreeItem {
  id?: number;
  name: string;
  type: 'folder' | 'file';
  language?: string;
  children?: FileTreeItem[];
  item?: CodeFile;
}

/** Review mode used by the review page. */
export type ReviewMode = 'paste' | 'select-files';
