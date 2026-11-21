import {shortRunId} from './format';
import {pageName} from './navigation';

export function titleOf(pathname: string) {
    const parts = pathname.split('/').filter(Boolean).map(decodeURIComponent);
    if (!parts.length) return 'DataFlow';
    const [section, first, second, third] = parts;
    const name = (() => {
        if (section === 'catalog' && first === 'lake' && second && third) return `${second}.${third}`;
        if (section === 'catalog' && first === 'serving' && second) return `serving.${second}`;
        if (section === 'pipelines' && first === 'new') return 'New pipeline';
        if (section === 'pipelines' && first) return first;
        if (section === 'runs' && first) return `Run ${shortRunId(first)}`;
        return pageName(pathname);
    })();
    return `${name} · DataFlow`;
}
