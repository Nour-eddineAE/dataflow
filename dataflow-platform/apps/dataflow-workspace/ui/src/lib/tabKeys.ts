const MOVES: Record<string, (index: number, count: number) => number> = {
    ArrowRight: (index, count) => (index + 1) % count,
    ArrowLeft: (index, count) => (index - 1 + count) % count,
    Home: () => 0,
    End: (_, count) => count - 1,
};

export function tabAfterKey<T>(tabs: { value: T; disabled?: boolean }[], current: T, key: string): T | undefined {
    const move = MOVES[key];
    const enabled = tabs.filter((tab) => !tab.disabled);
    if (!move || !enabled.length) return undefined;
    const index = Math.max(0, enabled.findIndex((tab) => tab.value === current));
    return enabled[move(index, enabled.length)].value;
}
