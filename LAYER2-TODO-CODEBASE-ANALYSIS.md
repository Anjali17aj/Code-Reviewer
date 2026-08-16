# Layer 2 TODO: Multi-File / Codebase Analysis Feature

**Received from:** @cto
**Risk Level:** Medium
**Security Required:** After Dev (post-implementation audit)
**Priority:** Critical - most requested missing feature

---

## Executive Summary

The current system supports **single-file review only** (paste code or select one file). This plan implements **multi-file/codebase analysis** -- the ability to select multiple files and have the AI analyze them together, understanding cross-file dependencies, imports, shared interfaces, and architectural patterns.

**Key Design Decisions:**
- Multi-file analysis sends all selected files as a structured prompt to the LLM, preserving file boundaries
- The LLM system prompt is extended with a codebase-aware variant
- A new `ReviewFile` junction table tracks which files participated in each review
- Frontend gets multi-select checkboxes in the file explorer and a new "Analyze Codebase" button
- The existing single-file flow is **completely preserved** -- this is purely additive

---

## Phase 1: Database Layer (Entities + Repositories)

### Task 1.1: Create `ReviewFile` Junction Entity
**File:** `backend/src/main/java/com/codereview/entity/ReviewFile.java` (NEW)
**Acceptance Criteria:**
- [ ] Entity maps to `review_files` table with `id`, `reviewId`, `fileId`, `fileName`, `language`, `createdAt`
- [ ] Lombok annotations generate getters/setters/builder
- [ ] Hibernate auto-creates table on startup (`ddl-auto=update`)

### Task 1.2: Create `ReviewFileRepository`
**File:** `backend/src/main/java/com/codereview/repository/ReviewFileRepository.java` (NEW)
**Acceptance Criteria:**
- [ ] `findByReviewId(Long)` returns all files for a review
- [ ] `deleteByReviewId(Long)` cleans up when review is deleted
- [ ] `findByFileId(Long)` finds all reviews that used a specific file

### Task 1.3: Create `CodebaseGroup` Entity
**File:** `backend/src/main/java/com/codereview/entity/CodebaseGroup.java` (NEW)
**Acceptance Criteria:**
- [ ] Maps to `codebase_groups` with `id`, `userId`, `name`, `description`, timestamps
- [ ] User isolation enforced (all queries filter by userId)

### Task 1.4: Create `CodebaseGroupFile` Junction Entity
**File:** `backend/src/main/java/com/codereview/entity/CodebaseGroupFile.java` (NEW)
**Acceptance Criteria:**
- [ ] Maps to `codebase_group_files` with `id`, `groupId`, `fileId`, `filePath`, `createdAt`
- [ ] Stores `filePath` for prompt context

### Task 1.5: Create `CodebaseGroupRepository`
**File:** `backend/src/main/java/com/codereview/repository/CodebaseGroupRepository.java` (NEW)

### Task 1.6: Create `CodebaseGroupFileRepository`
**File:** `backend/src/main/java/com/codereview/repository/CodebaseGroupFileRepository.java` (NEW)

### Task 1.7: Update `ReviewRepository` -- Add Codebase Queries
**File:** `backend/src/main/java/com/codereview/repository/ReviewRepository.java` (MODIFY)
**Acceptance Criteria:**
- [ ] Add `findByUserIdAndSourceTypeOrderByCreatedAtDesc(Long, String, Pageable)`
- [ ] Can filter reviews by `sourceType` ("codebase" vs "paste")

---

## Phase 2: DTOs

### Task 2.1: Create `CodebaseReviewRequest` DTO
**File:** `backend/src/main/java/com/codereview/dto/CodebaseReviewRequest.java` (NEW)
**Acceptance Criteria:**
- [ ] Requires 2-50 file IDs (`@Size(min=2, max=50)`)
- [ ] Optional `groupId` and `focus` fields

### Task 2.2: Create `CodebaseReviewResponse` DTO
**File:** `backend/src/main/java/com/codereview/dto/CodebaseReviewResponse.java` (NEW)
**Acceptance Criteria:**
- [ ] Contains `crossFileIssues`, `fileIssues`, `fileSummaries`, `architectureSummary`
- [ ] Backward-compatible: existing single-file response unchanged

### Task 2.3: Create Supporting DTOs
**Files:** `BulkUploadResponse.java`, `CodebaseGroupDTO.java` (NEW)

### Task 2.4: Update `ReviewDTO` -- Add File List
**File:** `backend/src/main/java/com/codereview/dto/ReviewDTO.java` (MODIFY)
**Acceptance Criteria:**
- [ ] Add `analyzedFiles` list (null for single-file reviews)

