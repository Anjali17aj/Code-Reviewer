# Layer 2 TODO: File Management System

**Received from:** @cto
**Risk Level:** Medium
**Security Required:** After Dev (data access patterns, user isolation)

## Tasks

- [ ] **Task 1: Update Backend Repositories**
  - Update CodeFileRepository to add missing methods and adjust sort orders
  - Update FolderRepository to adjust sort orders to match FileService requirements
  - Add deleteByUserIdAndFolderId method to CodeFileRepository
  - Acceptance: All repository methods used by FileService are present
  - Security: User isolation (all queries filter by userId)
  - Dependencies: None

- [ ] **Task 2: Create Backend DTOs**
  - Create CodeFileDTO.java in backend/src/main/java/com/codereview/dto/
  - Create FolderDTO.java in backend/src/main/java/com/codereview/dto/
  - Acceptance: DTOs match entity structure and Lombok annotations
  - Security: No sensitive data exposure
  - Dependencies: None

- [ ] **Task 3: Create FileService**
  - Create FileService.java in backend/src/main/java/com/codereview/service/
  - Implement folder and file operations as specified
  - Acceptance: All methods work with repository layer
  - Security: User isolation enforced in all operations
  - Dependencies: Task 1, Task 2

- [ ] **Task 4: Create FileController**
  - Create FileController.java in backend/src/main/java/com/codereview/controller/
  - Implement REST endpoints for file/folder operations
  - Acceptance: All endpoints functional with proper authentication
  - Security: User extraction from JWT, proper authorization
  - Dependencies: Task 3

- [ ] **Task 5: Create Frontend FilesService**
  - Create files.service.ts in frontend/src/app/features/files/
  - Implement API calls for all file/folder operations
  - Acceptance: Service methods match backend endpoints
  - Security: Proper authentication headers via ApiService
  - Dependencies: None

- [ ] **Task 6: Update Frontend FilesComponent**
  - Replace existing files.component.ts with full file explorer
  - Implement file tree view, editor integration, and review functionality
  - Acceptance: File explorer works with backend API
  - Security: User can only access their own files
  - Dependencies: Task 5

- [ ] **Task 7: Verify Build**
  - Backend: Run `cd backend && mvn compile` to ensure compilation
  - Frontend: Run `cd frontend && npx ng build --configuration=development` to ensure build
  - Acceptance: Both backend and frontend compile without errors
  - Security: No compilation errors that could hide security issues
  - Dependencies: All previous tasks

## Notes
- The CTO has provided full implementations for most tasks
- The main work is implementing these in the existing codebase
- User isolation is critical - all operations must filter by userId
- The file tree operation has N+1 query concern but is acceptable for MVP
- Language detection is basic but can be enhanced later
- The frontend uses Monaco Editor integration from existing review component