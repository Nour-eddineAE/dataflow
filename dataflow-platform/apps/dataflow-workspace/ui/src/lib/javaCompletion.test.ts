import {describe, expect, it} from 'vitest';
import {
    chain,
    importLine,
    javaHover,
    javaIndex,
    type JavaMember,
    javaSignatures,
    javaSuggestions,
    type JavaType
} from './javaCompletion';

const method = (name: string, returns: string, parameters: string[], isStatic = false): JavaMember =>
    ({name, kind: 'METHOD', isStatic, returns, parameters, deprecated: false});

const TYPES: JavaType[] = [
    {
        name: 'functions', qualifiedName: 'org.apache.spark.sql.functions', kind: 'CLASS', members: [
            method('col', 'Column', ['String'], true),
            method('pow', 'Column', ['Column', 'double'], true),
            method('pow', 'Column', ['String', 'String'], true),
            method('when', 'Column', ['Column', 'Object'], true),
        ]
    },
    {
        name: 'Column', qualifiedName: 'org.apache.spark.sql.Column', kind: 'CLASS', members: [
            method('multiply', 'Column', ['Object']),
            method('otherwise', 'Column', ['Object']),
            method('alias', 'Column', ['String']),
        ]
    },
    {
        name: 'Dataset', qualifiedName: 'org.apache.spark.sql.Dataset', kind: 'CLASS', members: [
            method('withColumn', 'Dataset<Row>', ['String', 'Column']),
            method('select', 'Dataset<Row>', ['Column...']),
            method('groupBy', 'RelationalGroupedDataset', ['String', 'String...']),
        ]
    },
    {
        name: 'RelationalGroupedDataset',
        qualifiedName: 'org.apache.spark.sql.RelationalGroupedDataset',
        kind: 'CLASS',
        members: [
            method('agg', 'Dataset<Row>', ['Column', 'Column...']),
        ]
    },
    {
        name: 'Row',
        qualifiedName: 'org.apache.spark.sql.Row',
        kind: 'INTERFACE',
        members: [method('getAs', 'T', ['String'])]
    },
    {
        name: 'DataTypes', qualifiedName: 'org.apache.spark.sql.types.DataTypes', kind: 'CLASS', members: [
            {name: 'StringType', kind: 'FIELD', isStatic: true, returns: 'DataType', parameters: [], deprecated: false},
        ]
    },
    {
        name: 'DataTransformation',
        qualifiedName: 'com.dataflow.transformation.DataTransformation',
        kind: 'ANNOTATION',
        members: []
    },
];

const API = javaIndex(TYPES);

const SOURCE = `package demo;

import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.col;

public final class Energy implements Transformation<Energy.In, Energy.Params> {

    public record In(Dataset<Row> rows) {}

    public record Params(double base) {}

    @Override
    public Dataset<Row> apply(In in, Params params) {
        Dataset<Row> rows = in.rows();
        var energy = col("mag").multiply(1.5);
        return rows
            .withColumn("energy", energy)
            .CURSOR
    }
}
`;

function at(marker: string, text = SOURCE) {
    const offset = text.indexOf(marker);
    return {text: text.replace(marker, ''), offset};
}

const labels = (text: string, offset: number) => javaSuggestions(text, offset, API, ['mag', 'depth']).map((suggestion) => suggestion.label);

describe('chain', () => {
    it('reads a call chain across lines, arguments and strings included', () => {
        expect(chain('rows\n  .withColumn("a)", col("x"))\n  .groupBy("k")')).toEqual([
            {name: 'rows', call: false}, {name: 'withColumn', call: true}, {name: 'groupBy', call: true},
        ]);
    });
});