---

## Phase 3: Backend Services

### Task 3.1: Extend `LLMService` -- Add Codebase Analysis
**File:** `backend/src/main/java/com/codereview/service/LLMService.java` (MODIFY)
**Acceptance Criteria:**
- [ ] Add `CODEBASE_SYSTEM_PROMPT` for multi-file analysis
- [ ] Add `analyzeCodebase(List<CodebaseFileInput>, String focus)` method
- [ ] Total code length validated (max 100K chars across all files)
- [ ] Each file delimited with `<<<FILE: name (lang)>>>` markers
- [ ] Parse `CodebaseReviewResponse` with cross-file issues
- [ ] Existing `analyzeCode()` method unchanged
- [ ] Language whitelist enforced per-file
- [ ] Prompt injection defense maintained

### Task 3.2: Extend `ReviewService` -- Add Codebase Orchestration
**File:** `backend/src/main/java/com/codereview/service/ReviewService.java` (MODIFY)
**Acceptance Criteria:**
- [ ] Add `analyzeCodebaseAndSave(userId, fileIds, groupId, focus)`
- [ ] Saves review with `sourceType="codebase"`
- [ ] Creates `ReviewFile` junction records
- [ ] Links to group if `groupId` provided
- [ ] Rate limiting applied

### Task 3.3: Extend `FileService` -- Add Bulk Upload + Batch Load
**File:** `backend/src/main/java/com/codereview/service/FileService.java` (MODIFY)
**Acceptance Criteria:**
- [ ] Add `uploadMultipleFiles(userId, files, folderId)` with partial success
- [ ] Add `getFilesByIds(userId, fileIds)` for batch loading with ownership check
- [ ] Max 50 files per upload

### Task 3.4: Extend `ReviewHistoryService`
**File:** `backend/src/main/java/com/codereview/service/ReviewHistoryService.java` (MODIFY)
**Acceptance Criteria:**
- [ ] Add `getCodebaseReviews(userId, page, size)` filtered by sourceType

---

## Phase 4: Backend Controllers

### Task 4.1: Add Codebase Analysis Endpoint
**File:** `backend/src/main/java/com/codereview/controller/ReviewController.java` (MODIFY)
**Endpoint:** `POST /api/reviews/analyze-codebase`
**Acceptance Criteria:**
- [ ] Requires JWT authentication
- [ ] Rate limiting applied
- [ ] Input validation (2-50 files)
- [ ] Returns `CodebaseReviewResponse`

### Task 4.2: Add Bulk Upload Endpoint
**File:** `backend/src/main/java/com/codereview/controller/FileController.java` (MODIFY)
**Endpoint:** `POST /api/files/upload-bulk`
**Acceptance Criteria:**
- [ ] Accepts multiple MultipartFile
- [ ] Individual file validation
- [ ] Returns partial results (successes + errors)

### Task 4.3: Add Codebase Group Endpoints
**File:** `backend/src/main/java/com/codereview/controller/CodebaseGroupController.java` (NEW)
**Endpoints:** Full CRUD + file management under `/api/codebase-groups`
**Acceptance Criteria:**
- [ ] Create, read, update, delete groups
- [ ] Add/remove files from groups
- [ ] User ownership enforced

### Task 4.4: Add History Endpoint for Codebase Reviews
**File:** `backend/src/main/java/com/codereview/controller/HistoryController.java` (MODIFY)
**Endpoint:** `GET /api/history/codebase`

---

## Phase 5: Backend Tests

### Task 5.1: Unit Tests for `LLMService.analyzeCodebase()`
**File:** `backend/src/test/java/com/codereview/service/LLMServiceTest.java` (MODIFY)
- [ ] Happy path with multiple files
- [ ] Reject <2 files
- [ ] Reject >50 files
- [ ] Reject >100K total chars
- [ ] Reject unsupported language
- [ ] Handle LLM unavailable
- [ ] Parse cross-file issues correctly

### Task 5.2: Unit Tests for `ReviewService.analyzeCodebaseAndSave()`
**File:** `backend/src/test/java/com/codereview/service/ReviewServiceTest.java` (MODIFY)
- [ ] Happy path saves review + junction records
- [ ] File not found throws error
- [ ] Ownership verification

### Task 5.3: Unit Tests for `FileService` bulk operations
**File:** `backend/src/test/java/com/codereview/service/FileServiceTest.java` (NEW or MODIFY)
- [ ] Bulk upload with mixed valid/invalid files
- [ ] Batch load with ownership check
- [ ] Partial success reporting

