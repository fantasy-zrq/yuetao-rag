DROP TABLE IF EXISTS "public"."t_chat_message";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_chat_message" (
    "id" int8 NOT NULL,
    "session_id" int8 NOT NULL,
    "user_id" int8 NOT NULL,
    "role" varchar(32) NOT NULL,
    "content" text NOT NULL,
    "content_type" varchar(32) NOT NULL DEFAULT 'TEXT'::character varying,
    "sequence_no" int4 NOT NULL,
    "trace_id" varchar(128),
    "model_provider" varchar(64),
    "model_name" varchar(128),
    "thinking_content" text,
    "thinking_duration_ms" int8,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_chat_message"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_chat_message"."session_id" IS '所属会话ID，逻辑关联 t_chat_session.id';
COMMENT ON COLUMN "public"."t_chat_message"."user_id" IS '消息归属用户ID，逻辑关联 t_user.id';
COMMENT ON COLUMN "public"."t_chat_message"."role" IS '消息角色：USER-用户消息，ASSISTANT-助手消息，SYSTEM-系统消息';
COMMENT ON COLUMN "public"."t_chat_message"."content" IS '消息正文内容';
COMMENT ON COLUMN "public"."t_chat_message"."content_type" IS '消息内容类型：TEXT-普通文本，THINKING-思考内容，EVENT-事件消息';
COMMENT ON COLUMN "public"."t_chat_message"."sequence_no" IS '消息在会话中的顺序号，同一会话内唯一';
COMMENT ON COLUMN "public"."t_chat_message"."trace_id" IS '本条消息对应的链路追踪ID';
COMMENT ON COLUMN "public"."t_chat_message"."model_provider" IS '生成该消息的模型提供商，例如 bailian、siliconflow';
COMMENT ON COLUMN "public"."t_chat_message"."model_name" IS '生成该消息的实际模型名称';
COMMENT ON COLUMN "public"."t_chat_message"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_chat_message"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_chat_message"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_chat_message" IS '会话消息表';


-- Indices
CREATE UNIQUE INDEX uk_t_chat_message_session_sequence ON public.t_chat_message USING btree (session_id, sequence_no);
CREATE INDEX idx_t_chat_message_session_time ON public.t_chat_message USING btree (session_id, create_time);
CREATE INDEX idx_t_chat_message_user_session_sequence ON public.t_chat_message USING btree (user_id, session_id, sequence_no);

DROP TABLE IF EXISTS "public"."t_chat_session";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_chat_session" (
    "id" int8 NOT NULL,
    "user_id" int8 NOT NULL,
    "title" varchar(256),
    "status" varchar(32) NOT NULL DEFAULT 'ACTIVE'::character varying,
    "last_active_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_chat_session"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_chat_session"."user_id" IS '会话所属用户ID，逻辑关联 t_user.id';
COMMENT ON COLUMN "public"."t_chat_session"."title" IS '会话标题，可由系统自动生成或用户自定义';
COMMENT ON COLUMN "public"."t_chat_session"."status" IS '会话状态：ACTIVE-活跃，CLOSED-关闭，ARCHIVED-归档';
COMMENT ON COLUMN "public"."t_chat_session"."last_active_at" IS '最近活跃时间';
COMMENT ON COLUMN "public"."t_chat_session"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_chat_session"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_chat_session"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_chat_session" IS '会话表';


-- Indices
CREATE INDEX idx_t_chat_session_user_status ON public.t_chat_session USING btree (user_id, status, delete_flag, last_active_at);

DROP TABLE IF EXISTS "public"."t_chat_session_summary";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_chat_session_summary" (
    "id" int8 NOT NULL,
    "session_id" int8 NOT NULL,
    "summary_text" text NOT NULL,
    "summary_version" int4 NOT NULL DEFAULT 1,
    "source_message_seq" int4 NOT NULL DEFAULT 0,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_chat_session_summary"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_chat_session_summary"."session_id" IS '所属会话ID，逻辑关联 t_chat_session.id';
COMMENT ON COLUMN "public"."t_chat_session_summary"."summary_text" IS '会话历史摘要内容';
COMMENT ON COLUMN "public"."t_chat_session_summary"."summary_version" IS '摘要版本号，同一会话内递增';
COMMENT ON COLUMN "public"."t_chat_session_summary"."source_message_seq" IS '本次摘要覆盖到的消息顺序号';
COMMENT ON COLUMN "public"."t_chat_session_summary"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_chat_session_summary"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_chat_session_summary"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_chat_session_summary" IS '会话摘要表';


