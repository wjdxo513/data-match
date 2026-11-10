/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 기술 설계서 TD-18, 4.3절: 빌드 결과(dist)는 백엔드 정적 리소스로 복사된다.
// 개발 중에는 /api 요청을 로컬 백엔드(127.0.0.1:18080)로 프록시한다.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:18080',
    },
  },
  build: {
    outDir: 'dist',
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
