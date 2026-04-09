package com.devlog.devlog.service.analysis;

import com.devlog.devlog.api.dto.response.SessionBlockResponse;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisService {

    private static final List<String> BLOCK_TYPES = List.of(
        "PROBLEM", "CAUSE", "TRIAL", "SOLUTION", "VERIFY"
    );
    private static final int BLOCK_CHUNK_SIZE = 3;

    private final LogicalSessionRepository sessionRepository;
    private final SyncedMessageRepository messageRepository;
    private final SessionBlockRepository blockRepository;
    private final SessionBlockMessageRepository blockMessageRepository;

    public AnalysisService(LogicalSessionRepository sessionRepository,
        SyncedMessageRepository messageRepository,
        SessionBlockRepository blockRepository,
        SessionBlockMessageRepository blockMessageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
        this.blockMessageRepository = blockMessageRepository;
    }

    @Transactional
    public SessionBlockResponse startAnalysis(String sessionId) {
        LogicalSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new RuntimeException("세션을 찾을 수 없습니다: " + sessionId));

        LocalDateTime now = LocalDateTime.now();
        sessionRepository.updateSessionStatus(sessionId, "ANALYZING");
        sessionRepository.updateAnalysisStatus(sessionId, "RUNNING");
        sessionRepository.updateAnalysisErrorMessage(sessionId, null);

        try {
            List<SyncedMessage> allMessages = messageRepository.findAllBySessionId(sessionId);
            List<SyncedMessage> targetMessages = allMessages.stream()
                .filter(message -> !isStructured(message.getStructureStatus()))
                .toList();

            if (targetMessages.isEmpty()) {
                refreshSessionState(sessionId, session, now, 0);
                sessionRepository.updateSessionStatus(sessionId, "READY");
                sessionRepository.updateAnalysisStatus(sessionId, "DONE");
                return null;
            }

            int nextSequenceNo = nextSequenceNo(blockRepository.findAllBySessionId(sessionId));
            List<String> structuredMessageIds = new ArrayList<>();
            int createdBlockCount = 0;

            for (int start = 0; start < targetMessages.size(); start += BLOCK_CHUNK_SIZE) {
                List<SyncedMessage> chunk = targetMessages.subList(start,
                    Math.min(start + BLOCK_CHUNK_SIZE, targetMessages.size()));
                if (chunk.isEmpty()) {
                    continue;
                }

                int sequenceNo = nextSequenceNo++;
                SessionBlock block = new SessionBlock(
                    null,
                    sessionId,
                    sequenceNo,
                    blockTypeFor(sequenceNo),
                    buildTitle(sequenceNo, chunk),
                    buildSummary(chunk),
                    buildContentJson(chunk),
                    "ACTIVE",
                    chunk.size(),
                    chunk.get(0).getMessageCreatedAt(),
                    chunk.get(chunk.size() - 1).getMessageCreatedAt()
                );
                Long blockId = blockRepository.save(block);
                createdBlockCount++;

                List<SessionBlockMessage> mappings = new ArrayList<>(chunk.size());
                for (int order = 0; order < chunk.size(); order++) {
                    SyncedMessage message = chunk.get(order);
                    mappings.add(new SessionBlockMessage(
                        null,
                        sessionId,
                        blockId,
                        message.getMessageId(),
                        order + 1,
                        now
                    ));
                    structuredMessageIds.add(message.getMessageId());
                }
                blockMessageRepository.saveAll(mappings);
            }

            if (!structuredMessageIds.isEmpty()) {
                messageRepository.markStructured(structuredMessageIds, now);
            }

            refreshSessionState(sessionId, session, now, createdBlockCount);
            sessionRepository.updateSessionStatus(sessionId, "READY");
            sessionRepository.updateAnalysisStatus(sessionId, "DONE");

            List<SessionBlock> blocks = blockRepository.findAllBySessionId(sessionId);
            SessionBlock latestBlock = blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
            return latestBlock != null
                ? new SessionBlockResponse(
                    latestBlock.getBlockId(),
                    latestBlock.getSessionId(),
                    latestBlock.getSequenceNo(),
                    latestBlock.getBlockType(),
                    latestBlock.getTitle(),
                    latestBlock.getSummary(),
                    latestBlock.getSourceMessageCount(),
                    structuredMessageIds
                )
                : null;
        } catch (Exception e) {
            sessionRepository.updateSessionStatus(sessionId, "FAILED");
            sessionRepository.updateAnalysisStatus(sessionId, "FAILED");
            sessionRepository.updateAnalysisErrorMessage(sessionId, safeMessage(e));
            sessionRepository.updateTimestamps(sessionId, session.getLastMessageAt(),
                session.getLastSyncedAt(), now);
            return null;
        }
    }

    private void refreshSessionState(String sessionId, LogicalSession session, LocalDateTime now,
        int createdBlockCount) {
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

    private static int nextSequenceNo(List<SessionBlock> blocks) {
        return blocks.stream()
            .map(SessionBlock::getSequenceNo)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0) + 1;
    }

    private static boolean isStructured(String structureStatus) {
        return structureStatus != null && "STRUCTURED".equalsIgnoreCase(structureStatus);
    }

    private static String blockTypeFor(int sequenceNo) {
        return BLOCK_TYPES.get((sequenceNo - 1) % BLOCK_TYPES.size());
    }

    private static String buildTitle(int sequenceNo, List<SyncedMessage> messages) {
        String headline = summarize(messages.get(0).getContent(), 24);
        return "블록 " + sequenceNo + (headline.isBlank() ? "" : " - " + headline);
    }

    private static String buildSummary(List<SyncedMessage> messages) {
        return summarize(messages.stream()
            .map(SyncedMessage::getContent)
            .filter(content -> content != null && !content.isBlank())
            .collect(Collectors.joining(" ")), 120);
    }

    private static String buildContentJson(List<SyncedMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            SyncedMessage message = messages.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                .append("\"messageId\":\"").append(jsonEscape(message.getMessageId())).append("\",")
                .append("\"role\":\"").append(jsonEscape(defaultString(message.getRole(), "USER"))).append("\",")
                .append("\"content\":\"").append(jsonEscape(defaultString(message.getContent(), ""))).append("\",")
                .append("\"messageCreatedAt\":\"").append(message.getMessageCreatedAt()).append("\"")
                .append('}');
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String summarize(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "...";
    }

    private static String jsonEscape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}
