import {useNavigate} from 'react-router-dom';
import type {RuleRun} from '../api/types';
import {formatNumber, formatTime} from '../lib/format';
import {links} from '../lib/links';
import {COLOR} from '../lib/colors';

const WIDTH = 96;
const HEIGHT = 20;
const SLOTS = 12;
const GAP = 2;

export function FailureTrend({runs, color}: { runs: RuleRun[]; color: string }) {
    const navigate = useNavigate();
    const recent = runs.slice(-SLOTS);
    if (!recent.length) return null;
    const shares = recent.map((run) => (run.evaluated ? run.failing / run.evaluated : 0));
    const peak = Math.max(...shares);
    const bar = (WIDTH - GAP * (SLOTS - 1)) / SLOTS;
    return (
        <svg width={WIDTH} height={HEIGHT} role="img" aria-label="Failing rows per run" style={{display: 'block'}}>
            {recent.map((run, index) => {
                const share = shares[index];
                const height = share > 0 && peak > 0 ? Math.max(2, (share / peak) * HEIGHT) : 2;
                return (
                    <rect key={run.runId} x={(SLOTS - recent.length + index) * (bar + GAP)} y={HEIGHT - height}
                          width={bar} height={height} rx={1}
                          fill={share > 0 ? color : COLOR.border} style={{cursor: 'pointer'}}
                          onClick={() => navigate(links.run(run.runId))}>
                        <title>{`${formatTime(run.at)} · ${formatNumber(run.failing)} of ${formatNumber(run.evaluated)}`}</title>
                    </rect>
                );
            })}
        </svg>
    );
}
