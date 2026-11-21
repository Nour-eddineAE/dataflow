import {NAME_LENGTH} from './names';

const DAY = /\d{4}-\d{2}-\d{2}/g;
const MONTH = /\d{4}-\d{2}/g;

export function datasetFromSource(url: string): string {
    const file = url.trim().split(/[?#]/)[0].split('/').pop() ?? '';
    const stem = (file.match(DAY) ? file.replace(DAY, '') : file.replace(MONTH, '')).toLowerCase().split('.')[0];
    const name = stem.replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
    if (!name) return '';
    return (/^[a-z]/.test(name) ? name : `source_${name}`).slice(0, NAME_LENGTH);
}

export const isWebSource = (url: string) => /^https?:\/\/[^/\s]+\/\S*$/i.test(url.trim());

export const isLakePath = (path: string) => /^(?!system\/)(?!.*\.\.)[A-Za-z0-9._-]+(\/[A-Za-z0-9._-]+)*$/.test(path.trim());

export const isSource = (value: string) => isWebSource(value) || isLakePath(value);
