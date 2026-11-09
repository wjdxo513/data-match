# AI 에이전틱 개발 템플릿

문서를 먼저 만들고(도메인 → PRD → 시나리오 → 와이어프레임 → 설계 원칙 → 아키텍처 → ERD → 실행 계획),
그 문서를 근거로 Claude Code 서브에이전트·스킬이 Task 단위로 개발하는 프로젝트 기본틀이다.

## 사용법

1. 이 폴더를 복사해 새 프로젝트 폴더를 만든다.
2. `.claude/CLAUDE.md`의 `{{ }}` 부분을 채운다.
3. [docs/0-ai-dev-guide.md](docs/0-ai-dev-guide.md)의 순서대로 `prompts/01~10` 프롬프트를 채워 실행한다.
4. `docs/8-plan.md`가 만들어지면 `/develop-backend BE-01`, `/develop-frontend FE-01`처럼 Task 단위로 개발한다.

## 구성

```
.claude/
  CLAUDE.md                # 최상위 지침 (템플릿)
  agents/                  # 서브에이전트 8종
  skills/                  # develop-backend, develop-frontend
.mcp.json                  # context7, playwright, chrome-devtools, markitdown
docs/0-ai-dev-guide.md     # 문서 작성 순서·규칙·개발 진행 방식
prompts/                   # 문서 생성 프롬프트 템플릿
```
