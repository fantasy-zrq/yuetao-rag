# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YuetaoRag is an enterprise RAG (Retrieval Augmented Generation) system with a Java backend and Vue frontend. The backend handles knowledge base management, document ingestion, chunking, and vector embedding via pgvector. The retrieval/generation pipeline is partially scaffolded — the ingestion side is fully implemented and tested.

## Build & Run Commands

All backend commands run from `yuetaoRagBackend/`. Use the local Maven repo flag to avoid polluting the system cache.

```bash
# Clean + compile
./mvnw -q -Dmaven.repo.local=/tmp/yuetao-m2 clean
./mvnw -q -Dmaven.repo.local=/tmp/yuetao-m2 -DskipTests compile

# Run all tests
./mvnw -q -Dmaven.repo.local=/tmp/yuetao-m2 test

# Run focused test classes (comma-separated)
./mvnw -q -Dmaven.repo.local=/tmp/yuetao-m2 -Dtest=KnowledgeBaseServiceImplTests,KnowledgeDocumentServiceImplTests test

# Real end-to-end split flow (requires live infra: PostgreSQL, RocketMQ, RustFS)
./mvnw -q -Dmaven.repo.local=/tmp/yuetao-m2 -Dtest=KnowledgeDocumentSplitRealFlowTests test

# Start backend
./mvnw spring-boot:run

# Package
./mvnw package
```

Frontend commands run from `yuetaoRagFront/` (currently a Vue 3 + Vite scaffold):

```bash
npm install
npm run dev
npm run build
```

## Architecture

### Backend (Spring Boot 3 / Java 17)

The backend is a standard layered Spring Boot app under package `com.rag.cn.yuetaoragbackend`:

- **controller/** — REST endpoints, all responses wrapped in `Result<T>` via `Results.success()`/`Results.failure()`
- **service/** — business logic interfaces; **service/impl/** — implementations
- **dao/entity/** — MyBatis-Plus entity classes extending `BaseDO` (auto-filled `id`, `createTime`, `updateTime`, `deleteFlag`)
- **dao/mapper/** — MyBatis-Plus mapper interfaces
- **config/** — AI model, vector store, and RustFS configuration; **config/properties/** — `@ConfigurationProperties` binding classes under prefix `app.*`
- **config/enums/** — business enums (parse status, chunk mode, visibility scope, etc.)
- **mq/** — RocketMQ producer/consumer/event classes for async document processing
- **framework/** — cross-cutting infrastructure (exception hierarchy, error codes, global exception handler, user context, idempotency, web/database config)
- **service/file/** — `FileService` abstraction over RustFS (S3-compatible object storage)

### Key Infra Dependencies

| Component | Purpose |
|-----------|---------|
| PostgreSQL + pgvector | Relational storage + vector similarity search |
| RustFS (S3-compatible) | Document file storage; accessed via AWS S3 SDK |
| RocketMQ | Transaction messages for async split/vectorization |
| Redis + Redisson | Session/cache (scaffolded) |
| Spring AI (openai module) | Embedding model integration via OpenAI-compatible API |
| MyBatis-Plus | ORM with logical deletion, pagination, JSON type handler |
| Sa-Token | Auth framework (scaffolded) |
| Apache Tika | Document parsing (PDF, DOCX, MD, TXT) |

### Document Ingestion Flow (the core implemented pipeline)

1. **Upload** — file uploaded via multipart to `KnowledgeDocumentController`, stored in RustFS bucket (bucket = KB's `collectionName`), metadata persisted to `t_knowledge_document` with `parseStatus=PENDING`
2. **Split trigger** — `POST /knowledge-documents/split` sends a RocketMQ **transaction message**; local transaction sets `parseStatus=PROCESSING`
3. **Async consume** — `KnowledgeDocumentSplitConsumer` receives the message, calls `KnowledgeDocumentSplitServiceImpl.processSplit(documentId)`
4. **Parse & chunk** — Tika parses file bytes → text; chunked via FIXED (char count) or STRUCTURE_AWARE (markdown headings → paragraphs → sentences → fixed fallback)
5. **Persist** — chunks batch-inserted to `t_chunk` (batch size 15 via JdbcTemplate); vector embeddings written to `t_chunk_vector` via `PgVectorStore.add()`
6. **Status update** — `parseStatus=SUCCESS` on completion, `FAILED` on error

Rebuild split (`dispatchRebuildSplit`) cleans old chunks/vectors before re-processing. First-time split (`dispatchSplit`) does not.

### AI Model Configuration

The project does **not** use Spring AI's auto-configured model starters. Instead, `AiModelConfiguration` manually builds `OpenAiEmbeddingModel` from custom `app.ai.providers.*` config, supporting multiple OpenAI-compatible providers (Bailian, SiliconFlow). Provider API keys come from environment variables (`ALIYUN_ACCESS_KEY_ID`, `SILICONFLOW_API_KEY`).

### Vector Store

`ChunkVectorStoreConfiguration` creates a `PgVectorStore` bound to custom table `t_chunk_vector` with:
- `id varchar(20)` (snowflake ID), `content text`, `metadata jsonb`, `embedding vector`
- Metadata contains: `document_id`, `chunk_no`, `collection_name`
- Indexes: GIN on metadata, BTREE on document_id+collection_name, HNSW on embedding (cosine)
- Schema init and validation disabled — managed manually via `db/schema/yuetaorag.sql`

### Conventions

- **Response format**: all endpoints return `Result<T>` with success code `"0"`
- **Exception hierarchy**: `ClientException` / `ServiceException` / `RemoteException` extending `AbstractException`, handled by `GlobalExceptionHandler`
- **Error codes**: Alibaba-style A/B/C classification in `BaseErrorCode`
- **User context**: `UserContext` uses `TransmittableThreadLocal`; service methods call `UserContext.requireUser()` for current user
- **Logical deletion**: most entities use `deleteFlag` field
- **MQ serialization**: `KnowledgeDocumentSplitConsumer` uses FastJson2 (not global ObjectMapper)
- **Test naming**: classes end with `*Tests`; mirror production package structure

### Server

- Port: `9596`
- Context path: `/yuetaoRag`
- Active profile: `dev`

## Troubleshooting

If you see Spring context errors or stale class conflicts after package moves, run:
```bash
./mvnw -q -Dmaven.repo.local=/tmp/yuetao-m2 clean
```
Then reimport in IDE and rebuild.
