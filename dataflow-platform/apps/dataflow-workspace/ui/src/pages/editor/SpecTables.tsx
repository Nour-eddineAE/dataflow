import {useDebouncedValue} from '@mantine/hooks';
import {useMemo} from 'react';
import {useTypedSpecTree} from '../../api/hooks';
import {RelationBrowser} from '../../components/RelationBrowser';
import {TYPING_PAUSE} from '../../lib/timing';

export function SpecTables({dataset, text, onInsert}: {
    dataset: string;
    text: string;
    onInsert: (text: string) => void;
}) {
    const [typed] = useDebouncedValue(text, TYPING_PAUSE);
    const tree = useTypedSpecTree(typed);
    const spec = useMemo(() => tree.data && {...tree.data, dataset}, [tree.data, dataset]);
    return <RelationBrowser spec={spec} error={tree.error} onInsert={onInsert}/>;
}
