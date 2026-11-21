import {Box, Group, ScrollArea, Text, TextInput} from '@mantine/core';
import {IconDownload, IconSearch} from '../icons';
import {type ReactNode, useDeferredValue, useEffect, useMemo, useRef, useState} from 'react';
import type {OutputLine} from '../api/types';
import {downloadText} from '../lib/download';
import {formatNumber} from '../lib/format';
import {Empty} from './ErrorState';
import {ICON_SMALL} from '../lib/icons';
import {IconAction} from './actions/IconAction';
import {COLOR} from '../lib/colors';
import {useRemainingHeight} from './layout/useRemainingHeight';
import {isTableLine} from '../lib/outputLine';

const PINNED_WITHIN = 24;
const MILLISECOND_DIGITS = 3;

const LEVEL_COLOR: Record<string, string> = {ERROR: COLOR.danger, WARN: COLOR.warning};

export function OutputLog({lines, name, follow, toolbar}: {
    lines: OutputLine[];
    name: string;
    follow: boolean;
    toolbar?: ReactNode
}) {
    const [filter, setFilter] = useState('');
    const query = useDeferredValue(filter.trim().toLowerCase());
    const shown = useMemo(() => (query ? lines.filter((line) => matches(line, query)) : lines), [lines, query]);
    const viewport = useRef<HTMLDivElement>(null);
    const pinned = useRef(true);
    const [fill, height] = useRemainingHeight();

    useEffect(() => {
        const element = viewport.current;
        if (follow && pinned.current && element) element.scrollTop = element.scrollHeight;
    }, [shown, follow]);

    return (
        <Box className="df-output">
            <Group justify="space-between" px="sm" h={44} wrap="nowrap" className="df-output-bar">
                <Group gap="sm" wrap="nowrap">{toolbar}</Group>
                <Group gap="snug" wrap="nowrap">
                    <Text size="xs" c="dimmed" className="df-num">
                        {query ? `${formatNumber(shown.length)} of ${formatNumber(lines.length)}` : formatNumber(lines.length)} lines
                    </Text>
                    <TextInput size="xs" w={200} placeholder="Filter" value={filter}
                               onChange={(event) => setFilter(event.currentTarget.value)}
                               leftSection={<IconSearch {...ICON_SMALL}/>} aria-label="Filter lines"/>
                    <IconAction label="Download" icon={IconDownload} disabled={!lines.length}
                                onClick={() => downloadText(name, 'log', lines.map(asText).join('\n'))}/>
                </Group>
            </Group>
            {shown.length ? (
                <ScrollArea h={height} ref={fill} viewportRef={viewport} scrollbarSize={8}
                            onScrollPositionChange={({y}) => {
                                const element = viewport.current;
                                if (element) pinned.current = element.scrollHeight - y - element.clientHeight < PINNED_WITHIN;
                            }}>
                    <Box py="snug">
                        {shown.map((line, position) => <Line key={position} line={line}/>)}
                    </Box>
                </ScrollArea>
            ) : (
                <Box h={height} ref={fill}><Empty
                    title={lines.length ? 'No matching lines' : follow ? 'No output yet' : 'No output'}/></Box>
            )}
        </Box>
    );
}

function Line({line}: { line: OutputLine }) {
    const [open, setOpen] = useState(false);
    const [first, ...rest] = line.message.split('\n');
    const level = line.level ?? '';
    return (
        <div className="df-output-line" data-level={level || undefined}>
            <span className="df-output-time">{time(line.timestamp)}</span>
            <span className="df-output-level" style={{color: LEVEL_COLOR[level]}}>{level === 'INFO' ? '' : level}</span>
            <span className="df-output-source" title={line.logger ?? undefined}>{line.flow ?? line.logger ?? ''}</span>
            <span className="df-output-message" data-table={isTableLine(first) || undefined}>
        {first}
                {rest.length > 0 && (
                    <>
                        {' '}
                        <button type="button" className="df-output-more" onClick={() => setOpen(!open)}>
                            {open ? 'less' : `${rest.length} more`}
                        </button>
                        {open && <span className="df-output-rest">{rest.join('\n')}</span>}
                    </>
                )}
      </span>
        </div>
    );
}

function matches(line: OutputLine, query: string) {
    return [line.message, line.level, line.logger, line.flow].some((value) => value?.toLowerCase().includes(query));
}

function time(timestamp?: string | null) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    if (Number.isNaN(date.getTime())) return '';
    return `${date.toLocaleTimeString('en-GB', {hour12: false})}.${String(date.getMilliseconds()).padStart(MILLISECOND_DIGITS, '0')}`;
}

function asText(line: OutputLine) {
    return [line.timestamp ?? '', line.level ?? '', line.logger ?? '', line.flow ? `[${line.flow}]` : '', line.message]
        .filter(Boolean).join(' ');
}
