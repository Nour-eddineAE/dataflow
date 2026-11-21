import {createBrowserRouter, createRoutesFromElements, Navigate, Route} from 'react-router-dom';
import {Shell} from './components/Shell';
import {HomePage} from './pages/HomePage';
import {page} from './routes';
import {PAGE} from './lib/links';
import {loadQueryLink} from './pages/sql/queryLink';
import {announceGitHubLink} from './components/gitHubLink';

const AdminPage = page('AdminPage');
const AlertsPage = page('AlertsPage');
const AutomationPage = page('AutomationPage');
const QueriesPage = page('QueriesPage');
const ServicesPage = page('ServicesPage');
const CatalogPage = page('CatalogPage');
const BoardPage = page('BoardPage');
const DashboardsPage = page('DashboardsPage');
const NotFoundPage = page('NotFoundPage');
const PipelinePage = page('PipelinePage');
const PipelinesPage = page('PipelinesPage');
const QualityPage = page('QualityPage');
const RunPage = page('RunPage');
const RunsPage = page('RunsPage');
const WorkspacePage = page('WorkspacePage');
const NewPipelinePage = page('NewPipelinePage');
const SqlPage = page('SqlPage');
const StoragePage = page('StoragePage');

export const router = createBrowserRouter(createRoutesFromElements(
    <Route element={<Shell/>}>
        <Route index element={<HomePage/>}/>
        <Route path="catalog" element={<CatalogPage/>}/>
        <Route path="catalog/lake/:dataset/:table" element={<CatalogPage/>}/>
        <Route path="catalog/serving/:served" element={<CatalogPage/>}/>
        <Route path="sql" element={<SqlPage/>} loader={loadQueryLink}/>
        <Route path="queries" element={<QueriesPage/>}/>
        <Route path="alerts" element={<AlertsPage/>}/>
        <Route path="pipelines" element={<PipelinesPage/>}/>
        <Route path="pipelines/new" element={<NewPipelinePage/>}/>
        <Route path="pipelines/:dataset" element={<PipelinePage/>}/>
        <Route path="pipelines/:dataset/:tab" element={<PipelinePage/>}/>
        <Route path="workspace" element={<WorkspacePage/>}/>
        <Route path="folders/*" element={<Navigate to={PAGE.workspace} replace/>}/>
        <Route path="runs" element={<RunsPage/>}/>
        <Route path="runs/:runId" element={<RunPage/>}/>
        <Route path="automation" element={<AutomationPage/>}/>
        <Route path="quality" element={<QualityPage/>}/>
        <Route path="dashboards" element={<DashboardsPage/>}/>
        <Route path="dashboards/grafana/:uid" element={<DashboardsPage/>}/>
        <Route path="dashboards/:dataset" element={<BoardPage/>}/>
        <Route path="admin" element={<AdminPage/>} loader={announceGitHubLink}/>
        <Route path="services" element={<ServicesPage/>}/>
        <Route path="storage" element={<StoragePage/>}/>
        <Route path="*" element={<NotFoundPage/>}/>
    </Route>,
));
