import { Outlet } from 'react-router-dom';
import { AppShell } from './AppShell';

export function App() {
  return (
    <AppShell>
      <Outlet />
    </AppShell>
  );
}
