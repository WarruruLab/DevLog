import { Navigate, createBrowserRouter } from 'react-router-dom';
import { App } from './App';
import { DraftPage } from '../pages/DraftPage';
import { SessionDetailPage } from '../pages/SessionDetailPage';
import { SessionListPage } from '../pages/SessionListPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      { index: true, element: <Navigate to="/sessions" replace /> },
      { path: 'sessions', element: <SessionListPage /> },
      { path: 'sessions/:sessionId', element: <SessionDetailPage /> },
      { path: 'drafts/:draftId', element: <DraftPage /> },
    ],
  },
]);
