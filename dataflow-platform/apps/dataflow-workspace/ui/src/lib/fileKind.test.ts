import {describe, expect, it} from 'vitest';
import {fileType} from './fileKind';

describe('fileType', () => {
    it('reads a pipeline spec as a spec of its dataset', () => {
        expect(fileType('pipelines/earthquakes/pipeline.yml')).toEqual({dataset: 'earthquakes', language: 'yaml', kind: 'spec'});
    });

    it('reads other YAML, deeper specs and unknown names as plain files', () => {
        expect(fileType('pipelines/earthquakes/schemas/events.yml')).toEqual({dataset: undefined, language: 'yaml', kind: 'file'});
        expect(fileType('pipelines/a/b/pipeline.yml').kind).toBe('file');
        expect(fileType('notes.txt')).toEqual({dataset: undefined, language: 'plaintext', kind: 'file'});
    });

    it('reads Markdown in any case as Markdown', () => {
        expect(fileType('README.MD')).toEqual({dataset: undefined, language: 'markdown', kind: 'markdown'});
    });

    it('picks the editor language from the extension', () => {
        expect(fileType('transforms/RegionEnergy.java').language).toBe('java');
        expect(fileType('queries/top.sql').language).toBe('sql');
    });
});
