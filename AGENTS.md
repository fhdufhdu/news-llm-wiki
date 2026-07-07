@/Users/estsoft/.codex/RTK.md

# News Wiki 운영 규칙

이 작업공간은 사용자가 제공한 기사 URL 수집, SQLite 저장, Codex CLI 기반 위키 데이터
생성, Spring Boot 서버 렌더링 UI를 제공하는 개인용 News Wiki 애플리케이션이다.

## 핵심 모델

1. 사용자는 웹 화면에서 기사 URL을 직접 제공한다.
2. 원본 기사 HTML 수집은 기계적으로 수행한다. URL 정규화, HTML fetch,
   중복 제거, raw 저장에는 LLM 판단을 사용하지 않는다.
3. LLM은 저장 완료된 raw source를 읽어 source-level note와 durable
   topic/entity/event/claim을 만드는 위키 데이터 생성에만 사용한다.
4. 위키는 일일 digest, keyword dump, crawler output이 아니라 연결된 지식
   베이스다.
5. Schema는 단순하게 시작하고, 문서를 저장하면서 더 나은 구조가 드러나면
   Flyway migration과 prompt 계약으로 진화시킨다.

## 현재 디렉터리 구조

- `src/main/java/com/newswiki/application/`: Spring MVC controller와 화면 진입점.
- `src/main/java/com/newswiki/service/`: URL import, raw 저장, 위키 생성, scheduler
  같은 application workflow.
- `src/main/java/com/newswiki/repository/`: SQLite 접근. Spring JDBC `JdbcClient`를
  직접 사용한다.
- `src/main/java/com/newswiki/infrastructure/`: HTTP fetcher,
  Codex runner, importer 같은 외부 입출력 구현.
- `src/main/java/com/newswiki/dto/`: 계층 사이를 오가는 단순 data carrier.
- `src/main/resources/db/migration/`: Flyway SQLite schema.
- `src/main/resources/templates/`: Thymeleaf 화면.
- `src/main/resources/static/css/`: 디자인 시스템 CSS.
- `docs/`: 설계와 구현 계획 문서.

## 런타임 데이터

- Docker Compose에서는 `./data`가 `/app/data`로 마운트된다.
- SQLite 파일은 `/app/data/newswiki.sqlite`에 둔다.
- raw HTML gzip 파일과 Codex AI 작업 부산물은 `/app/data` 아래에 생성한다.
- `raw/`, `state/`, `wikidata/`, `tools/`는 이전 Python/Markdown 위키 작업용
  디렉터리였으며 현재 소스 트리에는 두지 않는다.
- 과거 `state/source-index.jsonl` 같은 legacy corpus를 가져와야 할 때는
  외부 파일 경로를 `ExistingCorpusImporter`에 전달하는 방식으로 import한다.

## 구현 규칙

- 패키지 순서는 `application / service / repository / infrastructure / dto`를
  기준으로 한다.
- 전형적인 Spring layered architecture를 사용한다.
- service와 infrastructure의 강결합은 허용한다. 필요 없는 port/adapter
  abstraction을 미리 만들지 않는다.
- YAGNI, KISS, DRY를 따른다. 지금 필요한 URL import, raw 저장, 위키 데이터 생성,
  조회 화면에 필요한 코드만 만든다.
- `wiki_sections.fixed=1`은 대분류로 사용한다. 상단 navigation은 대분류만 표시한다.
- `wiki_sections.fixed=0`은 소분류로 사용한다. Codex는 위키 작성 중 소분류를 자연스럽게 생성, 수정, 삭제할 수 있다.

## 검증

기본 검증:

```bash
rtk ./gradlew test
rtk ./gradlew bootJar
```

Docker 실행:

```bash
rtk docker compose up -d news-wiki
rtk docker compose logs -f news-wiki
```