-- Indices
CREATE UNIQUE INDEX uk_t_chat_session_summary_session_version ON public.t_chat_session_summary USING btree (session_id, summary_version);
CREATE INDEX idx_t_chat_session_summary_session_time ON public.t_chat_session_summary USING btree (session_id, create_time);

DROP TABLE IF EXISTS "public"."t_chunk";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_chunk" (
    "id" int8 NOT NULL,
    "knowledge_base_id" int8 NOT NULL,
    "document_id" int8 NOT NULL,
    "chunk_no" int4 NOT NULL,
    "chunk_hash" varchar(128) NOT NULL,
    "original_content" text NOT NULL,
    "edited_content" text,
    "effective_content" text NOT NULL,
    "token_count" int4 NOT NULL DEFAULT 0,
    "embedding_status" varchar(32) NOT NULL DEFAULT 'PENDING'::character varying,
    "enabled" bool NOT NULL DEFAULT true,
    "manual_edited" bool NOT NULL DEFAULT false,
    "created_by" int8 NOT NULL,
    "updated_by" int8 NOT NULL,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_chunk"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_chunk"."knowledge_base_id" IS '所属知识库ID，冗余字段，逻辑关联 t_knowledge_base.id';
COMMENT ON COLUMN "public"."t_chunk"."document_id" IS '所属文档ID，逻辑关联 t_knowledge_document.id';
COMMENT ON COLUMN "public"."t_chunk"."chunk_no" IS '切片顺序号，同一文档内唯一';
COMMENT ON COLUMN "public"."t_chunk"."chunk_hash" IS '切片内容哈希值，用于增量更新比对';
COMMENT ON COLUMN "public"."t_chunk"."original_content" IS '原始切片内容，由解析与切分流程生成';
COMMENT ON COLUMN "public"."t_chunk"."edited_content" IS '人工编辑后的切片内容，未编辑时为空';
COMMENT ON COLUMN "public"."t_chunk"."effective_content" IS '最终生效的切片内容，用于 embedding、检索和回答';
COMMENT ON COLUMN "public"."t_chunk"."token_count" IS '切片估算 token 数';
COMMENT ON COLUMN "public"."t_chunk"."embedding_status" IS '向量化状态：PENDING-待处理，PROCESSING-处理中，SUCCESS-成功，FAILED-失败';
COMMENT ON COLUMN "public"."t_chunk"."enabled" IS '切片是否启用：true-启用参与检索，false-停用不参与检索';
COMMENT ON COLUMN "public"."t_chunk"."manual_edited" IS '是否被人工编辑：true-已编辑，false-未编辑';
COMMENT ON COLUMN "public"."t_chunk"."created_by" IS '创建人用户ID，逻辑关联 t_user.id';
COMMENT ON COLUMN "public"."t_chunk"."updated_by" IS '最后更新人用户ID，逻辑关联 t_user.id';
COMMENT ON COLUMN "public"."t_chunk"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_chunk"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_chunk"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_chunk" IS '文档切片表';


-- Indices
CREATE UNIQUE INDEX uk_t_chunk_document_chunk_no ON public.t_chunk USING btree (document_id, chunk_no);
CREATE INDEX idx_t_chunk_lookup ON public.t_chunk USING btree (knowledge_base_id, document_id, enabled, delete_flag);
CREATE INDEX idx_t_chunk_hash ON public.t_chunk USING btree (document_id, chunk_hash);

DROP TABLE IF EXISTS "public"."t_chunk_department_auth";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_chunk_department_auth" (
    "id" int8 NOT NULL,
    "chunk_id" int8 NOT NULL,
    "department_id" int8 NOT NULL,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_chunk_department_auth"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_chunk_department_auth"."chunk_id" IS '切片ID，逻辑关联 t_chunk.id';
COMMENT ON COLUMN "public"."t_chunk_department_auth"."department_id" IS '可访问部门ID，逻辑关联 t_department.id';
COMMENT ON COLUMN "public"."t_chunk_department_auth"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_chunk_department_auth"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_chunk_department_auth"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_chunk_department_auth" IS '切片部门授权表';


