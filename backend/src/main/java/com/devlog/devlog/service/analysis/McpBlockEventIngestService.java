package com.devlog.devlog.service.analysis;

import com.devlog.devlog.api.dto.request.McpSessionBlockEventIngestRequest;
import com.devlog.devlog.api.dto.response.McpSessionActiveBlockResponse;
import com.devlog.devlog.api.dto.response.McpSessionBlockEventIngestResponse;
import com.devlog.devlog.domain.analysis.McpIngestEvent;
import com.devlog.devlog.domain.analysis.McpIngestEventRepository;
import com.devlog.devlog.domain.analysis.SessionBlock;
import com.devlog.devlog.domain.analysis.SessionBlockMessage;
import com.devlog.devlog.domain.analysis.SessionBlockMessageRepository;
import com.devlog.devlog.domain.analysis.SessionBlockRepository;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import com.devlog.devlog.domain.sync.SyncedMessage;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class McpBlockEventIngestService {

    private final LogicalSessionRepository sessionRepository;
    private final SyncedMessageRepository messageRepository;
    private final SessionBlockRepository blockRepository;
    private final SessionBlockMessageRepository blockMessageRepository;
    private final McpIngestEventRepository ingestEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final int maxContentBytes;

    public McpBlockEventIngestService(
        LogicalSessionRepository sessionRepository,
        SyncedMessageRepository messageRepository,
        SessionBlockRepository blockRepository,
        SessionBlockMessageRepository blockMessageRepository,
        McpIngestEventRepository ingestEventRepository,
        PlatformTransactionManager transactionManager,
        @Value("${mcp.event.max-content-bytes:65536}") int maxContentBytes
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
        this.blockMessageRepository = blockMessageRepository;
        this.ingestEventRepository = ingestEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxContentBytes = maxContentBytes;
    }

    public McpSessionBlockEventIngestResponse ingest(McpSessionBlockEventIngestRequest request) {
        validateRequest(request);

        if (ingestEventRepository.existsIngestEvent(request.eventId())) {
            return buildDuplicateResponse(request);
        }

        try {
            return transactionTemplate.execute(status -> ingestInTransaction(request));
        } catch (RuntimeException e) {
            markFailure(request.sessionId(), safeMessage(e));
            throw e;
        }
    }

    public Optional<McpSessionActiveBlockResponse> findActiveBlock(String sessionId) {
        if (sessionRepository.findBySessionId(sessionId).isEmpty()) {
            return Optional.empty();
        }

        return blockRepository.findActiveBlockBySessionId(sessionId)
            .map(block -> toActiveBlockResponse(sessionId, block));
    }

    private McpSessionBlockEventIngestResponse ingestInTransaction(McpSessionBlockEventIngestRequest request) {
        if (ingestEventRepository.existsIngestEvent(request.eventId())) {
            return buildDuplicateResponse(request);
        }

        SessionContext context = loadSessionContext(request.sessionId());
        SyncedMessage message = requireMessage(context.messageById(), request.messageId());
        LocalDateTime now = LocalDateTime.now();

        sessionRepository.updateSessionStatus(request.sessionId(), "ANALYZING");
        sessionRepository.updateAnalysisStatus(request.sessionId(), "RUNNING");
        sessionRepository.updateAnalysisErrorMessage(request.sessionId(), null);

        McpSessionBlockEventIngestResponse response = switch (normalizeOperation(request.operation())) {
            case "CREATE_BLOCK" -> handleCreateBlock(request, context, message, now);
            case "APPEND" -> handleAppend(request, context, message, now);
            case "FINALIZE_BLOCK" -> handleFinalize(request, context);
            case "REOPEN_BLOCK" -> handleReopen(request, context);
            default -> throw new IllegalArgumentException("unsupported operation: " + request.operation());
        };

        ingestEventRepository.saveIngestEvent(new McpIngestEvent(
            request.eventId(),
            request.sessionId(),
            request.messageId(),
            normalizeOperation(request.operation()),
            now,
            response.status()
        ));

        refreshSessionState(request.sessionId(), context.session(), now);
        return response;
    }

    private void validateRequest(McpSessionBlockEventIngestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        requireText(request.sessionId(), "sessionId");
        requireText(request.eventId(), "eventId");
        requireText(request.messageId(), "messageId");
        requireText(request.operation(), "operation");
        if (request.targetBlock() == null) {
            throw new IllegalArgumentException("targetBlock is required");
        }
        requireText(request.targetBlock().mcpBlockId(), "targetBlock.mcpBlockId");
        String contentJson = toJsonContent(request.content());
        if (contentJson.getBytes(StandardCharsets.UTF_8).length > maxContentBytes) {
            throw new IllegalArgumentException("content exceeds maxContentBytes: " + maxContentBytes);
        }
    }

    private McpSessionBlockEventIngestResponse handleCreateBlock(
        McpSessionBlockEventIngestRequest request,
        SessionContext context,
        SyncedMessage message,
        LocalDateTime now
    ) {
        String externalBlockId = request.targetBlock().mcpBlockId();
        Optional<SessionBlock> existingBlock = blockRepository.findByExternalBlockId(request.sessionId(), externalBlockId);
        if (existingBlock.isPresent()) {
            return recoverCreateBlockForExistingBlock(request, context, message, now, existingBlock.get());
        }

        List<SessionBlockMessage> existingMessageMappings = blockMessageRepository.findByMessageId(
            request.sessionId(),
            request.messageId()
        );
        if (!existingMessageMappings.isEmpty()) {
            throw new IllegalArgumentException("message already mapped to another block: " + request.messageId());
        }

        SessionBlock block = new SessionBlock(
            null,
            request.sessionId(),
            externalBlockId,
            null,
            mapBlockType(request.targetBlock().blockType()),
            requireText(request.targetBlock().title(), "targetBlock.title"),
            requireText(request.targetBlock().summary(), "targetBlock.summary"),
            toJsonContent(request.content()),
            normalizeBlockStatus(request.targetBlock().status(), "ACTIVE"),
            1,
            message.getMessageCreatedAt(),
            message.getMessageCreatedAt()
        );

        Long blockId;
        try {
            blockId = blockRepository.save(block);
        } catch (DuplicateKeyException e) {
            SessionBlock racedBlock = blockRepository.findByExternalBlockId(request.sessionId(), externalBlockId)
                .orElseThrow(() -> e);
            return recoverCreateBlockForExistingBlock(request, context, message, now, racedBlock);
        }
        if (blockId == null) {
            throw new IllegalStateException("failed to create block_id");
        }

        saveMappingOrIgnoreSameBlockRace(request.sessionId(), blockId, request.messageId(), 1, now);
        messageRepository.markStructured(List.of(request.messageId()), now);

        return buildAppliedResponse(
            request,
            blockId,
            "Created block and mapped message."
        );
    }

    private McpSessionBlockEventIngestResponse recoverCreateBlockForExistingBlock(
        McpSessionBlockEventIngestRequest request,
        SessionContext context,
        SyncedMessage message,
        LocalDateTime now,
        SessionBlock existingBlock
    ) {
        List<SessionBlockMessage> mappingsForMessage = blockMessageRepository.findByMessageId(
            request.sessionId(),
            request.messageId()
        );
        boolean mappedToTarget = mappingsForMessage.stream()
            .anyMatch(mapping -> existingBlock.getBlockId().equals(mapping.getBlockId()));
        if (mappedToTarget) {
            return new McpSessionBlockEventIngestResponse(
                request.sessionId(),
                request.eventId(),
                normalizeOperation(request.operation()),
                "IGNORED",
                existingBlock.getBlockId(),
                existingBlock.getExternalBlockId(),
                "Message already mapped to existing block."
            );
        }
        if (!mappingsForMessage.isEmpty()) {
            throw new IllegalArgumentException("message already mapped to another block: " + request.messageId());
        }
        if (!isActive(existingBlock)) {
            throw new IllegalArgumentException("target block is not active: " + request.targetBlock().mcpBlockId());
        }

        int nextOrder = nextMessageOrder(context.mappingsByBlockId().get(existingBlock.getBlockId()));
        saveMappingOrIgnoreSameBlockRace(
            request.sessionId(),
            existingBlock.getBlockId(),
            request.messageId(),
            nextOrder,
            now
        );
        messageRepository.markStructured(List.of(request.messageId()), now);

        return buildAppliedResponse(
            request,
            existingBlock.getBlockId(),
            "Mapped message to existing block."
        );
    }

    private McpSessionBlockEventIngestResponse handleAppend(
        McpSessionBlockEventIngestRequest request,
        SessionContext context,
        SyncedMessage message,
        LocalDateTime now
    ) {
        SessionBlock existingBlock = requireBlock(request.sessionId(), request.targetBlock().mcpBlockId());
        if (!isActive(existingBlock)) {
            throw new IllegalArgumentException("target block is not active: " + request.targetBlock().mcpBlockId());
        }
        if (blockMessageRepository.existsBlockMessage(existingBlock.getBlockId(), request.messageId())) {
            return new McpSessionBlockEventIngestResponse(
                request.sessionId(),
                request.eventId(),
                normalizeOperation(request.operation()),
                "IGNORED",
                existingBlock.getBlockId(),
                existingBlock.getExternalBlockId(),
                "Message already mapped to target block."
            );
        }

        int nextOrder = nextMessageOrder(context.mappingsByBlockId().get(existingBlock.getBlockId()));
        saveMappingOrIgnoreSameBlockRace(request.sessionId(), existingBlock.getBlockId(), request.messageId(), nextOrder, now);
        messageRepository.markStructured(List.of(request.messageId()), now);

        SessionBlock updatedBlock = new SessionBlock(
            existingBlock.getBlockId(),
            existingBlock.getSessionId(),
            existingBlock.getExternalBlockId(),
            existingBlock.getSequenceNo(),
            mapBlockType(request.targetBlock().blockType()),
            requireText(request.targetBlock().title(), "targetBlock.title"),
            requireText(request.targetBlock().summary(), "targetBlock.summary"),
            toJsonContent(request.content()),
            "ACTIVE",
            nextOrder,
            min(existingBlock.getMessageStartAt(), message.getMessageCreatedAt()),
            max(existingBlock.getMessageEndAt(), message.getMessageCreatedAt())
        );
        blockRepository.update(updatedBlock);

        return buildAppliedResponse(
            request,
            existingBlock.getBlockId(),
            "Appended message to target block."
        );
    }

    private McpSessionBlockEventIngestResponse handleFinalize(
        McpSessionBlockEventIngestRequest request,
        SessionContext context
    ) {
        SessionBlock existingBlock = requireBlock(request.sessionId(), request.targetBlock().mcpBlockId());
        SessionBlock updatedBlock = new SessionBlock(
            existingBlock.getBlockId(),
            existingBlock.getSessionId(),
            existingBlock.getExternalBlockId(),
            existingBlock.getSequenceNo(),
            mapBlockType(request.targetBlock().blockType()),
            requireText(request.targetBlock().title(), "targetBlock.title"),
            requireText(request.targetBlock().summary(), "targetBlock.summary"),
            toJsonContent(request.content()),
            "CLOSED",
            existingBlock.getSourceMessageCount(),
            existingBlock.getMessageStartAt(),
            existingBlock.getMessageEndAt()
        );
        blockRepository.update(updatedBlock);

        return buildAppliedResponse(
            request,
            existingBlock.getBlockId(),
            isActive(existingBlock)
                ? "Finalized target block."
                : "Target block was already closed."
        );
    }

    private McpSessionBlockEventIngestResponse handleReopen(
        McpSessionBlockEventIngestRequest request,
        SessionContext context
    ) {
        SessionBlock existingBlock = requireBlock(request.sessionId(), request.targetBlock().mcpBlockId());
        SessionBlock updatedBlock = new SessionBlock(
            existingBlock.getBlockId(),
            existingBlock.getSessionId(),
            existingBlock.getExternalBlockId(),
            existingBlock.getSequenceNo(),
            mapBlockType(request.targetBlock().blockType()),
            requireText(request.targetBlock().title(), "targetBlock.title"),
            requireText(request.targetBlock().summary(), "targetBlock.summary"),
            toJsonContent(request.content()),
            "ACTIVE",
            existingBlock.getSourceMessageCount(),
            existingBlock.getMessageStartAt(),
            existingBlock.getMessageEndAt()
        );
        blockRepository.update(updatedBlock);

        return buildAppliedResponse(
            request,
            existingBlock.getBlockId(),
            "Reopened target block."
        );
    }

    private SessionContext loadSessionContext(String sessionId) {
        LogicalSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("session not found: " + sessionId));

        Map<String, SyncedMessage> messageById = messageRepository.findAllBySessionId(sessionId).stream()
            .collect(Collectors.toMap(
                SyncedMessage::getMessageId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));

        Map<Long, List<SessionBlockMessage>> mappingsByBlockId = blockMessageRepository.findAllBySessionId(sessionId)
            .stream()
            .collect(Collectors.groupingBy(
                SessionBlockMessage::getBlockId,
                LinkedHashMap::new,
                Collectors.collectingAndThen(Collectors.toList(), mappings -> mappings.stream()
                    .sorted(Comparator.comparingInt(McpBlockEventIngestService::messageOrder))
                    .toList())
            ));

        return new SessionContext(session, messageById, mappingsByBlockId);
    }

    private McpSessionActiveBlockResponse toActiveBlockResponse(String sessionId, SessionBlock block) {
        List<SessionBlockMessage> mappings = blockMessageRepository.findAllBySessionId(sessionId)
            .stream()
            .filter(mapping -> block.getBlockId().equals(mapping.getBlockId()))
            .sorted(Comparator.comparingInt(McpBlockEventIngestService::messageOrder))
            .toList();

        List<String> messageIds = mappings.stream()
            .map(SessionBlockMessage::getMessageId)
            .toList();
        String lastMessageId = messageIds.isEmpty() ? null : messageIds.get(messageIds.size() - 1);

        return new McpSessionActiveBlockResponse(
            block.getSessionId(),
            block.getBlockId(),
            block.getExternalBlockId(),
            block.getSequenceNo(),
            block.getBlockType(),
            block.getTitle(),
            block.getSummary(),
            block.getStatus(),
            block.getSourceMessageCount(),
            messageIds,
            lastMessageId,
            toMapContent(block.getContentJson())
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

        if (unstructuredMessageCount == 0) {
            sessionRepository.updateSessionStatus(sessionId, "READY");
            sessionRepository.updateAnalysisStatus(sessionId, "DONE");
        } else {
            sessionRepository.updateSessionStatus(sessionId, "ANALYZING");
            sessionRepository.updateAnalysisStatus(sessionId, "RUNNING");
        }
        sessionRepository.updateAnalysisErrorMessage(sessionId, null);
    }

    private void markFailure(String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessionRepository.updateSessionStatus(sessionId, "FAILED");
        sessionRepository.updateAnalysisStatus(sessionId, "FAILED");
        sessionRepository.updateAnalysisErrorMessage(sessionId, message);
    }

    private McpSessionBlockEventIngestResponse buildDuplicateResponse(McpSessionBlockEventIngestRequest request) {
        Optional<SessionBlock> targetBlock = blockRepository.findByExternalBlockId(
            request.sessionId(),
            request.targetBlock().mcpBlockId()
        );
        return new McpSessionBlockEventIngestResponse(
            request.sessionId(),
            request.eventId(),
            normalizeOperation(request.operation()),
            "IGNORED",
            targetBlock.map(SessionBlock::getBlockId).orElse(null),
            request.targetBlock().mcpBlockId(),
            "Event already processed."
        );
    }

    private McpSessionBlockEventIngestResponse buildAppliedResponse(
        McpSessionBlockEventIngestRequest request,
        Long blockId,
        String message
    ) {
        return new McpSessionBlockEventIngestResponse(
            request.sessionId(),
            request.eventId(),
            normalizeOperation(request.operation()),
            "APPLIED",
            blockId,
            request.targetBlock().mcpBlockId(),
            message
        );
    }

    private SessionBlock requireBlock(String sessionId, String externalBlockId) {
        return blockRepository.findByExternalBlockId(sessionId, externalBlockId)
            .orElseThrow(() -> new IllegalArgumentException("target block not found: " + externalBlockId));
    }

    private SyncedMessage requireMessage(Map<String, SyncedMessage> messageById, String messageId) {
        SyncedMessage message = messageById.get(messageId);
        if (message == null) {
            throw new IllegalArgumentException("message not found: " + messageId);
        }
        return message;
    }

    private void saveMappingOrIgnoreSameBlockRace(
        String sessionId,
        Long blockId,
        String messageId,
        int messageOrder,
        LocalDateTime now
    ) {
        try {
            blockMessageRepository.saveAll(List.of(new SessionBlockMessage(
                null,
                sessionId,
                blockId,
                messageId,
                messageOrder,
                now
            )));
        } catch (DuplicateKeyException e) {
            if (blockMessageRepository.existsBlockMessage(blockId, messageId)) {
                return;
            }
            List<SessionBlockMessage> mappings = blockMessageRepository.findByMessageId(sessionId, messageId);
            if (mappings.stream().anyMatch(mapping -> blockId.equals(mapping.getBlockId()))) {
                return;
            }
            throw e;
        }
    }

    private String toJsonContent(Map<String, Object> content) {
        return toJsonValue(content == null ? Map.of() : content);
    }

    private Map<String, Object> toMapContent(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return Map.of();
        }
        return Map.of("rawJson", contentJson);
    }

    private static String normalizeOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        return switch (operation.trim().toUpperCase()) {
            case "CREATE_BLOCK", "APPEND", "FINALIZE_BLOCK", "REOPEN_BLOCK" -> operation.trim().toUpperCase();
            default -> throw new IllegalArgumentException("unsupported operation: " + operation);
        };
    }

    private static String normalizeBlockStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        return switch (status.trim().toUpperCase()) {
            case "ACTIVE", "CLOSED", "REPLACED" -> status.trim().toUpperCase();
            default -> throw new IllegalArgumentException("unsupported block status: " + status);
        };
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

    private static String toJsonValue(Object value) {
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
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object element : list) {
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
            for (int index = 0; index < length; index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(toJsonValue(java.lang.reflect.Array.get(value, index)));
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

    private static int nextMessageOrder(List<SessionBlockMessage> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return 1;
        }
        return mappings.stream()
            .map(SessionBlockMessage::getMessageOrder)
            .filter(order -> order != null)
            .max(Integer::compareTo)
            .orElse(0) + 1;
    }

    private static int messageOrder(SessionBlockMessage mapping) {
        return mapping.getMessageOrder() != null ? mapping.getMessageOrder() : Integer.MAX_VALUE;
    }

    private static LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private static LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static boolean isActive(SessionBlock block) {
        return block.getStatus() == null || "ACTIVE".equalsIgnoreCase(block.getStatus());
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }

    private record SessionContext(
        LogicalSession session,
        Map<String, SyncedMessage> messageById,
        Map<Long, List<SessionBlockMessage>> mappingsByBlockId
    ) {}
}
