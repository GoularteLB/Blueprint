import { createBrowserRouter } from 'react-router-dom';
import { Layout } from '../shared/ui/Layout';
import { UploadPage } from '../features/imports/pages/UploadPage';
import { DashboardPage } from '../features/analytics/pages/DashboardPage';
import { ExplorerPage } from '../features/explorer/pages/ExplorerPage';

export const router = createBrowserRouter(
  [
    {
      element: <Layout />,
      children: [
        { path: '/', element: <UploadPage /> },
        { path: '/dashboard', element: <DashboardPage /> },
        { path: '/explorer', element: <ExplorerPage /> },
      ],
    },
  ],
  {
    future: {
      v7_relativeSplatPath: true,
      v7_fetcherPersist: true,
      v7_normalizeFormMethod: true,
      v7_partialHydration: true,
      v7_skipActionErrorRevalidation: true,
    },
  },
);
