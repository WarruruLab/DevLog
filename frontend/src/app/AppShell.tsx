import type { PropsWithChildren } from 'react';
import { Link, useLocation } from 'react-router-dom';

export function AppShell({ children }: PropsWithChildren) {
  const location = useLocation();
  const isDraftPage = location.pathname.startsWith('/drafts/');

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar__brand">
          <Link className="brand-mark" to="/sessions" aria-label="DevLog home" />
          <div>
            <Link className="brand-title" to="/sessions">
              DevLog
            </Link>
            <p className="brand-copy">
              DevTalk 세션을 블록으로 정리하고 초안을 만드는 작업 공간
            </p>
          </div>
        </div>
        <span className={`status-pill ${isDraftPage ? 'status-pill--draft' : 'status-pill--flow'}`}>
          {isDraftPage ? 'Markdown result' : 'Structured writing flow'}
        </span>
      </header>
      <main className="app-shell__body">{children}</main>
    </div>
  );
}
