import {type ComponentType, lazy} from 'react';

type Loader = () => Promise<Record<string, ComponentType>>;

const loaders = {
    AdminPage: () => import('./pages/AdminPage'),
    AlertsPage: () => import('./pages/AlertsPage'),
    AutomationPage: () => import('./pages/AutomationPage'),
    BoardPage: () => import('./pages/BoardPage'),
    CatalogPage: () => import('./pages/CatalogPage'),
    DashboardsPage: () => import('./pages/DashboardsPage'),
    NewPipelinePage: () => import('./pages/NewPipelinePage'),
    NotFoundPage: () => import('./pages/NotFoundPage'),
    PipelinePage: () => import('./pages/PipelinePage'),
    PipelinesPage: () => import('./pages/PipelinesPage'),
    QueriesPage: () => import('./pages/QueriesPage'),
    QualityPage: () => import('./pages/QualityPage'),
    RunPage: () => import('./pages/RunPage'),
    RunsPage: () => import('./pages/RunsPage'),
    ServicesPage: () => import('./pages/ServicesPage'),
    SqlPage: () => import('./pages/SqlPage'),
    StoragePage: () => import('./pages/StoragePage'),
    WorkspacePage: () => import('./pages/WorkspacePage'),
} satisfies Record<string, Loader>;

type Name = keyof typeof loaders;

export const page = (name: Name) =>
    lazy(() => (loaders[name] as Loader)().then((module) => ({default: module[name]})));

export function preloadPages() {
    Object.values(loaders).forEach((load) => {
        load().catch(() => undefined);
    });
}
