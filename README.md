# Code Review Assistant

AI-powered code review tool with GitHub integration.

## Features

- **Paste & Review**: Paste code snippets and get AI-powered feedback
- **File Management**: Create, upload, and organize code files in folders
- **GitHub Integration**: Connect GitHub account, review PRs directly
- **Review History**: Track all past reviews with filters and pagination
- **Multi-language Support**: Java, Python, JavaScript, TypeScript, C++, Go, and more

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Angular 17, TypeScript, Tailwind CSS, Monaco Editor |
| Backend | Java 17, Spring Boot 3.x, Spring Security |
| Database | MySQL 8 |
| Cache | Redis |
| AI | OpenAI GPT-4 |
| CI/CD | GitLab CI |

## Getting Started

### Prerequisites

- Docker & Docker Compose
- OpenAI API key
- GitHub OAuth App credentials (optional)

### Quick Start

1. Clone the repository
2. Copy `.env.example` to `.env` and fill in your API keys
3. Run with Docker Compose:

```bash
docker compose up -d
```

4. Access the app at http://localhost:4200

### Development Setup

#### Backend

```bash
cd backend
mvn spring-boot:run
```

#### Frontend

```bash
cd frontend
npm install
ng serve
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/auth/signup | Create account |
| POST | /api/auth/login | Login |
| POST | /api/reviews/analyze | Analyze code |
| GET | /api/reviews | Get review history |
| POST | /api/files | Create file |
| POST | /api/files/upload | Upload file |
| GET | /api/files/tree | Get file tree |
| GET | /api/github/repos | List GitHub repos |
| POST | /api/github/review-pr | Review PR |

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| DATABASE_URL | MySQL connection URL | Yes |
| JWT_SECRET | Secret for JWT tokens | Yes |
| OPENAI_API_KEY | OpenAI API key | Yes |
| GITHUB_CLIENT_ID | GitHub OAuth client ID | No |
| GITHUB_CLIENT_SECRET | GitHub OAuth client secret | No |
| REDIS_HOST | Redis host | No |
| REDIS_PORT | Redis port | No |

## License

MIT
