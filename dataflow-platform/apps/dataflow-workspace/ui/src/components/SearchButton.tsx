import {Kbd, UnstyledButton} from '@mantine/core';
import {useOs} from '@mantine/hooks';
import {IconSearch} from '../icons';
import {ICON} from '../lib/icons';
import './SearchButton.css';

export function SearchButton({onClick, label = 'Search', shortcut}: {
    onClick: () => void;
    label?: string;
    shortcut?: string;
}) {
    const os = useOs({getValueInEffect: false});
    const mac = os === 'macos' || os === 'ios';
    return (
        <div className="df-search-control">
            <UnstyledButton type="button" className="df-search-button" onClick={onClick}
                            aria-label="Search workspace" aria-haspopup="dialog" aria-keyshortcuts={mac ? 'Meta+K' : 'Control+K'}>
                <IconSearch {...ICON} aria-hidden/>
                <span className="df-search-label">{label}</span>
                <Kbd className="df-search-shortcut" aria-hidden>{shortcut ?? (mac ? '⌘K' : 'Ctrl K')}</Kbd>
            </UnstyledButton>
        </div>
    );
}
