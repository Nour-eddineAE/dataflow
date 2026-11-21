type SearchItem = {label?: string; description?: string; keywords?: string | string[]};

const RANK = {exact: 0, prefix: 1, wordPrefix: 2, allInLabel: 3, someInLabel: 4, elsewhere: 5};

export function searchItems<T extends SearchItem>(items: T[], query: string, limit = 12): T[] {
    const text = normalize(query);
    const count = Math.max(0, Math.floor(limit));
    if (!text) return items.slice(0, count);
    const terms = text.split(' ');
    return items.flatMap((item, index) => {
        const label = normalize(item.label ?? '');
        const keywords = Array.isArray(item.keywords) ? item.keywords.join(' ') : item.keywords ?? '';
        const content = `${label} ${normalize(item.description ?? '')} ${normalize(keywords)}`;
        if (!terms.every((term) => content.includes(term))) return [];
        const words = label.split(/[^\p{L}\p{N}]+/u);
        const rank = label === text ? RANK.exact
            : label.startsWith(text) ? RANK.prefix
            : terms.every((term) => words.some((word) => word.startsWith(term))) ? RANK.wordPrefix
            : terms.every((term) => label.includes(term)) ? RANK.allInLabel
            : terms.some((term) => label.includes(term)) ? RANK.someInLabel : RANK.elsewhere;
        return [{item, index, rank}];
    }).sort((left, right) => left.rank - right.rank || left.index - right.index)
        .slice(0, count).map(({item}) => item);
}

function normalize(value: string): string {
    return value.normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/g, ' ').trim();
}