### Task 5.4: Integration Tests for new endpoints
**File:** `backend/src/test/java/com/codereview/controller/ReviewControllerTest.java` (MODIFY)
- [ ] `/analyze-codebase` endpoint integration test
- [ ] `/upload-bulk` endpoint integration test
- [ ] Codebase group CRUD tests

---

## Phase 6: Frontend -- File Management

### Task 6.1: Update TypeScript Models
**File:** `frontend/src/app/shared/models/review.model.ts` (MODIFY)
**Acceptance Criteria:**
- [ ] Add `CodebaseReviewRequest`, `CodebaseReviewResponse`, `CrossFileIssue`, `FileSummary`
- [ ] Add `CodebaseGroup`, `CodebaseGroupFile` interfaces
- [ ] Add `BulkUploadResponse` interface
- [ ] Update `ReviewDTO` with optional `analyzedFiles`

### Task 6.2: Update `FilesService` -- Add API Methods
**File:** `frontend/src/app/features/files/files.service.ts` (MODIFY)
**Acceptance Criteria:**
- [ ] Add `analyzeCodebase(fileIds, groupId?, focus?)` method
- [ ] Add `uploadBulkFiles(files, folderId?)` method
- [ ] Add `getCodebaseGroups()`, `createCodebaseGroup()`, `deleteCodebaseGroup()` methods
- [ ] Add `addFilesToGroup()`, `removeFileFromGroup()` methods

### Task 6.3: Update File Explorer -- Multi-Select UI
**File:** `frontend/src/app/features/files/files.component.ts` (MODIFY)
**Acceptance Criteria:**
- [ ] Add checkboxes to each file item in the tree
- [ ] Track selected files in a `Set<number>` (selectedFileIds)
- [ ] "Select All" / "Deselect All" toggle in file explorer header
- [ ] "Analyze Selected (N)" button appears when 2+ files selected
- [ ] Button disabled when <2 files selected
- [ ] Loading state during codebase analysis
- [ ] File count badge on the button

### Task 6.4: Add Codebase Analysis Results Component
**File:** `frontend/src/app/features/files/codebase-results.component.ts` (NEW)
**Acceptance Criteria:**
- [ ] Displays `CodebaseReviewResponse` with tabs/sections:
  - Architecture Summary (top)
  - Cross-File Issues (highlighted, shows file list per issue)
  - Per-File Issues (grouped by file)
  - File Summaries (grid of cards)
- [ ] Reuses existing severity styling from `ResultsComponent`
- [ ] Shows "N files analyzed" header
- [ ] Expandable/collapsible sections

### Task 6.5: Add Bulk Upload UI
**File:** `frontend/src/app/features/files/files.component.ts` (MODIFY)
**Acceptance Criteria:**
- [ ] Add "Upload Multiple" button in explorer header
- [ ] Opens native file picker with `multiple` attribute
- [ ] Accepts `.java,.py,.js,.ts,.cpp,.c,.cs,.go,.rb,.rs`
- [ ] Shows upload progress/result toast (N succeeded, M failed)
- [ ] Refreshes file tree after upload

### Task 6.6: Add Codebase Group Management UI
**File:** `frontend/src/app/features/files/files.component.ts` (MODIFY)
**Acceptance Criteria:**
- [ ] "Groups" section in file explorer sidebar (below file tree)
- [ ] List of saved groups with name
- [ ] Click group -> selects all its files
- [ ] "Create Group from Selection" button
- [ ] Delete group button (with confirmation)

---

## Phase 7: Frontend -- History & Review Updates

### Task 7.1: Update `HistoryService` -- Codebase Reviews
**File:** `frontend/src/app/features/history/history.service.ts` (MODIFY)
**Acceptance Criteria:**
- [ ] Add `getCodebaseReviews(page, size)` method

### Task 7.2: Update `HistoryComponent` -- Show Codebase Reviews
**File:** `frontend/src/app/features/history/history.component.ts` (MODIFY)
**Acceptance Criteria:**
- [ ] Add tab/toggle: "Single File" vs "Codebase" reviews
- [ ] Codebase reviews show file count and linked files
- [ ] Click -> navigates to codebase results view

### Task 7.3: Update `ReviewService` -- Add Codebase Method
**File:** `frontend/src/app/features/review/review.service.ts` (MODIFY)
**Acceptance Criteria:**
- [ ] Add `analyzeCodebase(request)` calling `/api/reviews/analyze-codebase`

### Task 7.4: Update `GithubComponent` -- Multi-File PR Review
**File:** `frontend/src/app/features/github/github.component.ts` (MODIFY)
**Acceptance Criteria:**
- [ ] PR review now uses codebase analysis for multi-file PRs
- [ ] Shows cross-file issues in PR review results