-- Indices
CREATE UNIQUE INDEX uk_t_chunk_department_auth ON public.t_chunk_department_auth USING btree (chunk_id, department_id);
CREATE INDEX idx_t_chunk_auth_chunk_department ON public.t_chunk_department_auth USING btree (chunk_id, department_id);

DROP TABLE IF EXISTS "public"."t_chunk_vector";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_chunk_vector" (
    "id" varchar(20) NOT NULL,
    "content" text NOT NULL,
    "metadata" jsonb NOT NULL,
    "embedding" vector NOT NULL,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_chunk_vector"."id" IS '主键ID，使用雪花ID字符串，严格递增';
COMMENT ON COLUMN "public"."t_chunk_vector"."content" IS '切片文本内容';
COMMENT ON COLUMN "public"."t_chunk_vector"."metadata" IS '元数据，建议包含 document_id、chunk_no、collection_name';
COMMENT ON COLUMN "public"."t_chunk_vector"."embedding" IS 'pgvector 向量字段';


-- Comments
COMMENT ON TABLE "public"."t_chunk_vector" IS '文档切片向量表，基于 PgVectorStore 兼容结构';


-- Indices
CREATE INDEX idx_t_chunk_vector_metadata_gin ON public.t_chunk_vector USING gin (metadata);
CREATE INDEX idx_t_chunk_vector_meta_doc_coll ON public.t_chunk_vector USING btree (((metadata ->> 'document_id'::text)), ((metadata ->> 'collection_name'::text)));
CREATE INDEX idx_t_chunk_vector_embedding_hnsw ON public.t_chunk_vector USING hnsw (embedding vector_cosine_ops);

DROP TABLE IF EXISTS "public"."t_department";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_department" (
    "id" int8 NOT NULL,
    "dept_code" varchar(64) NOT NULL,
    "dept_name" varchar(128) NOT NULL,
    "status" varchar(32) NOT NULL DEFAULT 'ENABLED'::character varying,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_department"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_department"."dept_code" IS '部门编码，业务唯一';
COMMENT ON COLUMN "public"."t_department"."dept_name" IS '部门名称';
COMMENT ON COLUMN "public"."t_department"."status" IS '部门状态：ENABLED-启用，DISABLED-停用';
COMMENT ON COLUMN "public"."t_department"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_department"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_department"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_department" IS '部门表';


-- Indices
CREATE UNIQUE INDEX uk_t_department_dept_code ON public.t_department USING btree (dept_code);

DROP TABLE IF EXISTS "public"."t_document_chunk_log";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_document_chunk_log" (
    "id" int8 NOT NULL,
    "document_id" int8 NOT NULL,
    "knowledge_base_id" int8 NOT NULL,
    "operation_type" varchar(32) NOT NULL,
    "status" varchar(32) NOT NULL,
    "chunk_mode" varchar(32) NOT NULL,
    "chunk_config" jsonb NOT NULL,
    "chunk_count" int4 NOT NULL DEFAULT 0,
    "split_cost_millis" int8 NOT NULL DEFAULT 0,
    "vector_cost_millis" int8 NOT NULL DEFAULT 0,
    "total_cost_millis" int8 NOT NULL DEFAULT 0,
    "error_message" text,
    "start_time" timestamp NOT NULL,
    "end_time" timestamp,
    "created_by" int8,
    "updated_by" int8,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_document_chunk_log"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_document_chunk_log"."document_id" IS '文档ID，逻辑关联 t_knowledge_document.id';
