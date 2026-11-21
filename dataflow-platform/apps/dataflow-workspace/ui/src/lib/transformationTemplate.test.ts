import {describe, expect, it} from 'vitest';
import {className, TRANSFORMATION_NAME, transformationTemplate} from './transformationTemplate';

describe('transformationTemplate', () => {
    it('names the class after the transformation and pins version 1', () => {
        expect(className('zone_enrichment')).toBe('ZoneEnrichment');
        const source = transformationTemplate('zone_enrichment');
        expect(source).toContain('@DataTransformation(name = "zone_enrichment")');
        expect(source).toContain('public final class ZoneEnrichment implements Transformation<ZoneEnrichment.In, NoParams>');
        expect(source).toContain('public record In(Dataset<Row> rows) {}');
    });

    it('accepts snake_case names only', () => {
        expect(TRANSFORMATION_NAME.test('zone_enrichment')).toBe(true);
        expect(TRANSFORMATION_NAME.test('ZoneEnrichment')).toBe(false);
    });
});
