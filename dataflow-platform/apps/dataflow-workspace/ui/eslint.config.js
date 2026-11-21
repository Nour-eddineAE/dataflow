import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

const OBVIOUS_NUMBERS = [-1, 0, 1, 2, 100];
const DRAWINGS = ['src/components/Logo.tsx', 'src/components/actions/PanelGlyph.tsx'];
const FORMULAS = ['src/lib/tween.ts'];
const PALETTES = ['src/theme.ts', 'src/lib/layers.ts', 'src/lib/chartTheme.ts', 'src/lib/monaco.ts'];
const TYPED_SIZES = '^(IconAction|FavoriteButton|CopyValue|PanelGlyph)$';
const CLEAR_AT_THE_CALL = '^(w|h|maw|miw|mih|mah|width|height|minWidth|maxWidth|fw|lh|rows|minRows|maxRows|order|cols|limit'
    + '|scrollbarSize|maxDropdownHeight|defaultSize|min|max)$';
const CLEAR_IN_STYLE = '^(width|height|minWidth|maxWidth|minHeight|maxHeight|top|left|right|bottom|flex|zIndex|lineHeight)$';

const COMPONENT_LINES = 350;
const MODULE_LINES = 600;
const MAX_PARAMETERS = 5;
const MAX_NESTING = 4;
const HTTP_CLIENT = ['src/api/client.ts'];
const ICON_LIBRARY = ['src/icons.tsx'];
const BUTTON_WRAPPERS = ['src/components/actions/Action.tsx', 'src/components/actions/IconAction.tsx'];
const NOTIFIERS = ['src/components/notify.ts', 'src/components/RunNotifier.tsx'];
const PALETTE_NAMES = 'red|pink|grape|violet|indigo|blue|cyan|teal|green|lime|yellow|orange|gray|dark';

const lines = (max) => ['error', {max, skipBlankLines: true}];
const restricted = (...selectors) => ['error', ...selectors];
const nativeDialog = (name) => ({name, message: 'native-dialog: use FormModal or a notification.'});
const ICON_IMPORT = {name: '@phosphor-icons/react', message: 'icon-library: import from src/icons.tsx.'};
const BUTTON_IMPORT = {name: '@mantine/core', importNames: ['ActionIcon', 'Button'], message: 'button: use Action or IconAction.'};
const NOTIFY_IMPORT = {
    name: '@mantine/notifications',
    importNames: ['notifications'],
    message: 'notify: use notifyDone, notifyWarning or notifyFailure from components/notify.',
};
const jsxNumber = {
    selector: `JSXOpeningElement:not([name.name=/${TYPED_SIZES}/]) > JSXAttribute[name.name!=/${CLEAR_AT_THE_CALL}/]`
        + ' > JSXExpressionContainer > Literal[value>2]',
    message: 'jsx-number: use a theme token or a named constant.',
};
const styleNumber = {
    selector: `JSXAttribute[name.name='style'] Property[key.name!=/${CLEAR_IN_STYLE}/] > Literal[value>2]`,
    message: 'style-number: use a theme token or a named constant.',
};
const hexColor = {
    selector: 'Literal[value=/^#[0-9a-fA-F]{3,8}$/]',
    message: 'hex-color: use a colour from the theme or a palette.',
};
const rawColor = {
    selector: `:matches(JSXAttribute[name.name=/^(c|color|bg)$/], Property[key.name=/^(c|color|bg)$/]) > Literal[value=/^(${PALETTE_NAMES})(\\.[0-9])?$/]`,
    message: 'raw-color: use COLOR from lib/colors; neutral defaults live in the theme.',
};
const rawDuration = {
    selector: 'Literal[value=/\\b[0-9.]+m?s\\b/]',
    message: 'raw-duration: use MOTION or a --df-motion token.',
};
const rawRoute = {
    selector: "CallExpression[callee.name='navigate'] > Literal[value=/^\\//], JSXAttribute[name.name='to'] > Literal[value=/^\\//]",
    message: 'raw-route: use PAGE or links from lib/links.',
};
const defaultExport = {
    selector: 'ExportDefaultDeclaration',
    message: 'default-export: use a named export.',
};
const EVERYWHERE = [jsxNumber, styleNumber, rawDuration, rawRoute, defaultExport];

export default tseslint.config(
    {ignores: ['dist/**', 'node_modules/**', 'src/**/*.test.ts', 'src/**/*.test.tsx']},
    {
        files: ['src/**/*.{ts,tsx}'],
        languageOptions: {parser: tseslint.parser, parserOptions: {ecmaFeatures: {jsx: true}}},
        plugins: {'@typescript-eslint': tseslint.plugin, 'react-hooks': reactHooks},
        rules: {
            ...reactHooks.configs.flat.recommended.rules,
            'react-hooks/incompatible-library': 'off',
            'react-hooks/preserve-manual-memoization': 'off',
            '@typescript-eslint/no-magic-numbers': ['error', {
                ignore: OBVIOUS_NUMBERS,
                ignoreArrayIndexes: true,
                ignoreDefaultValues: true,
                ignoreEnums: true,
                ignoreNumericLiteralTypes: true,
                ignoreTypeIndexes: true,
            }],
            '@typescript-eslint/no-explicit-any': 'error',
            'max-depth': ['error', MAX_NESTING],
            'max-lines': lines(MODULE_LINES),
            'max-params': ['error', MAX_PARAMETERS],
            'no-restricted-globals': ['error',
                {name: 'fetch', message: 'http: call the API through src/api/client.ts.'},
                nativeDialog('alert'),
                nativeDialog('confirm'),
                nativeDialog('prompt'),
            ],
            'no-restricted-imports': ['error', {paths: [ICON_IMPORT, BUTTON_IMPORT, NOTIFY_IMPORT]}],
            'no-restricted-syntax': restricted(...EVERYWHERE, hexColor, rawColor),
        },
    },
    {
        files: ['src/**/*.tsx'],
        rules: {'max-lines': lines(COMPONENT_LINES)},
    },
    {
        files: HTTP_CLIENT,
        rules: {'no-restricted-globals': 'off'},
    },
    {
        files: [...ICON_LIBRARY, ...BUTTON_WRAPPERS],
        rules: {'no-restricted-imports': 'off'},
    },
    {
        files: NOTIFIERS,
        rules: {'no-restricted-imports': ['error', {paths: [ICON_IMPORT, BUTTON_IMPORT]}]},
    },
    {
        files: DRAWINGS,
        rules: {
            '@typescript-eslint/no-magic-numbers': 'off',
            'no-restricted-syntax': 'off',
        },
    },
    {
        files: FORMULAS,
        rules: {'@typescript-eslint/no-magic-numbers': 'off'},
    },
    {
        files: PALETTES,
        rules: {'no-restricted-syntax': restricted(...EVERYWHERE)},
    },
);
