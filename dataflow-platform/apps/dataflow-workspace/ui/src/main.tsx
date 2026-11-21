import '@fontsource-variable/inter';
import '@fontsource-variable/jetbrains-mono';
import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import '@mantine/spotlight/styles.css';
import './styles.css';
import {MantineProvider} from '@mantine/core';
import {Notifications} from '@mantine/notifications';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import {RouterProvider} from 'react-router-dom';
import {router} from './App';
import {cssVariables, theme} from './theme';
import {FRESH} from './lib/timing';

const queries = new QueryClient({
    defaultOptions: {
        queries: {refetchOnWindowFocus: false, retry: 1, staleTime: FRESH.moment},
    },
});

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <MantineProvider theme={theme} cssVariablesResolver={cssVariables} defaultColorScheme="auto">
            <Notifications position="bottom-right" limit={4}/>
            <QueryClientProvider client={queries}>
                <RouterProvider router={router}/>
            </QueryClientProvider>
        </MantineProvider>
    </StrictMode>,
);
