import {describe, expect, it} from 'vitest';
import {titleOf} from './title';

describe('titleOf', () => {
    it('names each page after what it shows', () => {
        expect(titleOf('/')).toBe('DataFlow');
        expect(titleOf('/workspace')).toBe('Workspace · DataFlow');
        expect(titleOf('/catalog/lake/earthquakes/silver')).toBe('earthquakes.silver · DataFlow');
        expect(titleOf('/catalog/serving/earthquakes')).toBe('serving.earthquakes · DataFlow');
        expect(titleOf('/runs/1f5022d6-45a9-4449-8131-e507a7c8b5f3')).toBe('Run 1f5022d6 · DataFlow');
        expect(titleOf('/admin')).toBe('Settings · DataFlow');
        expect(titleOf('/storage')).toBe('Storage · DataFlow');
    });
});
