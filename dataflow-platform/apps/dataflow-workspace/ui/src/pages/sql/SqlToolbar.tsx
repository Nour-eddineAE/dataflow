import {Menu} from '@mantine/core';
import {
    IconArrowBackUp,
    IconArrowForwardUp,
    IconCheck,
    IconCopy,
    IconDeviceFloppy,
    IconDots,
    IconLink,
    IconListNumbers,
    IconPencil,
    IconPlayerPlayFilled,
    IconWand,
    IconX,
} from '../../icons';
import {IconAction} from '../../components/actions/IconAction';
import {ToolbarButton} from '../../components/actions/ToolbarButton';
import {DocumentState} from '../../components/DocumentState';
import {Toolbar, ToolbarDivider, ToolbarGroup, ToolbarSpacer} from '../../components/layout/Toolbar';
import {formatNumber} from '../../lib/format';
import {ICON, ICON_SMALL} from '../../lib/icons';
import {LIMITS, type QueryTab, statusOf} from '../../lib/queryTabs';

export type Undoable = { undo: boolean; redo: boolean };

export type SqlCommands = {
    run: () => void;
    limit: (limit: number) => void;
    edit: (command: 'undo' | 'redo') => void;
    format: () => void;
    save: () => void;
    copyLink: () => void;
    saveAs: () => void;
    rename: () => void;
    close: () => void;
};

export function SqlToolbar({tab, busy, saving, undoable, commands}: {
    tab: QueryTab;
    busy: boolean;
    saving: boolean;
    undoable: Undoable;
    commands: SqlCommands;
}) {
    const status = statusOf(tab);
    const empty = !tab.sql.trim();
    return (
        <Toolbar>
            <ToolbarGroup>
                <ToolbarButton label="Run" icon={IconPlayerPlayFilled} primary shortcut="Mod+Enter"
                               hint="Run the statement or selection" loading={busy} onClick={commands.run}/>
                <Menu position="bottom-start" width={170}>
                    <Menu.Target>
                        <ToolbarButton label={`${formatNumber(tab.limit)} rows`} icon={IconListNumbers} quiet menu hint="Row limit"/>
                    </Menu.Target>
                    <Menu.Dropdown>
                        <Menu.Label>Row limit</Menu.Label>
                        {LIMITS.map((limit) => (
                            <Menu.Item key={limit} onClick={() => commands.limit(limit)}
                                       rightSection={limit === tab.limit ? <IconCheck {...ICON_SMALL}/> : null}>
                                {formatNumber(limit)}
                            </Menu.Item>
                        ))}
                    </Menu.Dropdown>
                </Menu>
            </ToolbarGroup>
            <ToolbarDivider/>
            <ToolbarGroup>
                <IconAction label="Undo" icon={IconArrowBackUp} shortcut="Mod+Z" disabled={!undoable.undo}
                            onClick={() => commands.edit('undo')}/>
                <IconAction label="Redo" icon={IconArrowForwardUp} shortcut="Mod+Shift+Z" disabled={!undoable.redo}
                            onClick={() => commands.edit('redo')}/>
                <IconAction label="Format" icon={IconWand} shortcut="Shift+Alt+F" onClick={commands.format}/>
            </ToolbarGroup>
            <ToolbarSpacer/>
            <ToolbarGroup>
                {status !== 'draft' && <DocumentState status={status}/>}
                <ToolbarButton label="Save" icon={IconDeviceFloppy} shortcut="Mod+S" loading={saving}
                               disabled={status === 'saved' || empty} onClick={commands.save}/>
                <IconAction label="Copy link" icon={IconLink} disabled={!tab.saved} onClick={commands.copyLink}/>
                <Menu position="bottom-end" width={200}>
                    <Menu.Target>
                        <IconAction label="More" icon={IconDots}/>
                    </Menu.Target>
                    <Menu.Dropdown>
                        <Menu.Item leftSection={<IconCopy {...ICON}/>} disabled={empty} onClick={commands.saveAs}>
                            Save as new query
                        </Menu.Item>
                        <Menu.Item leftSection={<IconPencil {...ICON}/>} onClick={commands.rename}>Rename</Menu.Item>
                        <Menu.Divider/>
                        <Menu.Item leftSection={<IconX {...ICON}/>} onClick={commands.close}>Close query</Menu.Item>
                    </Menu.Dropdown>
                </Menu>
            </ToolbarGroup>
        </Toolbar>
    );
}
