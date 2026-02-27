-- synced_message: DevTalk 원본 데이터
CREATE TABLE synced_message (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                session_id VARCHAR(255) NOT NULL,
                                role VARCHAR(50) NOT NULL,
                                content TEXT NOT NULL,
                                timestamp DATETIME NOT NULL,
                                synced_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                INDEX idx_session_timestamp (session_id, timestamp)
);

-- session_block: AI 분석 결과
CREATE TABLE session_block (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               session_id VARCHAR(255) NOT NULL,
                               block_type VARCHAR(100) NOT NULL,
                               topic VARCHAR(255),
                               json_content JSON NOT NULL,
                               message_ids TEXT,
                               created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                               updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               INDEX idx_session (session_id)
);

-- draft: 생성된 초안
CREATE TABLE draft (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       session_id VARCHAR(255) NOT NULL,
                       title VARCHAR(500),
                       content TEXT NOT NULL,
                       selected_block_ids TEXT,
                       created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                       INDEX idx_session (session_id)
);