describe('javaSuggestions', () => {
    it('follows the declared type through a chain', () => {
        const {text, offset} = at('CURSOR');
        expect(labels(text, offset).sort()).toEqual(['groupBy', 'select', 'withColumn']);
    });

    it('knows the return type of a method on the result of another', () => {
        const {text, offset} = at('CURSOR', SOURCE.replace('.CURSOR', '.groupBy("depthClass").CURSOR'));
        expect(labels(text, offset)).toEqual(['agg']);
    });

    it('reads record components and var declarations', () => {
        const records = at('CURSOR', SOURCE.replace('.CURSOR', ';\n        in.rows().CURSOR'));
        expect(labels(records.text, records.offset)).toContain('withColumn');
        const inferred = at('CURSOR', SOURCE.replace('.CURSOR', ';\n        energy.CURSOR'));
        expect(labels(inferred.text, inferred.offset).sort()).toEqual(['alias', 'multiply', 'otherwise']);
        const params = at('CURSOR', SOURCE.replace('.CURSOR', ';\n        params.CURSOR'));
        expect(labels(params.text, params.offset)).toEqual(['base']);
    });

    it('offers static members on a class name', () => {
        const {text, offset} = at('CURSOR', SOURCE.replace('.CURSOR', ';\n        DataTypes.CURSOR'));
        expect(javaSuggestions(text, offset, API, [])).toMatchObject([{label: 'StringType', kind: 'constant'}]);
    });

    it('adds the static import a function needs, once', () => {
        const {text, offset} = at('CURSOR', SOURCE.replace('.CURSOR', ';\n        poCURSOR'));
        const suggestions = javaSuggestions(text, offset, API, []);
        expect(suggestions.find((suggestion) => suggestion.label === 'pow')).toMatchObject({
            insertText: 'pow($0)',
            snippet: true,
            importLine: {line: 8, text: 'import static org.apache.spark.sql.functions.pow;\n'},
        });
        expect(suggestions.find((suggestion) => suggestion.label === 'col')?.importLine).toBeUndefined();
    });

    it('suggests column names only inside column arguments', () => {
        const inside = at('CURSOR', SOURCE.replace('.CURSOR', ';\n        col("CURSOR'));
        expect(labels(inside.text, inside.offset)).toEqual(['mag', 'depth']);
        const elsewhere = at('CURSOR', SOURCE.replace('.CURSOR', ';\n        String s = "CURSOR'));
        expect(labels(elsewhere.text, elsewhere.offset)).toEqual([]);
    });

    it('offers annotations after @ and nothing in comments', () => {
        const annotation = at('CURSOR', SOURCE.replace('.CURSOR', ';\n        @CURSOR'));
        expect(labels(annotation.text, annotation.offset)).toContain('DataTransformation');
        const comment = at('CURSOR', SOURCE.replace('.CURSOR', ';\n        // rows.CURSOR'));
        expect(labels(comment.text, comment.offset)).toEqual([]);
    });
});

describe('javaSignatures', () => {
    it('shows the overloads and the argument being typed', () => {
        const {text, offset} = at('CURSOR', SOURCE.replace('.CURSOR', ';\n        pow(col("mag"), CURSOR'));
        expect(javaSignatures(text, offset, API)).toEqual({
            signatures: [
                {label: 'Column pow(Column, double)', parameters: ['Column', 'double'], documentation: ''},
                {label: 'Column pow(String, String)', parameters: ['String', 'String'], documentation: ''},
            ],
            active: 0,
            parameter: 1,
        });
    });
});

describe('javaHover', () => {
    it('describes a method from its receiver', () => {
        const text = SOURCE.replace('.CURSOR', '');
        const offset = text.indexOf('withColumn') + 3;
        expect(javaHover(text, offset, API)?.contents).toBe('```java\nDataset<Row> withColumn(String, Column)\n```');
    });
});

describe('importLine', () => {
    it('adds a first static import after the others with a blank line', () => {
        expect(importLine('package a;\n\nimport x.Y;\n\nclass A {}', 'import static x.f.g;')).toEqual({
            line: 4,
            text: '\nimport static x.f.g;\n'
        });
    });

    it('respects wildcards', () => {
        expect(importLine('import static x.f.*;', 'import static x.f.g;')).toBeUndefined();
    });
});
