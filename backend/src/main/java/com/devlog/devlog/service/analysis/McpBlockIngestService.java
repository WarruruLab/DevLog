package com.devlog.devlog.service.analysis;

import com.devlog.devlog.api.dto.request.McpSessionBlocksIngestRequest;
import com.devlog.devlog.api.dto.request.McpSessionBlocksIngestRequest.McpBlockRequest;
import com.devlog.devlog.api.dto.response.McpSessionBlocksIngestResponse;
import com.devlog.devlog.domain.analysis.SessionBlock;
import com.devlog.devlog.domain.analysis.SessionBlockMessage;
import com.devlog.devlog.domain.analysis.SessionBlockMessageRepository;
import com.devlog.devlog.domain.analysis.SessionBlockRepository;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import com.devlog.devlog.domain.sync.SyncedMessage;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class McpBlockIngestService {

    private final LogicalSessionRepository sessionRepository;
    private final SyncedMessageRepository messageRepository;
    private final SessionBlockRepository blockRepository;
    private final SessionBlockMessageRepository blockMessageRepository;
    private final TransactionTemplate transactionTemplate;
    private final int maxBlocks;
    private final int maxMessagesPerBlock;

    public McpBlockIngestService(
        LogicalSessionRepository sessionRepository,
        SyncedMessageRepository messageRepository,
        SessionBlockRepository blockRepository,
        SessionBlockMessageRepository blockMessageRepository,
        PlatformTransactionManager transactionManager,
        @Value("${mcp.ingest.max-blocks:500}") int maxBlocks,
        @Value("${mcp.ingest.max-messages-per-block:200}") int maxMessagesPerBlock
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
        this.blockMessageRepository = blockMessageRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxBlocks = maxBlocks;
        this.maxMessagesPerBlock = maxMessagesPerBlock;
    }

    public McpSessionBlocksIngestResponse ingest(McpSessionBlocksIngestRequest request) {
        validateRequest(request);

        try {
            return transactionTemplate.execute(status -> ingestInTransaction(request));
        } catch (RuntimeException e) {
            markFailure(request.sessionId(), safeMessage(e));
            throw e;
        }
    }

    private McpSessionBlocksIngestResponse ingestInTransaction(McpSessionBlocksIngestRequest request) {
        String sessionId = request.sessionId();
        LogicalSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("session not found: " + sessionId));

        List<SyncedMessage> existingMessages = messageRepository.findAllBySessionId(sessionId);
        Map<String, SyncedMessage> messageById = existingMessages.stream()
            .collect(Collectors.toMap(
                SyncedMessage::getMessageId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));

        validateMessageIdsExist(request, messageById.keySet());

        LocalDateTime now = LocalDateTime.now();
        sessionRepository.updateSessionStatus(sessionId, "ANALYZING");
        sessionRepository.updateAnalysisStatus(sessionId, "RUNNING");
        sessionRepository.updateAnalysisErrorMessage(sessionId, null);

        if ("REPLACE".equalsIgnoreCase(request.mode())) {
            blockMessageRepository.deleteBySessionId(sessionId);
            blockRepository.deleteBySessionId(sessionId);
            messageRepository.markPendingBySessionId(sessionId);
        }

        Map<String, Long> blockIdMap = new LinkedHashMap<>();
        List<String> structuredMessageIds = new ArrayList<>();
        int savedBlockCount = 0;

        for (int index = 0; index < request.blocks().size(); index++) {
            McpBlockRequest blockRequest = request.blocks().get(index);
            int sequenceNo = blockRequest.sequenceNo() != null ? blockRequest.sequenceNo() : index + 1;
            String blockType = mapBlockType(blockRequest.blockType());
            List<String> messageIds = blockRequest.messageIds();
            LocalDateTime messageStartAt = resolveMessageStartAt(messageIds, messageById);
            LocalDateTime messageEndAt = resolveMessageEndAt(messageIds, messageById);
            String contentJson = toJsonContent(blockRequest.content());

            SessionBlock block = new SessionBlock(
                null,
                sessionId,
                sequenceNo,
                blockType,
                requireText(blockRequest.title(), "title"),
                requireText(blockRequest.summary(), "summary"),
                contentJson,
                "ACTIVE",
                messageIds.size(),
                messageStartAt,
                messageEndAt
            );

            Long blockId = blockRepository.save(block);
            if (blockId == null) {
                throw new IllegalStateException("failed to create block_id");
            }
            blockIdMap.put(blockRequest.mcpBlockId(), blockId);
            savedBlockCount++;

            List<SessionBlockMessage> mappings = new ArrayList<>(messageIds.size());
            for (int order = 0; order < messageIds.size(); order++) {
                String messageId = messageIds.get(order);
                mappings.add(new SessionBlockMessage(
                    null,
                    sessionId,
                    blockId,
                    messageId,
                    order + 1,
                    now
                ));
                structuredMessageIds.add(messageId);
            }
            blockMessageRepository.saveAll(mappings);
        }

        if (!structuredMessageIds.isEmpty()) {
            messageRepository.markStructured(structuredMessageIds, now);
        }

        refreshSessionState(sessionId, session, now);
        sessionRepository.updateSessionStatus(sessionId, "READY");
        sessionRepository.updateAnalysisStatus(sessionId, "DONE");
        sessionRepository.updateAnalysisErrorMessage(sessionId, null);

        long structuredCount = messageRepository.countStructuredBySessionId(sessionId);
        long unstructuredCount = messageRepository.countUnstructuredBySessionId(sessionId);
        return new McpSessionBlocksIngestResponse(
            sessionId,
            savedBlockCount,
            Math.toIntExact(structuredCount),
            Math.toIntExact(unstructuredCount),
            blockIdMap,
            "DONE"
        );
    }

    private void refreshSessionState(String sessionId, LogicalSession session, LocalDateTime now) {
        long totalMessageCount = messageRepository.countBySessionId(sessionId);
        long structuredMessageCount = messageRepository.countStructuredBySessionId(sessionId);
        long unstructuredMessageCount = messageRepository.countUnstructuredBySessionId(sessionId);
        int blockCount = blockRepository.findAllBySessionId(sessionId).size();
        LocalDateTime lastMessageAt = messageRepository.findLastCreatedAtBySessionId(sessionId)
            .orElse(session.getLastMessageAt());

        sessionRepository.updateCounts(
            sessionId,
            Math.toIntExact(totalMessageCount),
            Math.toIntExact(totalMessageCount),
            Math.toIntExact(structuredMessageCount),
            Math.toIntExact(unstructuredMessageCount),
            blockCount
        );
        sessionRepository.updateTimestamps(sessionId, lastMessageAt, session.getLastSyncedAt(), now);
    }

    private void markFailure(String sessionId, String message) {
        sessionRepository.updateSessionStatus(sessionId, "FAILED");
        sessionRepository.updateAnalysisStatus(sessionId, "FAILED");
        sessionRepository.updateAnalysisErrorMessage(sessionId, message);
    }

    private void validateRequest(McpSessionBlocksIngestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        requireText(request.sessionId(), "sessionId");
        requireText(request.mode(), "mode");
        if (request.blocks() == null || request.blocks().isEmpty()) {
            throw new IllegalArgumentException("blocks must not be empty");
        }
        if (request.blocks().size() > maxBlocks) {
            throw new IllegalArgumentException("blocks exceeds maxBlocks: " + maxBlocks);
        }
        if (!"REPLACE".equalsIgnoreCase(request.mode())) {
            throw new IllegalArgumentException("only REPLACE mode is supported");
        }
        Set<String> blockIds = new LinkedHashSet<>();
        Set<String> messageIds = new LinkedHashSet<>();
        for (McpBlockRequest block : request.blocks()) {
            if (block == null) {
                throw new IllegalArgumentException("block must not be null");
            }
            requireText(block.mcpBlockId(), "mcpBlockId");
            if (!blockIds.add(block.mcpBlockId())) {
                throw new IllegalArgumentException("duplicate mcpBlockId: " + block.mcpBlockId());
            }
            requireText(block.title(), "title");
            requireText(block.summary(), "summary");
            if (block.messageIds() == null || block.messageIds().isEmpty()) {
                throw new IllegalArgumentException("messageIds must not be empty");
            }
            if (block.messageIds().size() > maxMessagesPerBlock) {
                throw new IllegalArgumentException("messageIds exceeds maxMessagesPerBlock: " + maxMessagesPerBlock);
            }
            for (String messageId : block.messageIds()) {
                requireText(messageId, "messageId");
                if (!messageIds.add(messageId)) {
                    throw new IllegalArgumentException("duplicate messageId: " + messageId);
                }
            }
        }
    }

    private void validateMessageIdsExist(McpSessionBlocksIngestRequest request, Set<String> existingMessageIds) {
        Set<String> requested = new LinkedHashSet<>();
        for (McpBlockRequest block : request.blocks()) {
            requested.addAll(block.messageIds());
        }
        if (!existingMessageIds.containsAll(requested)) {
            requested.removeAll(existingMessageIds);
            throw new IllegalArgumentException("unknown messageId included: " + requested);
        }
    }

    private LocalDateTime resolveMessageStartAt(List<String> messageIds, Map<String, SyncedMessage> messageById) {
        LocalDateTime startAt = null;
        for (String messageId : messageIds) {
            SyncedMessage message = messageById.get(messageId);
            if (message == null) {
                continue;
            }
            LocalDateTime createdAt = message.getMessageCreatedAt();
            if (createdAt == null) {
                continue;
            }
            if (startAt == null || createdAt.isBefore(startAt)) {
                startAt = createdAt;
            }
        }
        return startAt;
    }

    private LocalDateTime resolveMessageEndAt(List<String> messageIds, Map<String, SyncedMessage> messageById) {
        LocalDateTime endAt = null;
        for (String messageId : messageIds) {
            SyncedMessage message = messageById.get(messageId);
            if (message == null) {
                continue;
            }
            LocalDateTime createdAt = message.getMessageCreatedAt();
            if (createdAt == null) {
                continue;
            }
            if (endAt == null || createdAt.isAfter(endAt)) {
                endAt = createdAt;
            }
        }
        return endAt;
    }

    private String toJsonContent(Map<String, Object> content) {
        return toJsonValue(content == null ? Map.of() : content);
    }

    private String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return "\"" + escapeJson(string) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(toJsonValue(String.valueOf(entry.getKey())));
                builder.append(':');
                builder.append(toJsonValue(entry.getValue()));
                first = false;
            }
            return builder.append('}').toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object element : collection) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(toJsonValue(element));
                first = false;
            }
            return builder.append(']').toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder builder = new StringBuilder("[");
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(toJsonValue(java.lang.reflect.Array.get(value, i)));
            }
            return builder.append(']').toString();
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private static String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static String mapBlockType(String blockType) {
        if (blockType == null || blockType.isBlank()) {
            return "PROBLEM";
        }
        String normalized = blockType.trim().toUpperCase();
        return switch (normalized) {
            case "PROBLEM", "CAUSE", "TRIAL", "SOLUTION", "VERIFY" -> normalized;
            case "PROPOSAL" -> "SOLUTION";
            case "RESULT" -> "VERIFY";
            case "INSIGHT" -> "CAUSE";
            case "TRIAL_BLOCK" -> "TRIAL";
            default -> throw new IllegalArgumentException("unsupported blockType: " + blockType);
        };
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}
