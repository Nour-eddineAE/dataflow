import {formatPercent} from './format';

const ALARMING_DROP = -0.2;
const NO_CHANGE_BELOW = 0.001;

export function rowChange(now: number, before: number): { label: string; alarming: boolean } {
    if (now === before) return {label: 'same', alarming: false};
    if (before === 0) return {label: `+${now.toLocaleString('en-US')}`, alarming: false};
    const share = (now - before) / before;
    if (Math.abs(share) < NO_CHANGE_BELOW) return {label: '≈ same', alarming: false};
    const percent = formatPercent(Math.abs(share * 100));
    return {label: `${share > 0 ? '+' : '−'}${percent}`, alarming: share <= ALARMING_DROP};
}
