import type {DraftCheck, Problem} from '../../api/types';
import {Empty, Loading} from '../../components/ErrorState';
import {IssueRow} from '../../components/IssueRow';

export function CheckTab({result, warnings, onLine}: { result?: DraftCheck; warnings: Problem[]; onLine: (line: number) => void }) {
    if (!result) return <Loading/>;
    if (!result.problems.length && !result.warnings.length) return <Empty title="No problems"/>;
    return (
        <div className="df-issues">
            {result.problems.map(({message, line}, index) => (
                <IssueRow key={index} severity="error" message={message} detail={line && `Line ${line}`}
                          onPick={line ? () => onLine(line) : undefined}/>
            ))}
            {warnings.map(({message, line}) => (
                <IssueRow key={message} severity="warning" message={message} detail={line && `Line ${line}`}
                          onPick={line ? () => onLine(line) : undefined}/>
            ))}
        </div>
    );
}
