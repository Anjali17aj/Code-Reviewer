import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { CodeFile, Folder } from '../../shared/models/review.model';

@Injectable({ providedIn: 'root' })
export class FilesService {
  private readonly FILES_ENDPOINT = '/api/files';

  constructor(private apiService: ApiService) {}

  // Folder operations
  createFolder(name: string, parentId?: number): Observable<Folder> {
    return this.apiService.post<Folder>(`${this.FILES_ENDPOINT}/folders`, { name, parentId });
  }

  getFolders(parentId?: number): Observable<Folder[]> {
    let params = new HttpParams();
    if (parentId) {
      params = params.set('parentId', parentId.toString());
    }
    return this.apiService.get<Folder[]>(`${this.FILES_ENDPOINT}/folders`, params);
  }

  renameFolder(id: number, name: string): Observable<Folder> {
    return this.apiService.put<Folder>(`${this.FILES_ENDPOINT}/folders/${id}`, { name });
  }

  deleteFolder(id: number): Observable<void> {
    return this.apiService.delete<void>(`${this.FILES_ENDPOINT}/folders/${id}`);
  }

  // File operations
  createFile(name: string, language: string, content: string, folderId?: number): Observable<CodeFile> {
    return this.apiService.post<CodeFile>(this.FILES_ENDPOINT, { name, language, content, folderId });
  }

  uploadFile(file: File, folderId?: number): Observable<CodeFile> {
    const formData = new FormData();
    formData.append('file', file);
    if (folderId) formData.append('folderId', folderId.toString());
    return this.apiService.post<CodeFile>(`${this.FILES_ENDPOINT}/upload`, formData);
  }

  getFiles(folderId?: number): Observable<CodeFile[]> {
    let params = new HttpParams();
    if (folderId) {
      params = params.set('folderId', folderId.toString());
    }
    return this.apiService.get<CodeFile[]>(this.FILES_ENDPOINT, params);
  }

  getFileTree(): Observable<any[]> {
    return this.apiService.get<any[]>(`${this.FILES_ENDPOINT}/tree`);
  }

  getFile(id: number): Observable<CodeFile> {
    return this.apiService.get<CodeFile>(`${this.FILES_ENDPOINT}/${id}`);
  }

  updateFile(id: number, name?: string, content?: string): Observable<CodeFile> {
    return this.apiService.put<CodeFile>(`${this.FILES_ENDPOINT}/${id}`, { name, content });
  }

  deleteFile(id: number): Observable<void> {
    return this.apiService.delete<void>(`${this.FILES_ENDPOINT}/${id}`);
  }

  moveFile(id: number, targetFolderId: number): Observable<CodeFile> {
    return this.apiService.put<CodeFile>(`${this.FILES_ENDPOINT}/${id}/move`, { targetFolderId });
  }
}
