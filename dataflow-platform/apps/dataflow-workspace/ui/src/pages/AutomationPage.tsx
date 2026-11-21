import {useAutomation} from '../api/hooks';
import {Empty, ErrorState} from '../components/ErrorState';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {TableSkeleton} from '../components/TableSkeleton';
import {TriggersTable} from '../components/TriggersTable';
import {sortTriggers} from '../lib/automation';

export function AutomationPage() {
    const {data, error, isLoading} = useAutomation();
    return (
        <Page>
            <PageHeader title="Automation"/>
            <Panel flush>
                {isLoading ? <TableSkeleton/> : error ? <ErrorState error={error}/>
                    : !data?.length ? <Empty title="Nothing starts on its own"/>
                        : <TriggersTable triggers={sortTriggers(data)}/>}
            </Panel>
        </Page>
    );
}
