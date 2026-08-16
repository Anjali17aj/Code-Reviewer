import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import {
  ReviewDTO,
  CodebaseReviewRequest,
  CodebaseReviewResponse,
  CodeFileContent,
  ReviewFile
} from '../../shared/models/review.model';

@Injectable({
  providedIn: 'root'
})
export class ReviewService {
  private readonly REVIEWS_ENDPOINT = '/api/reviews';

  constructor(private apiService: ApiService) {}

  /**
   * Analyze code using AI-powered code review.
   * @param code - The source code to analyze
   * @param language - The programming language of the code
   * @returns Observable of ReviewDTO containing the review results
   */
  analyzeCode(code: string, language: string): Observable<ReviewDTO> {
    const body = { code, language };
    return this.apiService.post<ReviewDTO>(`${this.REVIEWS_ENDPOINT}/analyze`, body);
  }

  /**
   * Analyze multiple files as a codebase (server-side stored files).
   * @param fileIds - Array of file IDs to include in the review
   * @returns Observable of CodebaseReviewResponse
   */
  analyzeCodebase(fileIds: number[]): Observable<CodebaseReviewResponse> {
    const body: CodebaseReviewRequest = { fileIds };
    return this.apiService.post<CodebaseReviewResponse>(
      `${this.REVIEWS_ENDPOINT}/analyze-codebase`,
      body
    );
  }

  /**
   * Analyze multiple files sent from the client (inline content).
   * @param files - Array of CodeFileContent objects
   * @returns Observable of ReviewDTO (backend returns ReviewDTO for this endpoint)
   */
  analyzeFiles(files: CodeFileContent[]): Observable<ReviewDTO> {
    return this.apiService.post<ReviewDTO>(
      `${this.REVIEWS_ENDPOINT}/analyze-files`,
      { files }
    );
  }

  /**
   * Get the file associations for a review.
   * @param reviewId - The review ID
   * @returns Observable of ReviewFile array
   */
  getReviewFiles(reviewId: number): Observable<ReviewFile[]> {
    return this.apiService.get<ReviewFile[]>(
      `${this.REVIEWS_ENDPOINT}/${reviewId}/files`
    );
  }

  /**
   * Get a paginated list of reviews with optional language filter.
   * @param page - Page number (0-indexed)
   * @param size - Number of items per page
   * @param language - Optional language filter
   * @returns Observable of ReviewDTO array
   */
  getReviews(page: number, size: number, language?: string): Observable<ReviewDTO[]> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (language) {
      params = params.set('language', language);
    }

    return this.apiService.get<ReviewDTO[]>(this.REVIEWS_ENDPOINT, params);
  }

  /**
   * Get a single review by ID.
   * @param id - The review ID
   * @returns Observable of ReviewDTO
   */
  getReview(id: number): Observable<ReviewDTO> {
    return this.apiService.get<ReviewDTO>(`${this.REVIEWS_ENDPOINT}/${id}`);
  }

  /**
   * Delete a review by ID.
   * @param id - The review ID to delete
   * @returns Observable of void
   */
  deleteReview(id: number): Observable<void> {
    return this.apiService.delete<void>(`${this.REVIEWS_ENDPOINT}/${id}`);
  }
}
