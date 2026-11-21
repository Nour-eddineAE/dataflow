import {defineConfig} from 'vitest/config';
import react from '@vitejs/plugin-react';

const workspace = process.env.WORKSPACE_API ?? 'http://localhost:8090';
const sameHost = {target: workspace, changeOrigin: false};

export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        proxy: {
            '/api': workspace,
            '/oauth2': sameHost,
            '/login': sameHost,
            '/logout': sameHost,
        },
    },
    build: {
        outDir: 'dist',
        chunkSizeWarningLimit: 2000,
    },
    test: {
        css: {include: [/styles\.css/]},
    },
});
