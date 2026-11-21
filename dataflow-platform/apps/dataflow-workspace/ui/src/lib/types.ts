export function shortType(type: string) {
    const bare = type.replace(/^Nullable\((.*)\)$/, '$1').replace(/^LowCardinality\((.*)\)$/, '$1');
    if (/^DateTime64/.test(bare)) return 'timestamp';
    if (/^DateTime/.test(bare)) return 'timestamp';
    if (/^Date/.test(bare)) return 'date';
    if (/^Float/.test(bare)) return 'double';
    if (/^U?Int(8|16|32)$/.test(bare)) return 'int';
    if (/^U?Int/.test(bare)) return 'bigint';
    if (/^Decimal/.test(bare)) return bare.toLowerCase();
    if (/^(String|FixedString)/.test(bare)) return 'string';
    if (/^Bool/.test(bare)) return 'boolean';
    if (/^Array/.test(bare)) return 'array';
    if (/^Map/.test(bare)) return 'map';
    if (/^Tuple/.test(bare)) return 'struct';
    return bare.toLowerCase();
}

const NUMERIC = /^(tinyint|smallint|int|bigint|float|double|decimal)\b/;
const FLOATING = /^(float|double)$/;

export const isNumericType = (type: string) => NUMERIC.test(shortType(type));

export const isFloatingType = (type: string) => FLOATING.test(shortType(type));
