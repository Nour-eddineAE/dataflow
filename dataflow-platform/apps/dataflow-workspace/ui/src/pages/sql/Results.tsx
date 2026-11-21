import {Text, Tooltip} from '@mantine/core';
import {IconDownload, IconTable} from '../../icons';
import {lazy, Suspense} from 'react';
import type {QueryResult} from '../../api/types';
import type {ChartChoice} from '../../lib/chartChoice';
import {IconAction} from '../../components/actions/IconAction';
import {shortcutLabel, SHORTCUTS} from '../../components/actions/shortcut';
import {DataGrid} from '../../components/DataGrid';
import {ErrorState, Loading} from '../../components/ErrorState';
import {BOTTOM_PANE_MAX, Pane} from '../../components/layout/Pane';
import {TabStrip} from '../../components/layout/Tabs';
import {downloadCsv} from '../../lib/csv';
import {formatBytes, formatCompact, formatNumber} from '../../lib/format';

const DEFAULT_HEIGHT = 320;
const MIN_HEIGHT = 120;

export type ResultView = 'results' | 'chart';
export type Outcome = { result?: QueryResult; error?: Error };

const ResultChart = lazy(() => import('./ResultChart').then((module) => ({default: module.ResultChart})));

type Props = {
    outcome?: Outcome;
    running: boolean;
    name: string;
    view: ResultView;
    onView: (view: ResultView) => void;
    chart?: ChartChoice;
    onChart: (chart: ChartChoice) => void;
    collapsed: boolean;
    onCollapsedChange: (collapsed: boolean) => void;
};

export function Results({outcome, running, name, view, onView, chart, onChart, collapsed, onCollapsedChange}: Props) {
    const result = outcome?.result;
    const shown = result ? view : 'results';
    return (
        <Pane id="sql.results" side="bottom" title="Results" icon={IconTable} defaultSize={DEFAULT_HEIGHT} min={MIN_HEIGHT} max={BOTTOM_PANE_MAX}
              collapsed={collapsed} onCollapsedChange={onCollapsedChange}
              header={<TabStrip value={shown} items={[
                  {value: 'results', label: 'Results'},
                  {value: 'chart', label: 'Chart', disabled: !result},
              ]} onChange={(next) => {
                  onView(next);
                  onCollapsedChange(false);
              }}/>}
              actions={<>
                  <span role="status">{result && <Summary result={result}/>}</span>
                  <IconAction label="Download CSV" icon={IconDownload} disabled={!result}
                              onClick={() => result && downloadCsv(name, result)}/>
              </>}>
            <div className="df-sql-output" data-running={running || undefined}>
                <div className="df-sql-progress" aria-hidden/>
                {outcome?.error ? <div className="df-sql-error"><ErrorState error={outcome.error}/></div>
                    : !result ? <Waiting running={running}/>
                        : shown === 'chart' ? <Suspense fallback={<Loading/>}><ResultChart result={result} choice={chart} onChoice={onChart}/></Suspense>
                            : <DataGrid result={result} height="100%"/>}
            </div>
        </Pane>
    );
}

function Summary({result}: { result: QueryResult }) {
    return (
        <Tooltip label={`Read ${formatCompact(result.rowsRead)} rows, ${formatBytes(result.bytesRead)}`}>
            <Text size="xs" c="dimmed" className="df-num df-sql-summary">
                {formatNumber(result.rows.length)} rows{result.truncated ? ' (limit)' : ''} · {formatNumber(result.elapsedMs)} ms
            </Text>
        </Tooltip>
    );
}

function Waiting({running}: { running: boolean }) {
    return (
        <div className="df-sql-empty">
            <Text size="sm" c="dimmed">{running ? 'Running' : 'Run a query to see results'}</Text>
            <Text size="xs" c="dimmed" style={{visibility: running ? 'hidden' : 'visible'}}>{shortcutLabel(SHORTCUTS.run)}</Text>
        </div>
    );
}
