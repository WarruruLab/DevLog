import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { formatCount, formatDateTime } from '../lib/format';
import { statusTone } from '../lib/session';
import type { RequestStatus, SessionSummary } from '../types/api';

export function SessionListPage() {
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [status, setStatus] = useState<RequestStatus>('loading');
  const [error, setError] = useState<string | null>(null);

  async function loadSessions() {
    setStatus('loading');
    setError(null);
    try {
      const payload = await api.getSessions();
      setSessions(payload);
      setStatus(payload.length > 0 ? 'success' : 'idle');
    } catch (loadError) {
      setStatus('error');
      setError(loadError instanceof Error ? loadError.message : '세션 목록을 불러오지 못했습니다.');
    }
  }

  useEffect(() => {
    void loadSessions();
  }, []);

  return (
    <section className="page page--sessions">
      <div className="page-head">
        <div>
          <span className="eyebrow">Sessions</span>
          <h1>작업할 세션을 고르세요</h1>
          <p className="page-copy">
            홈에서는 세션 목록만 보여줍니다. 세션에 들어가면 구조화 상태를 확인하고 블록을 고를 수 있습니다.
          </p>
        </div>
        <button className="button button--secondary" onClick={() => void loadSessions()}>
          목록 새로고침
        </button>
      </div>

      {status === 'loading' ? (
        <div className="panel panel--state">
          <div className="loader" />
          <p>세션 목록을 불러오고 있습니다.</p>
        </div>
      ) : null}

      {status === 'error' ? (
        <div className="panel panel--state panel--danger">
          <h2>세션 목록을 불러오지 못했습니다</h2>
          <p>{error}</p>
        </div>
      ) : null}

      {status !== 'loading' && status !== 'error' && sessions.length === 0 ? (
        <div className="panel panel--state">
          <h2>아직 세션이 없습니다</h2>
          <p>DevTalk 동기화가 끝나면 목록이 여기에 표시됩니다.</p>
        </div>
      ) : null}

      {sessions.length > 0 ? (
        <div className="session-list">
          {sessions.map((session) => {
            const tone = statusTone(session.sessionStatus, session.analysisStatus);
            return (
              <Link className="session-card" key={session.sessionId} to={`/sessions/${session.sessionId}`}>
                <div className="session-card__head">
                  <div>
                    <h2>{session.title || session.sessionId}</h2>
                    <p>{session.sourceSessionId}</p>
                  </div>
                  <span className={`badge badge--${tone}`}>
                    {session.unstructuredMessageCount > 0 ? '구조화 중' : '구조화 완료'}
                  </span>
                </div>
                <div className="session-card__meta">
                  <span>{formatCount(session.totalMessageCount, '개 메시지')}</span>
                  <span>{formatCount(session.blockCount, '개 블록')}</span>
                  <span>{formatCount(session.unstructuredMessageCount, '개 미구조화')}</span>
                </div>
                <div className="session-card__foot">
                  <strong>{formatDateTime(session.lastSyncedAt ?? session.lastMessageAt)}</strong>
                  <span>{session.analysisStatus}</span>
                </div>
              </Link>
            );
          })}
        </div>
      ) : null}
    </section>
  );
}
