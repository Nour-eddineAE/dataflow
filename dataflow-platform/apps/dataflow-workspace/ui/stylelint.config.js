const PIXELS = '/[0-9]px/';
const HEX = '/#[0-9a-fA-F]{3,8}\\b/';

export default {
    rules: {
        'declaration-property-value-disallowed-list': {
            '/^font-size$/': [PIXELS],
            '/^border-radius$/': [PIXELS],
            '/^(?!--)/': [HEX],
        },
    },
};