COMMENT ON COLUMN "public"."t_document_chunk_log"."knowledge_base_id" IS '知识库ID，逻辑关联 t_knowledge_base.id';
COMMENT ON COLUMN "public"."t_document_chunk_log"."operation_type" IS '操作类型：SPLIT-首次分块，REBUILD-重建分块';
COMMENT ON COLUMN "public"."t_document_chunk_log"."status" IS '执行状态：PROCESSING-处理中，SUCCESS-成功，FAILED-失败，TIMEOUT-超时';
COMMENT ON COLUMN "public"."t_document_chunk_log"."chunk_mode" IS '本次执行使用的分块模式，例如 FIXED、STRUCTURE_AWARE';
COMMENT ON COLUMN "public"."t_document_chunk_log"."chunk_config" IS '本次执行使用的分块配置 JSON 快照';
COMMENT ON COLUMN "public"."t_document_chunk_log"."chunk_count" IS '本次执行生成的分块数量';
COMMENT ON COLUMN "public"."t_document_chunk_log"."split_cost_millis" IS '解析与分块耗时，单位毫秒';
COMMENT ON COLUMN "public"."t_document_chunk_log"."vector_cost_millis" IS '向量化写入耗时，单位毫秒';
COMMENT ON COLUMN "public"."t_document_chunk_log"."total_cost_millis" IS '本次执行总耗时，单位毫秒';
COMMENT ON COLUMN "public"."t_document_chunk_log"."error_message" IS '失败或超时时的错误信息';
COMMENT ON COLUMN "public"."t_document_chunk_log"."start_time" IS '本次分块执行开始时间';
COMMENT ON COLUMN "public"."t_document_chunk_log"."end_time" IS '本次分块执行结束时间，处理中为空';
COMMENT ON COLUMN "public"."t_document_chunk_log"."created_by" IS '创建人用户ID，系统触发时可为空';
COMMENT ON COLUMN "public"."t_document_chunk_log"."updated_by" IS '最后更新人用户ID，系统触发时可为空';
COMMENT ON COLUMN "public"."t_document_chunk_log"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_document_chunk_log"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_document_chunk_log"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_document_chunk_log" IS '文档分块执行日志表，每次分块或重建产生一条历史记录';


-- Indices
CREATE INDEX idx_t_document_chunk_log_document_time ON public.t_document_chunk_log USING btree (document_id, start_time DESC);
CREATE INDEX idx_t_document_chunk_log_kb_time ON public.t_document_chunk_log USING btree (knowledge_base_id, start_time DESC);
CREATE INDEX idx_t_document_chunk_log_status_time ON public.t_document_chunk_log USING btree (status, start_time);
CREATE INDEX idx_t_document_chunk_log_processing_timeout ON public.t_document_chunk_log USING btree (status, start_time) WHERE ((delete_flag = 0) AND ((status)::text = 'PROCESSING'::text));
CREATE INDEX idx_t_document_chunk_log_chunk_config_gin ON public.t_document_chunk_log USING gin (chunk_config);

DROP TABLE IF EXISTS "public"."t_document_department_auth";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_document_department_auth" (
    "id" int8 NOT NULL,
    "document_id" int8 NOT NULL,
    "department_id" int8 NOT NULL,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_document_department_auth"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_document_department_auth"."document_id" IS '文档ID，逻辑关联 t_knowledge_document.id';
COMMENT ON COLUMN "public"."t_document_department_auth"."department_id" IS '可访问部门ID，逻辑关联 t_department.id';
COMMENT ON COLUMN "public"."t_document_department_auth"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_document_department_auth"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_document_department_auth"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_document_department_auth" IS '文档部门授权表';


-- Indices
CREATE UNIQUE INDEX uk_t_document_department_auth ON public.t_document_department_auth USING btree (document_id, department_id);

DROP TABLE IF EXISTS "public"."t_knowledge_base";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_knowledge_base" (
    "id" int8 NOT NULL,
    "name" varchar(128) NOT NULL,
    "description" text,
    "status" varchar(32) NOT NULL DEFAULT 'ENABLED'::character varying,
    "embedding_model" varchar(128) NOT NULL,
    "collection_name" varchar(128) NOT NULL,
    "created_by" int8 NOT NULL,
    "updated_by" int8 NOT NULL,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_knowledge_base"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_knowledge_base"."name" IS '知识库名称，业务唯一';
COMMENT ON COLUMN "public"."t_knowledge_base"."description" IS '知识库描述';
COMMENT ON COLUMN "public"."t_knowledge_base"."status" IS '知识库状态：ENABLED-启用，DISABLED-停用';
COMMENT ON COLUMN "public"."t_knowledge_base"."embedding_model" IS '该知识库默认使用的向量模型名称';
COMMENT ON COLUMN "public"."t_knowledge_base"."collection_name" IS '知识库存储集合名称，当前用于 RustFS 的 bucket 名称，业务唯一';
COMMENT ON COLUMN "public"."t_knowledge_base"."created_by" IS '创建人用户ID，逻辑关联 t_user.id';
COMMENT ON COLUMN "public"."t_knowledge_base"."updated_by" IS '最后更新人用户ID，逻辑关联 t_user.id';
COMMENT ON COLUMN "public"."t_knowledge_base"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_knowledge_base"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_knowledge_base"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_knowledge_base" IS '知识库表';


