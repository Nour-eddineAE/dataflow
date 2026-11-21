import {CopyButton, Group, Popover, ScrollArea, Stack, Text} from '@mantine/core';
import {useState} from 'react';
import {IconCheck, IconCopy} from '../../icons';
import {columnText} from '../../lib/format';
import {prettyValue} from '../../lib/grid';
import {ICON_BIG} from '../../lib/icons';
import {IconAction} from '../actions/IconAction';

const CHARACTER_WIDTH = 7.5;

type Props = {
    value: unknown;
    type: string;
    width: number;
    numeric: boolean;
    expandOnDoubleClick: boolean;
    selected: boolean;
    onSelect?: () => void;
};

export function GridCell({value, type, width, numeric, expandOnDoubleClick, selected, onSelect}: Props) {
    const [opened, setOpened] = useState(false);
    const text = columnText(value, type);
    const isNull = value === null || value === undefined;
    const classes = `df-grid-cell${numeric ? ' num' : ''}${isNull ? ' null' : ''}${selected ? ' selected' : ''}`;
    if (text.length < width / CHARACTER_WIDTH) {
        return <div className={classes} style={{width}} onClick={onSelect}>{text}</div>;
    }
    return (
        <Popover width={420} position="bottom-start" shadow="md" withinPortal opened={opened} onChange={setOpened}>
            <Popover.Target>
                <div
                    className={classes}
                    style={{width, cursor: expandOnDoubleClick ? undefined : 'zoom-in'}}
                    title={expandOnDoubleClick ? 'Double-click to see the whole value' : undefined}
                    onClick={expandOnDoubleClick ? undefined : (event) => {
                        event.stopPropagation();
                        onSelect?.();
                        setOpened((open) => !open);
                    }}
                    onDoubleClick={expandOnDoubleClick ? (event) => {
                        event.stopPropagation();
                        setOpened(true);
                    } : undefined}
                >
                    {text}
                </div>
            </Popover.Target>
            <Popover.Dropdown>
                <Stack gap="snug">
                    <Group justify="flex-end">
                        <CopyButton value={text}>
                            {({copied, copy}) => (
                                <IconAction {...ICON_BIG} label={copied ? 'Copied' : 'Copy'} icon={copied ? IconCheck : IconCopy} onClick={copy}/>
                            )}
                        </CopyButton>
                    </Group>
                    <ScrollArea.Autosize mah={260}>
                        <Text size="xs" className="df-mono" style={{whiteSpace: 'pre-wrap', wordBreak: 'break-all'}}>
                            {prettyValue(value)}
                        </Text>
                    </ScrollArea.Autosize>
                </Stack>
            </Popover.Dropdown>
        </Popover>
    );
}
