import {Box, SegmentedControl, Stack} from '@mantine/core';
import {useState} from 'react';
import {DiffView} from '../../components/DiffView';
import {Empty} from '../../components/ErrorState';

export function FileDiff({saved, committed, current}: { saved?: string; committed?: string; current: string }) {
    const [against, setAgainst] = useState<'saved' | 'committed'>(saved !== undefined ? 'saved' : 'committed');
    const original = against === 'saved' ? saved : committed;
    return (
        <Stack gap={0} h="100%">
            <Box p="sm" style={{flex: 'none'}}>
                <SegmentedControl size="xs" value={against}
                                  onChange={(value) => setAgainst(value as 'saved' | 'committed')}
                                  data={[{
                                      value: 'saved',
                                      label: 'Saved',
                                      disabled: saved === undefined
                                  }, {value: 'committed', label: 'Last commit'}]}/>
            </Box>
            <Box style={{flex: 1, minHeight: 0}}>
                {original === undefined ? <Empty title="Never committed"/>
                    : original === current ? <Empty title="No differences"/>
                        : <DiffView original={original} current={current}/>}
            </Box>
        </Stack>
    );
}
