import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import './app/styles/admin-shell.css';
import './app/styles/admin-ui.css';
import './app/styles/admin-page-layout.css';
import './app/styles/dashboard-atelier.css';
import './app/styles/admin-auth.css';
import { AuthProvider } from './app/providers/AuthProvider';
import { App } from './app/App';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </StrictMode>
);
