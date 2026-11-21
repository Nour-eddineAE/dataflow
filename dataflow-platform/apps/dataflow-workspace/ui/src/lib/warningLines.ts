import type {Problem} from '../api/types';
import {nodeOfPath, nodeOfProblem, PIPELINE, type Spec} from './specModel';
import {writeSpec} from './specYaml';

export function warningLines(text: string, spec: Spec, warnings: string[]): Problem[] {
    const written = writeSpec(spec as Record<string, unknown>);
    const canonical = written.text === text;
    return warnings.map((message) => {
        const node = nodeOfProblem(spec, message);
        const index = canonical && node !== PIPELINE ? written.paths.findIndex((path) => !!path && nodeOfPath(path, spec) === node) : -1;
        return {message, line: index < 0 ? undefined : index + 1};
    });
}
