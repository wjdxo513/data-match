import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.(ts|tsx)$/.test(name) && !/\.test\.(ts|tsx)$/.test(name) ? [path] : [];
  });
}

// SETUP-02 완료 조건: 런타임 의존성은 react, react-dom뿐이고 전역 상태·UI 라이브러리를 쓰지 않는다(TD-18).
describe('프로젝트 설정', () => {
  const pkg = JSON.parse(readFileSync(resolve(__dirname, '../package.json'), 'utf-8'));

  it('dependencies는 react와 react-dom뿐이다', () => {
    expect(Object.keys(pkg.dependencies).sort()).toEqual(['react', 'react-dom']);
  });

  it('dev 서버가 /api를 127.0.0.1:18080으로 프록시한다', () => {
    const config = readFileSync(resolve(__dirname, '../vite.config.ts'), 'utf-8');
    expect(config).toContain("'/api': 'http://127.0.0.1:18080'");
  });

  it('스타일에 판정·운영 경고 색상 변수가 있다', () => {
    const css = readFileSync(resolve(__dirname, 'styles.css'), 'utf-8');
    for (const name of ['--match', '--mismatch', '--missing', '--prod-warning']) {
      expect(css).toContain(name);
    }
  });

  // FE-01: 입력값을 브라우저 저장소에 저장하지 않고 전역 상태 라이브러리를 쓰지 않는다(SA-09, TD-18).
  it('소스에 브라우저 저장소와 전역 상태 라이브러리 사용이 없다', () => {
    const files = sourceFiles(__dirname);
    expect(files.length).toBeGreaterThan(0);
    for (const file of files) {
      const src = readFileSync(file, 'utf-8');
      expect(src, file).not.toMatch(/localStorage|sessionStorage|indexedDB|document\.cookie/);
      expect(src, file).not.toMatch(
        /from ['"](redux|@reduxjs\/toolkit|zustand|recoil|jotai|mobx|valtio)/,
      );
    }
  });
});
