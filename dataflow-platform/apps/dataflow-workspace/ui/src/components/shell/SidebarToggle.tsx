import {IconAction} from '../actions/IconAction';
import {PanelGlyph} from '../actions/PanelGlyph';

export function SidebarToggle({collapsed, onToggle}: { collapsed: boolean; onToggle: () => void }) {
    return (
        <IconAction label={collapsed ? 'Expand' : 'Collapse'} shortcut="Mod+B" size={32} onClick={onToggle}
                    glyph={<PanelGlyph side="left" collapsed={collapsed} size={22}/>}/>
    );
}