-- Indices
CREATE UNIQUE INDEX uk_t_knowledge_base_name_del_flag ON public.t_knowledge_base USING btree (name, delete_flag);
CREATE UNIQUE INDEX uk_t_knowledge_base_collection_name_del_flag ON public.t_knowledge_base USING btree (collection_name, delete_flag);

DROP TABLE IF EXISTS "public"."t_knowledge_document";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_knowledge_document" (
    "id" int8 NOT NULL,
    "knowledge_base_id" int8 NOT NULL,
    "title" varchar(256) NOT NULL,
    "source_type" varchar(32) NOT NULL,
    "mime_type" varchar(128),
    "storage_bucket" varchar(128) NOT NULL,
    "storage_key" varchar(512) NOT NULL,
    "storage_etag" varchar(128),
    "storage_url" varchar(1024) NOT NULL,
    "file_size" int8 NOT NULL DEFAULT 0,
    "parse_status" varchar(32) NOT NULL DEFAULT 'PENDING'::character varying,
    "visibility_scope" varchar(32) NOT NULL DEFAULT 'INTERNAL'::character varying,
    "min_rank_level" int4 NOT NULL DEFAULT 10,
    "status" varchar(32) NOT NULL DEFAULT 'ENABLED'::character varying,
    "created_by" int8 NOT NULL,
    "updated_by" int8 NOT NULL,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    "chunk_mode" varchar(32) NOT NULL,
    "chunk_config" json NOT NULL,
    "fail_reason" text,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_knowledge_document"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_knowledge_document"."knowledge_base_id" IS '所属知识库ID，逻辑关联 t_knowledge_base.id';
COMMENT ON COLUMN "public"."t_knowledge_document"."title" IS '文档标题';
COMMENT ON COLUMN "public"."t_knowledge_document"."source_type" IS '文档来源类型，例如 UPLOAD-上传文件，IMPORT-外部导入';
COMMENT ON COLUMN "public"."t_knowledge_document"."mime_type" IS '文件MIME类型，例如 application/pdf';
COMMENT ON COLUMN "public"."t_knowledge_document"."storage_bucket" IS '文档原始文件所在 RustFS bucket 名称';
COMMENT ON COLUMN "public"."t_knowledge_document"."storage_key" IS '文档原始文件在 RustFS 中的对象键';
COMMENT ON COLUMN "public"."t_knowledge_document"."storage_etag" IS '对象存储返回的 ETag，用于文件内容版本识别';
COMMENT ON COLUMN "public"."t_knowledge_document"."storage_url" IS '文档在 RustFS 中的访问URL或内部访问地址';
COMMENT ON COLUMN "public"."t_knowledge_document"."file_size" IS '文件大小，单位字节';
COMMENT ON COLUMN "public"."t_knowledge_document"."parse_status" IS '文档解析状态：PENDING-待解析，PROCESSING-解析中，SUCCESS-解析成功，FAILED-解析失败';
COMMENT ON COLUMN "public"."t_knowledge_document"."visibility_scope" IS '文档可见性范围标签，例如 INTERNAL-内部文档，SENSITIVE-敏感文档';
COMMENT ON COLUMN "public"."t_knowledge_document"."min_rank_level" IS '最低可访问职级，用户职级需大于等于该值才可访问';
COMMENT ON COLUMN "public"."t_knowledge_document"."status" IS '文档状态：ENABLED-启用，DISABLED-停用';
COMMENT ON COLUMN "public"."t_knowledge_document"."created_by" IS '创建人用户ID，逻辑关联 t_user.id';
COMMENT ON COLUMN "public"."t_knowledge_document"."updated_by" IS '最后更新人用户ID，逻辑关联 t_user.id';
COMMENT ON COLUMN "public"."t_knowledge_document"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_knowledge_document"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_knowledge_document"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';
COMMENT ON COLUMN "public"."t_knowledge_document"."chunk_mode" IS '直接分块或者动态感知分块';
COMMENT ON COLUMN "public"."t_knowledge_document"."chunk_config" IS '分块大小与重叠大小';
COMMENT ON COLUMN "public"."t_knowledge_document"."fail_reason" IS '文档解析失败原因，重试开始或解析成功时清空';


