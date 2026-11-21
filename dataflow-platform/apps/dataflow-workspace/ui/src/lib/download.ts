export function downloadText(name: string, extension: string, text: string, type = 'text/plain') {
    const url = URL.createObjectURL(new Blob([text], {type: `${type};charset=utf-8`}));
    const link = document.createElement('a');
    link.href = url;
    link.download = `${name.replace(/[^A-Za-z0-9._-]+/g, '_')}.${extension}`;
    link.click();
    URL.revokeObjectURL(url);
}
