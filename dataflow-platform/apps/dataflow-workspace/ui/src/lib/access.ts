export type PrincipalKind = 'group' | 'user';

export const privilegeLabel = (code: string) => code.charAt(0) + code.slice(1).toLowerCase();

export function knownPrincipals(kind: PrincipalKind, granted: string[], ownGroups: string[]): string[] {
    const prefix = `${kind}:`;
    const names = granted.filter((principal) => principal.startsWith(prefix)).map((principal) => principal.slice(prefix.length));
    const all = kind === 'group' ? [...names, ...ownGroups] : names;
    return [...new Set(all)].sort((left, right) => left.localeCompare(right));
}
