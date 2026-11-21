const TABLE_LINE = /^\s*(\+-|\|)/;

export const isTableLine = (message: string) => TABLE_LINE.test(message);
