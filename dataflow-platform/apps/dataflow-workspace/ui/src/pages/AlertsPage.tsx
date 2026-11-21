
import {Tabs} from '@mantine/core';
import {useSearchParams} from 'react-router-dom';
import {IconBellCheck, IconExternalLink} from '../icons';
import {useAlerts, useQueryAlerts, useStatus} from '../api/hooks';
import {AddQueryAlert, QueryAlertsTable} from '../components/QueryAlerts';
import {AlertsTable} from '../components/AlertsTable';
import {Empty, ErrorState} from '../components/ErrorState';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {TableSkeleton} from '../components/TableSkeleton';
import {ICON_HERO} from '../lib/icons';
import {Action} from '../components/actions/Action';

const QUERIES = 'queries';

export function AlertsPage() {
    const {data, error, isLoading} = useAlerts();
    const {data: status} = useStatus();
    const queryAlerts = useQueryAlerts();
    const [params, setParams] = useSearchParams();
    const tab = params.get('tab') === QUERIES ? QUERIES : 'firing';
    const grafana = status?.links.dashboards;
    return (
        <Page>
            <PageHeader title="Alerts" actions={<>
                {grafana && <Action icon={IconExternalLink} label="Rules" href={grafana} external/>}
                <AddQueryAlert/>
            </>}/>
            <Tabs value={tab} onChange={(value) => setParams(value === QUERIES ? {tab: QUERIES} : {}, {replace: true})} mb="md">
                <Tabs.List>
                    <Tabs.Tab value="firing">Firing</Tabs.Tab>
                    <Tabs.Tab value={QUERIES}>Query alerts</Tabs.Tab>
                </Tabs.List>
            </Tabs>
            {tab === QUERIES ? (
                <Panel flush>
                    {queryAlerts.isLoading ? <TableSkeleton rows={3}/> : queryAlerts.error ? <ErrorState error={queryAlerts.error}/>
                        : !queryAlerts.data?.length ? <Empty icon={<IconBellCheck {...ICON_HERO}/>} title="No query alerts"/>
                            : <QueryAlertsTable alerts={queryAlerts.data}/>}
                </Panel>
            ) : (
                <Panel flush>
                    {isLoading ? <TableSkeleton rows={3}/> : error ? <ErrorState error={error}/>
                        : !data?.available ? <Empty title={data?.reason ?? 'Alerts are not available'}/>
                            : !data.alerts.length ? <Empty icon={<IconBellCheck {...ICON_HERO}/>} title="Nothing firing"/>
                                : <AlertsTable alerts={data.alerts}/>}
                </Panel>
            )}
        </Page>
    );
}
