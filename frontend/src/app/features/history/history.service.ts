import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ReviewDTO } from '../../shared/models/review.model';

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class HistoryService {
  private readonly HISTORY_ENDPOINT = '/api/history';

  constructor(private apiService: ApiService) {}

  /**
   * Get paginated review history with optional filters.
   * @param page - Page number (0-indexed)
   * @param size - Number of items per page
   * @param language - Optional language filter
   * @param assessment - Optional assessment filter (good, needs_improvement, poor)
   * @param startDate - Optional start date filter (ISO date string)
   * @param endDate - Optional end date filter (ISO date string)
   * @returns Observable of PaginatedResponse containing ReviewDTOs
   */
  getHistory(
    page: number,
    size: number,
    language?: string,
    assessment?: string,
    startDate?: string,
    endDate?: string
  ): Observable<PaginatedResponse<ReviewDTO>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (language) {
      params = params.set('language', language);
    }
    if (assessment) {
      params = params.set('assessment', assessment);
    }
    if (startDate) {
      params = params.set('startDate', startDate);
    }
    if (endDate) {
      params = params.set('endDate', endDate);
    }

    return this.apiService.get<PaginatedResponse<ReviewDTO>>(this.HISTORY_ENDPOINT, params);
  }

  /**
   * Get a single review detail by ID.
   * @param id - The review ID
   * @returns Observable of ReviewDTO
   */
  getReviewDetail(id: number): Observable<ReviewDTO> {
    return this.apiService.get<ReviewDTO>(`${this.HISTORY_ENDPOINT}/${id}`);
  }

  /**
   * Delete a review by ID.
   * @param id - The review ID to delete
   * @returns Observable of void
   */
  deleteReview(id: number): Observable<void> {
    return this.apiService.delete<void>(`${this.HISTORY_ENDPOINT}/${id}`);
  }
}