---

## Phase 8: Integration & Polish

### Task 8.1: End-to-End Flow Verification
**Acceptance Criteria:**
- [ ] Upload multiple files -> select them -> analyze -> see cross-file results
- [ ] Create group -> save selection -> recall group later -> analyze
- [ ] History shows codebase reviews with file list
- [ ] Single-file review still works identically
- [ ] GitHub PR review uses codebase analysis for multi-file PRs

### Task 8.2: Edge Cases
**Acceptance Criteria:**
- [ ] File deleted after being in a group -> graceful handling
- [ ] All files from same language -> still finds cross-file issues
- [ ] Files from different languages -> analysis still works
- [ ] Very large codebase (near 100K limit) -> clear error message
- [ ] Empty selection -> button disabled
- [ ] Exactly 2 files -> minimum accepted

### Task 8.3: Performance Considerations
**Acceptance Criteria:**
- [ ] Batch file loading uses single DB query (not N+1)
- [ ] LLM timeout extended for codebase analysis (120s vs 60s)
- [ ] Loading spinner shows file count during analysis

---

## Task Dependency Graph

```
Phase 1 (DB) ──────────────> Phase 2 (DTOs) ──> Phase 3 (Services) ──> Phase 4 (Controllers)
                                                                              │
                                                                              v
Phase 5 (Backend Tests) <─────────────────────────────────────────────────────┘
                                                                              │
                                                                              v
Phase 6 (Frontend Files) ──> Phase 7 (Frontend History) ──> Phase 8 (Integration)
```

**Parallel opportunities:**
- Phase 1.1-1.6 can be done in parallel (independent entities/repos)
- Phase 2.1-2.3 can be done in parallel (independent DTOs)
- Phase 5.1-5.3 can be done in parallel (independent test files)
- Phase 6.1 and 6.2 can be done in parallel
- Backend (Phases 1-5) and Frontend model updates (Phase 6.1) can overlap

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| LLM token limit exceeded with large codebase | High | Enforce 100K char total limit, show clear error |
| Cross-file analysis quality varies | Medium | Allow "focus" parameter to guide LLM |
| Performance with many files | Medium | Batch DB queries, async processing if needed |
| Frontend complexity increase | Low | Reuse existing components, clear state management |
| Breaking existing single-file flow | High | All changes additive, existing methods untouched |

---

## Files Summary

### New Files (Backend) -- 10
1. `entity/ReviewFile.java`
2. `entity/CodebaseGroup.java`
3. `entity/CodebaseGroupFile.java`
4. `repository/ReviewFileRepository.java`
5. `repository/CodebaseGroupRepository.java`
6. `repository/CodebaseGroupFileRepository.java`
7. `dto/CodebaseReviewRequest.java`
8. `dto/CodebaseReviewResponse.java` (+ CrossFileIssue, FileSummary, AnalyzedFile)
9. `dto/BulkUploadResponse.java`
10. `controller/CodebaseGroupController.java`

### Modified Files (Backend) -- 8
1. `repository/ReviewRepository.java`
2. `service/LLMService.java`
3. `service/ReviewService.java`
4. `service/FileService.java`
5. `service/ReviewHistoryService.java`
6. `controller/ReviewController.java`
7. `controller/FileController.java`
8. `controller/HistoryController.java`

### Modified Files (Frontend) -- 7
1. `shared/models/review.model.ts`
2. `features/files/files.service.ts`
3. `features/files/files.component.ts`
4. `features/review/review.service.ts`
5. `features/history/history.service.ts`
6. `features/history/history.component.ts`
7. `features/github/github.component.ts`

### New Files (Frontend) -- 1
1. `features/files/codebase-results.component.ts`

### Test Files -- 4
1. `test/.../service/LLMServiceTest.java` (modify)
2. `test/.../service/ReviewServiceTest.java` (modify)
3. `test/.../service/FileServiceTest.java` (new/modify)
4. `test/.../controller/ReviewControllerTest.java` (modify)

---

## Estimated Effort

| Phase | Tasks | Estimated Hours |
|-------|-------|-----------------|
| Phase 1: DB Layer | 7 | 3-4h |
| Phase 2: DTOs | 4 | 2-3h |
| Phase 3: Services | 4 | 6-8h |
| Phase 4: Controllers | 4 | 3-4h |
| Phase 5: Backend Tests | 4 | 4-5h |
| Phase 6: Frontend Files | 6 | 6-8h |
| Phase 7: Frontend History | 4 | 3-4h |
| Phase 8: Integration | 3 | 3-4h |
| **Total** | **36** | **30-40h** |
