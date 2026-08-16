import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { Repo, GithubBranch, GithubPR } from '../../shared/models/review.model';

@Injectable({ providedIn: 'root' })
export class GithubService {
  private readonly GITHUB_ENDPOINT = '/api/github';

  constructor(private apiService: ApiService) {}

  getAuthUrl(): Observable<{ url: string }> {
    return this.apiService.get<{ url: string }>(`${this.GITHUB_ENDPOINT}/auth-url`);
  }

  handleCallback(code: string): Observable<{ message: string }> {
    const params = new HttpParams().set('code', code);
    return this.apiService.get<{ message: string }>(`${this.GITHUB_ENDPOINT}/callback`, params);
  }

  getRepos(): Observable<Repo[]> {
    return this.apiService.get<Repo[]>(`${this.GITHUB_ENDPOINT}/repos`);
  }

  getBranches(owner: string, repo: string): Observable<GithubBranch[]> {
    return this.apiService.get<GithubBranch[]>(
      `${this.GITHUB_ENDPOINT}/repos/${owner}/${repo}/branches`
    );
  }

  getPullRequests(owner: string, repo: string): Observable<GithubPR[]> {
    return this.apiService.get<GithubPR[]>(
      `${this.GITHUB_ENDPOINT}/repos/${owner}/${repo}/pulls`
    );
  }

  getPRDiff(owner: string, repo: string, pr: number): Observable<string> {
    return this.apiService.get<string>(
      `${this.GITHUB_ENDPOINT}/repos/${owner}/${repo}/pulls/${pr}/diff`
    );
  }

  reviewPR(owner: string, repo: string, prNumber: number): Observable<any> {
    return this.apiService.post<any>(`${this.GITHUB_ENDPOINT}/review-pr`, {
      owner,
      repo,
      prNumber
    });
  }
}
