export type Scheme = 'light' | 'dark';

export const SERIES: Record<Scheme, string[]> = {
    light: ['#4855c9', '#eb6834', '#1a9a6b', '#b07d00', '#d0628d', '#008300', '#2a78d6', '#e34948'],
    dark: ['#5766e3', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#3987e5', '#e66767'],
};

export const INK: Record<Scheme, {
    text: string;
    muted: string;
    grid: string;
    surface: string;
    success: string;
    danger: string
}> = {
    light: {
        text: '#1f2328',
        muted: '#5f6975',
        grid: '#eceff3',
        surface: '#ffffff',
        success: '#25743f',
        danger: '#b42b37'
    },
    dark: {
        text: '#e6e8eb',
        muted: '#9aa1ab',
        grid: '#25282d',
        surface: '#16181b',
        success: '#4cae6e',
        danger: '#ec7078'
    },
};

export function seriesColors(names: string[], scheme: Scheme): Record<string, string> {
    const palette = SERIES[scheme];
    return Object.fromEntries([...names].sort().slice(0, palette.length).map((name, index) => [name, palette[index]]));
}
