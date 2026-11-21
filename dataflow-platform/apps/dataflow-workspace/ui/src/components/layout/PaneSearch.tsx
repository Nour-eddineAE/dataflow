import {CloseButton, TextInput} from '@mantine/core';
import {IconSearch} from '../../icons';
import {ICON_SMALL} from '../../lib/icons';

export function PaneSearch({value, onChange, placeholder = 'Search'}: {
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
}) {
    return (
        <div className="df-pane-search">
            <TextInput placeholder={placeholder} aria-label={placeholder} value={value}
                       leftSection={<IconSearch {...ICON_SMALL}/>}
                       rightSection={<CloseButton size="xs" aria-label="Clear search" onClick={() => onChange('')}
                                                  style={{visibility: value ? 'visible' : 'hidden'}}/>}
                       onChange={(event) => onChange(event.currentTarget.value)}
                       onKeyDown={(event) => {
                           if (event.key === 'Escape' && value) {
                               event.stopPropagation();
                               onChange('');
                           }
                       }}/>
        </div>
    );
}
