import {Group, Select, TextInput} from '@mantine/core';
import {IconSearch} from '../../icons';
import {useState} from 'react';
import {useLineage} from '../../api/hooks';
import {Panel} from '../../components/Panel';
import {WhyDrawer} from '../../components/WhyDrawer';
import {ICON_SMALL} from '../../lib/icons';
import {Action} from '../../components/actions/Action';

export function WhyTab({dataset, keys}: { dataset: string; keys: string[] }) {
    const {data: lineage} = useLineage(dataset);
    const [value, setValue] = useState('');
    const [column, setColumn] = useState<string | null>(null);
    const [asked, setAsked] = useState<{ id: string; column?: string }>();
    const columns = lineage?.tables.find((table) => table.id === 'silver')?.columns
        .filter((entry) => entry.role === 'declared' || entry.role === 'derived').map((entry) => entry.name) ?? [];
    const ask = () => value.trim() && setAsked({id: value.trim(), column: column ?? undefined});

    return (
        <Panel>
            <Group align="flex-end" wrap="nowrap" maw={760}>
                <TextInput label={keys.length === 1 ? keys[0] : 'factUniqueId'} value={value}
                           onChange={(event) => setValue(event.currentTarget.value)}
                           leftSection={<IconSearch {...ICON_SMALL}/>} style={{flex: 1}}
                           onKeyDown={(event) => event.key === 'Enter' && ask()}/>
                <Select label="Column" placeholder="All" data={columns} value={column} onChange={setColumn} clearable
                        searchable w={220}/>
                <Action tone="primary" label="Trace" disabled={!value.trim()} onClick={ask}/>
            </Group>
            <WhyDrawer dataset={dataset} id={asked?.id} column={asked?.column} onClose={() => setAsked(undefined)}/>
        </Panel>
    );
}
