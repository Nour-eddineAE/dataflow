import {Tooltip} from '@mantine/core';
import type {RunRuntime} from '../api/types';
import {runtimeDetail, runtimeLabel} from '../lib/runtime';
import {Meta} from './PageHeader';

export function RuntimeMeta({runtime}: { runtime?: RunRuntime | null }) {
    if (!runtime) return null;
    return (
        <Meta label="Runtime">
            <Tooltip label={runtimeDetail(runtime)}><span>{runtimeLabel(runtime)}</span></Tooltip>
        </Meta>
    );
}
