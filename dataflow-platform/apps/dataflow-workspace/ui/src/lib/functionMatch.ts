import type {SqlFunction} from '../api/types';

export function functionsStartingWith(functions: SqlFunction[], word: string): SqlFunction[] {
    const start = word.toLowerCase();
    return start ? functions.filter((fn) => fn.name.toLowerCase().startsWith(start)) : functions;
}
