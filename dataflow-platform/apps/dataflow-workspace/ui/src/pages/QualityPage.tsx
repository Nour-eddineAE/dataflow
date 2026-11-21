import {Group, SegmentedControl} from '@mantine/core';
import {useState} from 'react';
import {useQuality} from '../api/hooks';
import {Empty, ErrorState} from '../components/ErrorState';
import {TableSkeleton} from '../components/TableSkeleton';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {QualityRules} from '../components/QualityRules';
import {isFailing, isPassing} from '../lib/quality';

export function QualityPage() {
    const {data, error, isLoading} = useQuality();
    const [filter, setFilter] = useState('all');
    const rules = data ?? [];
    const shown = rules.filter((rule) => {
        if (filter === 'failing') return isFailing(rule);
        if (filter === 'passing') return isPassing(rule);
        return true;
    });

    return (
        <Page>
            <PageHeader title="Quality"/>
            <Group mb="md">
                <SegmentedControl value={filter} onChange={setFilter} data={[{value: 'all', label: 'All'}, {
                    value: 'failing',
                    label: 'Failing'
                }, {value: 'passing', label: 'Passing'}]}/>
            </Group>
            {isLoading ? <TableSkeleton/> : error ? <ErrorState error={error}/>
                : !shown.length ? <Empty title={rules.length ? 'No rule matches' : 'No rules'}/>
                    : <Panel flush><QualityRules rules={shown} showDataset/></Panel>}
        </Page>
    );
}
