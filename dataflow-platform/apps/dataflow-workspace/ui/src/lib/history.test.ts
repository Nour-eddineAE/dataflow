import {describe, expect, it} from 'vitest';
import {begin, record, redo, replace, undo} from './history';

describe('history', () => {
    it('undoes and redoes each change, and a new change drops what was undone', () => {
        let history = record(record(begin('a'), 'b'), 'c');

        history = undo(history);
        expect(history.present).toBe('b');
        history = redo(history);
        expect(history.present).toBe('c');

        history = record(undo(history), 'd');
        expect(history).toMatchObject({past: ['a', 'b'], present: 'd', future: []});
    });

    it('merges a burst of changes with the same key into one step', () => {
        let history = begin('');
        for (const [index, text] of ['h', 'he', 'hel', 'hell', 'hello'].entries()) {
            history = record(history, text, {merge: 'name', at: index * 100});
        }
        history = record(history, 'hello!', {merge: 'name', at: 5000});

        expect(history.past).toEqual(['', 'hello']);
        expect(undo(undo(history)).present).toBe('');
    });

    it('never merges different keys, or a change into the starting state', () => {
        const typed = record(begin('x'), 'xy', {merge: 'name', at: 0});
        expect(typed.past).toEqual(['x']);
        expect(record(typed, 'xyz', {merge: 'owner', at: 10}).past).toEqual(['x', 'xy']);
    });

    it('replaces the present without making a step, and ignores no-op changes', () => {
        const history = record(begin({n: 1}), {n: 2});
        expect(replace(history, {n: 3})).toMatchObject({past: [{n: 1}], present: {n: 3}});
        expect(record(history, history.present)).toBe(history);
        expect(undo(begin('only'))).toEqual(begin('only'));
        expect(redo(begin('only'))).toEqual(begin('only'));
    });
});
