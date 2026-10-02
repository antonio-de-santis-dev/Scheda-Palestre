import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import '@fontsource/barlow/400.css';
import '@fontsource/barlow/500.css';
import '@fontsource/barlow/600.css';
import '@fontsource/barlow/700.css';
import '@fontsource/barlow-condensed/600.css';
import '@fontsource/barlow-condensed/700.css';
import './shared/styles/global.css';
import './shared/styles/design.css';
import { AppProviders } from './app/providers/AppProviders';
import { routes } from './app/router/routes';

if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => { void navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' }).catch(() => { /* App remains usable without PWA support. */ }); });
}

const router = createBrowserRouter(routes);

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root element #root not found');
}

createRoot(container).render(
  <StrictMode>
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
);
