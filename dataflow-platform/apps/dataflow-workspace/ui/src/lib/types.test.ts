import {describe, expect, it} from 'vitest';
import {isFloatingType, isNumericType, shortType} from './types';

describe('shortType', () => {
    it('shows ClickHouse types in the words the specs use', () => {
        expect(shortType('Nullable(String)')).toBe('string');
        expect(shortType('LowCardinality(String)')).toBe('string');
        expect(shortType('Nullable(DateTime64(6))')).toBe('timestamp');
        expect(shortType('Float64')).toBe('double');
        expect(shortType('UInt64')).toBe('bigint');
        expect(shortType('Int32')).toBe('int');
        expect(shortType('Date32')).toBe('date');
        expect(shortType('Array(String)')).toBe('array');
    });

    it('counts Spark and ClickHouse number types as numeric', () => {
        for (const type of ['tinyint', 'smallint', 'int', 'bigint', 'float', 'double', 'decimal(10,2)', 'Nullable(Float64)', 'UInt64', 'Decimal(18, 4)']) {
            expect(isNumericType(type)).toBe(true);
        }
        for (const type of ['string', 'timestamp', 'interval day', 'Nullable(String)', 'boolean']) {
            expect(isNumericType(type)).toBe(false);
        }
    });

    it('counts only binary floating types as floating', () => {
        expect(isFloatingType('double')).toBe(true);
        expect(isFloatingType('Float32')).toBe(true);
        expect(isFloatingType('decimal(10,2)')).toBe(false);
        expect(isFloatingType('bigint')).toBe(false);
    });
});
