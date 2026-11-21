import {describe, expect, it} from 'vitest';
import styles from '../styles.css?raw';
import {LIGHT_DIMMED, theme} from '../theme';
import {INK, SERIES} from './chartTheme';
import {layer} from './layers';

const READABLE_TEXT = 4.5;
const VISIBLE_PART = 3;
const WHITE = '#ffffff';
const SURFACES = ['--df-main', '--df-shell', '--df-subtle', '--df-hover'];
const STATUS_TOKENS = ['--df-link', '--df-success', '--df-danger', '--df-warning'];

function tokens(selector: string): Record<string, string> {
    const block = styles.slice(styles.indexOf(`${selector} {`)).split('}')[0];
    return Object.fromEntries([...block.matchAll(/(--df-[\w-]+):\s*(#[0-9a-f]{6})/g)].map(([, name, value]) => [name, value]));
}

const LAYERS = ['landing', 'bronze', 'silver', 'quarantine', 'history', 'current', 'reference', 'input', 'gold', 'serving', 'system'];
const APART_FROM_STATUS = 0.09;
const TO_CONE = [
    [0.4122214708, 0.5363325363, 0.0514459929],
    [0.2119034982, 0.6806995451, 0.1073969566],
    [0.0883024619, 0.2817188376, 0.6299787005],
];
const TO_OKLAB = [
    [0.2104542553, 0.793617785, -0.0040720468],
    [1.9779984951, -2.428592205, 0.4505937099],
    [0.0259040371, 0.7827717662, -0.808675766],
];

function linear(hex: string) {
    return [1, 3, 5].map((start) => {
        const channel = parseInt(hex.slice(start, start + 2), 16) / 255;
        return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
    });
}

function luminance(hex: string) {
    const [red, green, blue] = linear(hex);
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function times(matrix: number[][], vector: number[]) {
    return matrix.map((row) => row.reduce((sum, weight, index) => sum + weight * vector[index], 0));
}

function difference(one: string, other: string) {
    const [first, second] = [one, other].map((hex) => times(TO_OKLAB, times(TO_CONE, linear(hex)).map(Math.cbrt)));
    return Math.hypot(...first.map((value, index) => value - second[index]));
}

function contrast(one: string, other: string) {
    const [lighter, darker] = [luminance(one), luminance(other)].sort((a, b) => b - a);
    return (lighter + 0.05) / (darker + 0.05);
}

const brand = theme.colors!.brand!;
const greys = theme.colors!.dark!;
const shades = theme.primaryShade as { light: number; dark: number };

const SCHEMES = [
    {name: 'light', values: tokens(':root'), dimmed: LIGHT_DIMMED, primary: brand[shades.light]},
    {name: 'dark', values: tokens("[data-mantine-color-scheme='dark']"), dimmed: greys[2], primary: brand[shades.dark]},
];

describe.each(SCHEMES)('text in the $name theme', ({values, dimmed, primary}) => {
    it.each(SURFACES)('status and link colours are readable on %s', (surface) => {
        for (const token of STATUS_TOKENS) {
            expect(contrast(values[token], values[surface]), token).toBeGreaterThanOrEqual(READABLE_TEXT);
        }
    });

    it.each(SURFACES)('secondary text is readable on %s', (surface) => {
        expect(contrast(dimmed, values[surface])).toBeGreaterThanOrEqual(READABLE_TEXT);
    });

    it('selected text is readable on its background', () => {
        expect(contrast(values['--df-active-text'], values['--df-active'])).toBeGreaterThanOrEqual(READABLE_TEXT);
    });

    it('a primary button has readable text', () => {
        expect(contrast(WHITE, primary)).toBeGreaterThanOrEqual(READABLE_TEXT);
    });

    it.each(['--df-main', '--df-active'])('the focus ring stands out on %s', (surface) => {
        expect(contrast(values['--df-focus'], values[surface])).toBeGreaterThanOrEqual(VISIBLE_PART);
    });

    it('an input border stands out on the page', () => {
        expect(contrast(values['--df-border-control'], values['--df-main'])).toBeGreaterThanOrEqual(VISIBLE_PART);
    });

    it('danger text is readable on a selected row', () => {
        expect(contrast(values['--df-danger'], values['--df-active'])).toBeGreaterThanOrEqual(READABLE_TEXT);
    });
});

describe.each(['light', 'dark'] as const)('charts in the %s theme', (scheme) => {
    it.each(SERIES[scheme])('series %s stands out on the chart', (color) => {
        expect(contrast(color, INK[scheme].surface)).toBeGreaterThanOrEqual(VISIBLE_PART);
    });

    it('status colours match the page tokens', () => {
        const values = SCHEMES.find((entry) => entry.name === scheme)!.values;
        expect([INK[scheme].success, INK[scheme].danger]).toEqual([values['--df-success'], values['--df-danger']]);
    });
});

describe.each(SCHEMES)('layer tones in the $name theme', ({values}) => {
    it.each(LAYERS)('%s is not mistaken for a status', (name) => {
        for (const token of STATUS_TOKENS) {
            expect(difference(layer(name).tone, values[token]), token).toBeGreaterThanOrEqual(APART_FROM_STATUS);
        }
    });

    it.each(LAYERS)('%s icons stand out on the page', (name) => {
        expect(contrast(layer(name).tone, values['--df-main'])).toBeGreaterThanOrEqual(VISIBLE_PART);
    });
});
