import {createTheme, type CSSVariablesResolver, type MantineColorsTuple} from '@mantine/core';
import {EASE, MOTION} from './lib/motion';
import {INK} from './lib/chartTheme';
import './components/actions/Key.css';
import './components/layout/Tabs.css';
import {TOOLTIP_DELAY} from './lib/timing';

const brand: MantineColorsTuple = [
    '#eef0fd', '#dde1fb', '#bcc4f6', '#99a5f0', '#7a88ea', '#6473e6', '#5766e3', '#4855c9', '#3f4bb4', '#323d9b',
];

const dark: MantineColorsTuple = [
    '#e4e6ea', '#c4c8ce', '#9ba1aa', '#757c86', '#383c43', '#2d3136', '#25282d', '#1c1e22', '#16181b', '#111214',
];

export const LIGHT_DIMMED = '#5f6975';

export const theme = createTheme({
    primaryColor: 'brand',
    black: INK.light.text,
    primaryShade: {light: 7, dark: 6},
    colors: {brand, dark},
    fontFamily: '"Inter Variable", Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    fontFamilyMonospace: '"JetBrains Mono Variable", "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace',
    spacing: {tight: '4px', snug: '6px', cozy: '8px', block: '40px', section: '48px', hollow: '56px', full: '64px'},
    fontSizes: {
        micro: '10px',
        caption: '11px',
        xs: '12px',
        sm: '13px',
        md: '14px',
        lg: '16px',
        xl: '20px',
        display: '28px',
    },
    lineHeights: {micro: '12px', caption: '16px', xs: '16px', sm: '20px', md: '20px', lg: '24px', xl: '28px', display: '36px'},
    headings: {
        fontFamily: '"Inter Variable", Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        fontWeight: '600',
        sizes: {h1: {fontSize: '24px'}, h2: {fontSize: '20px'}, h3: {fontSize: '16px'}, h4: {fontSize: '14px'}},
    },
    radius: {xs: '2px', sm: '4px', md: '6px', lg: '10px', xl: '999px'},
    defaultRadius: 'sm',
    shadows: {md: 'var(--df-shadow-raised)', xl: 'var(--df-shadow-overlay)'},
    cursorType: 'pointer',
    respectReducedMotion: true,
    activeClassName: 'df-press',
    components: {
        Button: {defaultProps: {size: 'sm', fw: 500}},
        Card: {defaultProps: {withBorder: true, radius: 'md', padding: 'md', shadow: 'none'}},
        Paper: {defaultProps: {radius: 'md'}},
        Loader: {defaultProps: {color: 'gray'}},
        Notification: {styles: {description: {whiteSpace: 'pre-line'}}},
        Badge: {defaultProps: {radius: 'sm', variant: 'default', tt: 'none', fw: 500}},
        Kbd: {classNames: {root: 'df-key'}},
        Tooltip: {defaultProps: {openDelay: TOOLTIP_DELAY, fz: 'xs'}},
        Tabs: {defaultProps: {keepMounted: false}, classNames: {list: 'df-tab-list', tab: 'df-tab'}},
        Table: {defaultProps: {verticalSpacing: 9, horizontalSpacing: 'md', fz: 'sm'}},
        Menu: {
            defaultProps: {
                radius: 'md',
                shadow: 'md',
                transitionProps: {transition: 'pop-top-left', duration: MOTION.fast, timingFunction: EASE}
            }
        },
        Modal: {defaultProps: {radius: 'lg', transitionProps: {transition: 'pop', duration: MOTION.base, timingFunction: EASE}}},
        Select: {defaultProps: {size: 'sm'}},
        TextInput: {defaultProps: {size: 'sm'}},
        Drawer: {
            defaultProps: {
                padding: 'lg',
                transitionProps: {duration: MOTION.base, timingFunction: EASE}
            }
        },
    },
});

const surfaces = {
    '--mantine-color-default': 'var(--df-main)',
    '--mantine-color-default-hover': 'var(--df-hover)',
    '--mantine-color-default-border': 'var(--df-border-strong)',
    '--mantine-color-placeholder': 'var(--mantine-color-dimmed)',
};

export const cssVariables: CSSVariablesResolver = () => ({
    variables: {},
    light: {...surfaces, '--mantine-color-dimmed': LIGHT_DIMMED},
    dark: surfaces,
});
