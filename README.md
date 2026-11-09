# data-match

> 프로젝트 소개는 도메인 정의서·PRD 작성 후 채운다.

[agentic-template](https://github.com/wjdxo513/agentic-template) 기반 프로젝트다.
문서를 먼저 만들고(도메인 → PRD → 시나리오 → 와이어프레임 → 설계 원칙 → 아키텍처 → ERD → 실행 계획),
그 문서를 근거로 Claude Code 서브에이전트·스킬이 Task 단위로 개발한다.

## 진행 방법

1. [docs/0-ai-dev-guide.md](docs/0-ai-dev-guide.md)의 순서대로 `prompts/01~10` 프롬프트를 채워 실행한다.
2. `docs/8-plan.md`가 만들어지면 `/develop-backend BE-01`, `/develop-frontend FE-01`처럼 Task 단위로 개발한다.

## 구성

```
.claude/
  CLAUDE.md                # 최상위 지침
  agents/                  # 서브에이전트 8종
  skills/                  # develop-backend, develop-frontend
.mcp.json                  # context7, playwright, chrome-devtools, markitdown
docs/                      # 설계 문서 (0-ai-dev-guide.md부터)
prompts/                   # 문서 생성 프롬프트
```