-- Comments
COMMENT ON TABLE "public"."t_knowledge_document" IS '知识文档表';


-- Indices
CREATE INDEX idx_t_knowledge_document_kb_status ON public.t_knowledge_document USING btree (knowledge_base_id, status, delete_flag);
CREATE INDEX idx_t_knowledge_document_created_by ON public.t_knowledge_document USING btree (created_by, delete_flag);

DROP TABLE IF EXISTS "public"."t_qa_trace_log";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_qa_trace_log" (
    "id" int8 NOT NULL,
    "trace_id" varchar(128) NOT NULL,
    "session_id" int8,
    "user_id" int8 NOT NULL,
    "stage" varchar(64) NOT NULL,
    "status" varchar(32) NOT NULL,
    "latency_ms" int8 NOT NULL DEFAULT 0,
    "payload_ref" varchar(512),
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_qa_trace_log"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_qa_trace_log"."trace_id" IS '一次问答链路的追踪ID';
COMMENT ON COLUMN "public"."t_qa_trace_log"."session_id" IS '所属会话ID，逻辑关联 t_chat_session.id，可为空';
COMMENT ON COLUMN "public"."t_qa_trace_log"."user_id" IS '发起用户ID，逻辑关联 t_user.id';
COMMENT ON COLUMN "public"."t_qa_trace_log"."stage" IS '链路阶段，例如 REWRITE、RETRIEVE、RERANK、GENERATE';
COMMENT ON COLUMN "public"."t_qa_trace_log"."status" IS '阶段执行状态：SUCCESS-成功，FAILED-失败，CANCELLED-取消';
COMMENT ON COLUMN "public"."t_qa_trace_log"."latency_ms" IS '当前阶段耗时，单位毫秒';
COMMENT ON COLUMN "public"."t_qa_trace_log"."payload_ref" IS '链路扩展数据引用，例如日志文件位置或对象存储地址';
COMMENT ON COLUMN "public"."t_qa_trace_log"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_qa_trace_log"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_qa_trace_log"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_qa_trace_log" IS '问答链路追踪日志表';


-- Indices
CREATE INDEX idx_t_qa_trace_log_trace_time ON public.t_qa_trace_log USING btree (trace_id, create_time);
CREATE INDEX idx_t_qa_trace_log_session_stage_time ON public.t_qa_trace_log USING btree (session_id, stage, create_time);
CREATE INDEX idx_t_qa_trace_log_user_time ON public.t_qa_trace_log USING btree (user_id, create_time);

DROP TABLE IF EXISTS "public"."t_user";
-- This script only contains the table creation statements and does not fully represent the table in the database. Do not use it as a backup.

-- Table Definition
CREATE TABLE "public"."t_user" (
    "id" int8 NOT NULL,
    "username" varchar(64) NOT NULL,
    "display_name" varchar(128) NOT NULL,
    "role_code" varchar(32) NOT NULL,
    "department_id" int8 NOT NULL,
    "rank_level" int4 NOT NULL,
    "status" varchar(32) NOT NULL DEFAULT 'ENABLED'::character varying,
    "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag" int2 NOT NULL DEFAULT 0,
    "password_hash" varchar(255),
    PRIMARY KEY ("id")
);

-- Column Comments
COMMENT ON COLUMN "public"."t_user"."id" IS '主键ID，BIGINT，使用雪花算法生成';
COMMENT ON COLUMN "public"."t_user"."username" IS '登录用户名，业务唯一';
COMMENT ON COLUMN "public"."t_user"."display_name" IS '用户展示名称';
COMMENT ON COLUMN "public"."t_user"."role_code" IS '角色编码，例如 ADMIN-管理员，USER-普通用户';
COMMENT ON COLUMN "public"."t_user"."department_id" IS '所属部门ID，逻辑关联 t_department.id';
COMMENT ON COLUMN "public"."t_user"."rank_level" IS '职级数值，数值越大表示职级越高';
COMMENT ON COLUMN "public"."t_user"."status" IS '用户状态：ENABLED-启用，DISABLED-停用';
COMMENT ON COLUMN "public"."t_user"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_user"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_user"."delete_flag" IS '逻辑删除标记：0-未删除，1-已删除';


-- Comments
COMMENT ON TABLE "public"."t_user" IS '用户表';


