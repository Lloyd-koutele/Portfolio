import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { Providers } from '@shared/app/Providers';
import { routes } from './routes';

const router = createBrowserRouter(routes);

export function App() {
  return (
    <Providers>
      <RouterProvider router={router} />
    </Providers>
  );
}
