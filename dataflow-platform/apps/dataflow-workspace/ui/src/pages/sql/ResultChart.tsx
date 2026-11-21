import {Box, Center, Group, SegmentedControl, Select, Stack, Text, useComputedColorScheme} from '@mantine/core';
import {useMemo} from 'react';
import type {QueryResult} from '../../api/types';
import {CHART_KINDS, type ChartChoice, chartAxes, type ChartKind} from '../../lib/chartChoice';
import {resultOption} from '../../lib/boardOptions';
import type {Scheme} from '../../lib/chartTheme';
import {EChart} from '../../components/EChart';

const CHART_ROWS = 500;
const KIND_LABELS: Record<ChartKind, string> = {bar: 'Bar', line: 'Line'};

export function ResultChart({result, choice, onChoice}: { result: QueryResult; choice?: ChartChoice; onChoice: (choice: ChartChoice) => void }) {
    const {names, numeric, x, y, kind, whole} = chartAxes(result, choice);
    const scheme = useComputedColorScheme('dark') as Scheme;
    const option = useMemo(() => {
        if (!x || !y) return undefined;
        const rows = result.rows.slice(0, CHART_ROWS);
        const xi = names.indexOf(x);
        const yi = names.indexOf(y);
        return resultOption({labels: rows.map((row) => String(row[xi])), values: rows.map((row) => Number(row[yi])), name: y, kind, whole}, scheme);
    }, [result, x, y, names, kind, whole, scheme]);
    if (!numeric.length) return <Center h="100%"><Text size="sm" c="dimmed">No numeric column to plot</Text></Center>;
    return (
        <Stack h="100%" gap="sm">
            <Group gap="sm">
                <Select size="xs" data={names} value={x ?? null} onChange={(next) => next && onChoice({x: next, y, kind})} w={180}
                        aria-label="X axis" allowDeselect={false} leftSection={<Text fz="micro" c="dimmed">X</Text>}/>
                <Select size="xs" data={numeric} value={y ?? null} onChange={(next) => next && onChoice({x, y: next, kind})} w={180}
                        aria-label="Y axis" allowDeselect={false} leftSection={<Text fz="micro" c="dimmed">Y</Text>}/>
                <SegmentedControl size="xs" aria-label="Chart type" value={kind}
                                  data={CHART_KINDS.map((value) => ({value, label: KIND_LABELS[value]}))}
                                  onChange={(next) => onChoice({x, y, kind: next as ChartKind})}/>
            </Group>
            {option && <Box style={{flex: 1, minHeight: 0}}><EChart option={option} height="100%"/></Box>}
        </Stack>
    );
}