-- Indices
CREATE UNIQUE INDEX uk_t_user_username ON public.t_user USING btree (username);
CREATE INDEX idx_t_user_department_rank_status ON public.t_user USING btree (department_id, rank_level, delete_flag, status);
















-- ============================================================
-- 意图树节点表
-- ============================================================
DROP TABLE IF EXISTS "public"."t_intent_node";

CREATE TABLE "public"."t_intent_node" (
    "id"                    int8        NOT NULL,
    "intent_code"           varchar(128) NOT NULL,
    "name"                  varchar(128) NOT NULL,
    "level"                 int4        NOT NULL DEFAULT 0,
    "parent_id"             int8        NOT NULL DEFAULT 0,
    "parent_code"           varchar(128),
    "description"           text,
    "examples"              text,
    "collection_name"       varchar(128),
    "mcp_tool_id"           varchar(128),
    "top_k"                 int4,
    "kind"                  int4        NOT NULL DEFAULT 0,
    "sort_order"            int4        NOT NULL DEFAULT 0,
    "enabled"               int4        NOT NULL DEFAULT 1,
    "prompt_snippet"        text,
    "prompt_template"       text,
    "param_prompt_template" text,
    "created_by"            int8        NOT NULL,
    "updated_by"            int8        NOT NULL,
    "create_time"           timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time"           timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "delete_flag"           int2        NOT NULL DEFAULT 0,
    PRIMARY KEY ("id")
);

COMMENT ON TABLE  "public"."t_intent_node" IS '意图树节点表';
COMMENT ON COLUMN "public"."t_intent_node"."id"                    IS '主键ID，BIGINT，雪花算法';
COMMENT ON COLUMN "public"."t_intent_node"."intent_code"           IS '意图标识，业务唯一，如 biz-oa';
COMMENT ON COLUMN "public"."t_intent_node"."name"                  IS '节点名称';
COMMENT ON COLUMN "public"."t_intent_node"."level"                 IS '层级：0-DOMAIN，1-CATEGORY，2-TOPIC';
COMMENT ON COLUMN "public"."t_intent_node"."parent_id"             IS '父节点ID，根节点为0';
COMMENT ON COLUMN "public"."t_intent_node"."parent_code"           IS '父节点意图标识（冗余），根节点为null';
COMMENT ON COLUMN "public"."t_intent_node"."description"           IS '节点描述';
COMMENT ON COLUMN "public"."t_intent_node"."examples"              IS '示例问题，JSON数组字符串';
COMMENT ON COLUMN "public"."t_intent_node"."collection_name"       IS '向量库Collection名称（kind=KB时）';
COMMENT ON COLUMN "public"."t_intent_node"."mcp_tool_id"           IS 'MCP工具ID（kind=MCP时）';
COMMENT ON COLUMN "public"."t_intent_node"."top_k"                 IS '节点级TopK，null时使用全局';
COMMENT ON COLUMN "public"."t_intent_node"."kind"                  IS '节点类型：0-KB，1-SYSTEM，2-MCP';
COMMENT ON COLUMN "public"."t_intent_node"."sort_order"            IS '同级排序号';
COMMENT ON COLUMN "public"."t_intent_node"."enabled"               IS '是否启用：1-启用，0-停用';
COMMENT ON COLUMN "public"."t_intent_node"."prompt_snippet"        IS '短规则片段';
COMMENT ON COLUMN "public"."t_intent_node"."prompt_template"       IS 'Prompt模板';
COMMENT ON COLUMN "public"."t_intent_node"."param_prompt_template" IS '参数提取提示词模板（MCP专属）';
COMMENT ON COLUMN "public"."t_intent_node"."created_by"            IS '创建人用户ID';
COMMENT ON COLUMN "public"."t_intent_node"."updated_by"            IS '最后更新人用户ID';

CREATE UNIQUE INDEX uk_t_intent_node_intent_code ON public.t_intent_node USING btree (intent_code) WHERE delete_flag = 0;
CREATE INDEX idx_t_intent_node_parent_id        ON public.t_intent_node USING btree (parent_id, delete_flag);
CREATE INDEX idx_t_intent_node_parent_code      ON public.t_intent_node USING btree (parent_code, delete_flag);
CREATE INDEX idx_t_intent_node_level_enabled    ON public.t_intent_node USING btree (level, enabled, delete_flag);
