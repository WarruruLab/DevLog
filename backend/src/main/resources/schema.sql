-- DevLog target schema aligned with concept-c flow

-- 1. Drop existing tables
DROP TABLE IF EXISTS draft_block;
DROP TABLE IF EXISTS draft;
DROP TABLE IF EXISTS mcp_ingest_event;
DROP TABLE IF EXISTS session_block_message;
DROP TABLE IF EXISTS session_block;
DROP TABLE IF EXISTS session_message;
DROP TABLE IF EXISTS logical_session;

-- 2. Session root table
CREATE TABLE logical_session (
    session_id VARCHAR(255) PRIMARY KEY,
    source_session_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    session_status VARCHAR(30) NOT NULL DEFAULT 'READY',
    sync_status VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    analysis_status VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    total_message_count INT NOT NULL DEFAULT 0,
    synced_message_count INT NOT NULL DEFAULT 0,
    structured_message_count INT NOT NULL DEFAULT 0,
    unstructured_message_count INT NOT NULL DEFAULT 0,
    block_count INT NOT NULL DEFAULT 0,
    last_message_at DATETIME NULL,
    last_synced_at DATETIME NULL,
    last_analyzed_at DATETIME NULL,
    sync_error_message TEXT NULL,
    analysis_error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_status (session_status),
    INDEX idx_sync_status (sync_status),
    INDEX idx_analysis_status (analysis_status)
);

-- 3. Raw synced messages with structuring state
CREATE TABLE session_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    author_name VARCHAR(100) NULL,
    content LONGTEXT NOT NULL,
    message_created_at DATETIME NOT NULL,
    structure_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    structured_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uidx_message_id (message_id),
    INDEX idx_session_created_at (session_id, message_created_at),
    INDEX idx_session_structure_status (session_id, structure_status)
);

-- 4. Structured blocks
CREATE TABLE session_block (
    block_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    external_block_id VARCHAR(255) NULL,
    sequence_no INT NOT NULL,
    block_type VARCHAR(30) NOT NULL DEFAULT 'PROBLEM',
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    content_json JSON NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    source_message_count INT NOT NULL DEFAULT 0,
    message_start_at DATETIME NULL,
    message_end_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uidx_session_sequence (session_id, sequence_no),
    UNIQUE INDEX uidx_session_external_block (session_id, external_block_id),
    INDEX idx_session_block_status (session_id, status),
    INDEX idx_session_block_type (session_id, block_type)
);

-- 5. Message-to-block mapping
CREATE TABLE session_block_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    block_id BIGINT NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    message_order INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uidx_block_message (block_id, message_id),
    INDEX idx_session_message (session_id, message_id),
    INDEX idx_block_order (block_id, message_order)
);

-- 6. MCP ingest event dedupe log
CREATE TABLE mcp_ingest_event (
    event_id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    operation VARCHAR(30) NOT NULL,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result_status VARCHAR(30) NOT NULL,
    INDEX idx_mcp_ingest_session (session_id),
    INDEX idx_mcp_ingest_message (message_id),
    INDEX idx_mcp_ingest_processed_at (processed_at)
);

-- 7. Draft versions
CREATE TABLE draft (
    draft_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    title VARCHAR(255) NOT NULL,
    content_markdown LONGTEXT NULL,
    generation_prompt TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uidx_session_version (session_id, version_no),
    INDEX idx_session_draft_status (session_id, status)
);

-- 8. Selected blocks for each draft
CREATE TABLE draft_block (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    block_id BIGINT NOT NULL,
    selected_order INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uidx_draft_block (draft_id, block_id),
    UNIQUE INDEX uidx_draft_selected_order (draft_id, selected_order),
    INDEX idx_block_id (block_id)
);
