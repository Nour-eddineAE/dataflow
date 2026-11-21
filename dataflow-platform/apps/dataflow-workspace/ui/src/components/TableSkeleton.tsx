import {Skeleton, Stack} from '@mantine/core';
import {Panel} from './Panel';

const HEADER_HEIGHT = 14;
const HEADER_WIDTH = '40%';
const ROW_HEIGHT = 12;
const ROW_WIDTHS = ['70%', '58%', '46%'];

export function TableSkeleton({rows = 6}: { rows?: number }) {
    return (
        <Panel flush>
            <Stack gap={0}>
                <Skeleton h={HEADER_HEIGHT} w={HEADER_WIDTH} my="md" radius="sm"/>
                {Array.from({length: rows}, (_, index) => (
                    <div key={index} className="df-skeleton-row">
                        <Skeleton h={ROW_HEIGHT} w={ROW_WIDTHS[index % ROW_WIDTHS.length]} radius="sm"/>
                    </div>
                ))}
            </Stack>
        </Panel>
    );
}
