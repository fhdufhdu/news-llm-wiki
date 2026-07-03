# SQLite Codex Wiki 재설계

## 목적

News Wiki는 RSS 기사 요약 앱이 아니라, RSS로 원문을 수집하고 Codex가
SQLite에 직접 위키 데이터를 쓰는 개인용 LLM Wiki 시스템이다.

서버는 기계적인 수집과 웹 렌더링을 담당한다. Codex는 저장된 raw source를
읽고 섹션, 문서, 출처 연결, 변경 이력을 직접 갱신한다. AI는 위키 데이터
생성에만 사용하고, RSS fetch, URL 정규화, HTML fetch, 중복 제거, raw 저장은
LLM 판단 없이 수행한다.

## 핵심 원칙

- 기존 소스 코드는 참고 자료로만 본다. 필요한 구조는 새로 만든다.
- Spring Boot는 전형적인 layered architecture를 유지한다.
- 패키지 흐름은 `application / service / repository / infrastructure / dto`를
  따른다.
- controller는 service만 composition한다. repository를 직접 참조하지 않는다.
- service와 infrastructure의 강결합은 허용한다. 불필요한 port/adapter 추상화는
  만들지 않는다.
- YAGNI, KISS, DRY를 우선한다. 지금 필요한 수집, raw 저장, 위키 생성, 조회
  화면만 만든다.
- `GeekNews`는 section이 아니라 provider다.
- section navigation과 provider 목록은 hard-code하지 않고 DB에서 조회한다.

## 역할 분리

### 서버

서버는 수집기와 위키 뷰어다.

- RSS source를 읽는다.
- feed entry를 수집한다.
- URL을 정규화하고 중복을 제거한다.
- 기사 HTML 원문을 가져와 SQLite에 저장한다.
- fetch 실패 항목을 retry queue에 저장한다.
- 정해진 주기로 수집 job과 Codex wiki job을 실행한다.
- 작업 로그와 진행 상태를 웹에서 보여준다.
- 위키 테이블은 조회만 하고 수정하지 않는다.

### Codex

Codex는 위키 작성자이자 편집자다.

- Python helper를 통해 SQLite를 조회하고 업데이트한다.
- 수집 테이블은 읽기만 한다.
- 위키 테이블은 읽고 쓴다.
- 한 세션에서 최대 80건의 기사를 처리한다.
- 기사 1건을 읽고, 필요한 섹션과 위키 문서를 즉시 업데이트한다.
- 결과 파일을 서버가 import하는 방식은 사용하지 않는다.
- 진행 상황은 helper를 통해 DB 로그에 계속 기록한다.

## 데이터 모델

수집 영역과 위키 영역을 분리한다.

### 수집 테이블

서버가 read/write한다. Codex는 read-only로 사용한다.

#### `providers`

기사 제공사다. 언론사, GeekNews 같은 커뮤니티 소스가 여기에 들어간다.

주요 컬럼:

- `id`
- `name`
- `slug`
- `homepage_url`
- `enabled`
- `created_at`
- `updated_at`

#### `rss_feeds`

직접 RSS URL만 저장한다. RSS index 확장 방식은 사용하지 않는다.

주요 컬럼:

- `id`
- `provider_id`
- `title`
- `feed_url`
- `section_hint`
- `enabled`
- `created_at`
- `updated_at`

#### `articles`

기사 메타데이터와 처리 상태를 저장한다.

주요 컬럼:

- `id`
- `provider_id`
- `rss_feed_id`
- `title`
- `url`
- `canonical_url`
- `published_at`
- `collected_at`
- `raw_status`: `PENDING`, `FETCHED`, `FAILED`
- `wiki_status`: `PENDING`, `RUNNING`, `DONE`, `FAILED`
- `wiki_locked_at`
- `wiki_attempt_count`
- `wiki_last_error`
- `created_at`
- `updated_at`

`canonical_url`은 unique로 둔다.

#### `article_raw_sources`

기사 HTML 원문을 DB에 그대로 저장한다. gzip 파일이나 외부 raw 파일을 주 저장소로
사용하지 않는다.

주요 컬럼:

- `id`
- `article_id`
- `raw_html`
- `content_hash`
- `http_status`
- `fetched_at`
- `created_at`

`article_id`는 unique로 둔다.

#### `article_fetch_failures`

원문 fetch 실패 재처리 큐다.

주요 컬럼:

- `id`
- `provider_id`
- `rss_feed_id`
- `url`
- `canonical_url`
- `title`
- `failure_count`
- `last_error`
- `next_retry_at`
- `ignored`
- `created_at`
- `updated_at`

환경변수의 최대 retry 횟수를 넘으면 `ignored=true`로 둔다.

### 위키 테이블

Codex가 read/write한다. 서버는 read-only로 조회한다.

#### `wiki_sections`

Codex가 자유롭게 생성, 수정, 삭제할 수 있는 위키 섹션이다. 서버는 seed하거나
보정하지 않고, 현재 DB 상태를 그대로 navigation으로 렌더링한다.

주요 컬럼:

- `id`
- `slug`
- `title`
- `summary`
- `display_order`
- `status`: `ACTIVE`, `ARCHIVED`
- `created_at`
- `updated_at`

섹션 삭제는 실제 delete를 허용한다. 연결된 page는 helper가 다른 섹션으로
이동하거나 `section_id=null` 상태로 만든다.

#### `wiki_pages`

누적 갱신되는 실제 위키 문서다.

주요 컬럼:

- `id`
- `section_id`
- `slug`
- `title`
- `summary`
- `body`
- `importance`
- `status`: `ACTIVE`, `ARCHIVED`
- `created_at`
- `updated_at`

문서 본문은 기사 단위 요약이 아니라 여러 원문을 엮은 지식 베이스 문서여야 한다.

#### `wiki_page_sources`

위키 문서와 근거 기사 연결이다.

주요 컬럼:

- `id`
- `wiki_page_id`
- `article_id`
- `contribution_summary`
- `evidence_type`
- `created_at`

`wiki_page_id, article_id`는 unique로 둔다.

#### `wiki_revisions`

Codex가 위키 문서를 어떻게 바꿨는지 남기는 변경 이력이다.

주요 컬럼:

- `id`
- `wiki_page_id`
- `article_id`
- `wiki_run_id`
- `change_summary`
- `body_snapshot`
- `created_at`

v1에서는 복잡한 diff를 만들지 않는다. 현재 문서 스냅샷과 변경 요약이면 충분하다.

#### `wiki_runs`

Codex 세션 단위 실행 기록이다.

주요 컬럼:

- `id`
- `job_run_id`
- `status`: `RUNNING`, `SUCCESS`, `FAILED`
- `claimed_count`
- `done_count`
- `failed_count`
- `started_at`
- `finished_at`
- `last_message`

## Codex Python Helper 계약

Codex는 SQL을 직접 매번 짜기보다 helper를 사용한다. helper는 SQLite 접근,
상태 전이, 로그 기록을 캡슐화한다.

필수 함수:

- `claim_articles(limit=80)`: `wiki_status=PENDING` 기사를 잠그고 반환한다.
- `recover_stale_articles(timeout_minutes)`: 오래된 `RUNNING` 기사를 `PENDING`으로
  되돌린다.
- `get_article(article_id)`: 기사 메타데이터와 raw HTML을 반환한다.
- `extract_article_text(article_id)`: HTML에서 본문 후보 텍스트를 추출한다.
- `list_sections()`: 현재 섹션 목록을 반환한다.
- `create_section(title, summary, order_hint=None)`: 섹션을 만든다.
- `update_section(section_id, title=None, summary=None, display_order=None)`: 섹션을
  수정한다.
- `delete_section(section_id, move_pages_to=None)`: 섹션을 삭제하고 연결 문서를
  이동하거나 미분류 처리한다.
- `search_pages(query)`: 기존 위키 문서를 검색한다.
- `get_page(page_id)`: 위키 문서 하나를 반환한다.
- `upsert_page(section_id, title, summary, body, importance)`: 위키 문서를 생성하거나
  갱신한다.
- `link_source(page_id, article_id, contribution_summary, evidence_type=None)`: 문서와
  기사를 연결한다.
- `add_revision(page_id, article_id, change_summary, body_snapshot)`: 변경 이력을
  추가한다.
- `mark_article_done(article_id)`: 기사 상태를 `DONE`으로 바꾼다.
- `mark_article_failed(article_id, error)`: 기사 상태를 `FAILED`로 바꾼다.
- `progress(message, **counts)`: 작업 로그를 DB에 기록한다.

## Codex 처리 흐름

Codex는 한 세션에서 최대 80건을 처리한다. 단, 80건을 모두 처리한 뒤 결과를
저장하지 않고 기사 1건마다 DB에 즉시 반영한다.

1. `recover_stale_articles()`를 호출한다.
2. `claim_articles(80)`으로 대상 기사를 잡는다.
3. `progress()`로 시작 로그를 남긴다.
4. 기사 하나를 `get_article()`로 읽는다.
5. `extract_article_text()`로 본문 후보를 만든다.
6. 기존 `wiki_sections`와 `wiki_pages`를 조회한다.
7. 새 흐름이면 섹션과 문서를 만든다.
8. 기존 흐름이면 문서 본문을 갱신한다.
9. `link_source()`로 근거 기사를 연결한다.
10. `add_revision()`으로 변경 이력을 남긴다.
11. `mark_article_done()`으로 기사를 완료 처리한다.
12. 실패하면 `mark_article_failed()`와 `progress()`에 이유를 남긴다.
13. 다음 기사로 넘어간다.

Codex가 중간에 종료되어도 이미 반영된 문서는 유지된다. 다음 실행은 완료되지 않은
기사만 이어서 처리한다.

## 서버 화면

서버 UI는 위키 DB를 기준으로 렌더링한다.

### 홈

- `wiki_sections` navigation을 표시한다.
- 전체 섹션의 주요 흐름을 표시한다.
- 최근 갱신된 `wiki_pages`를 표시한다.
- 수집/위키 job 상태를 요약한다.

### 섹션 화면

- 선택한 `wiki_section`의 문서 목록을 표시한다.
- 문서 importance와 갱신 시각을 표시한다.
- 섹션 설명은 `wiki_sections.summary`에서 읽는다.

### 위키 문서 화면

- `wiki_pages.title`, `summary`, `body`를 표시한다.
- 연결된 `wiki_page_sources`를 출처 목록으로 표시한다.
- 필요한 경우 `wiki_revisions`를 변경 이력으로 표시한다.

### 기사 화면

기사 화면은 메인 경험이 아니라 디버그/근거 확인용이다.

- 기사 메타데이터
- raw fetch 상태
- 연결된 위키 문서
- 원문 URL

## 스케줄링

기본 스케줄:

- RSS 수집: 5분마다
- Codex wiki job: 수집 후 신규 `PENDING` 기사가 있으면 실행하거나, 별도 주기로 실행

Codex 작업이 이미 실행 중이면 새 Codex 작업은 시작하지 않는다.

## 복구 정책

- 서버 재시작 시 오래된 job lock을 해제한다.
- 오래된 `articles.wiki_status=RUNNING`은 `PENDING`으로 되돌린다.
- `DONE` 기사는 다시 처리하지 않는다.
- 원문 fetch 실패는 `article_fetch_failures`에서 retry 횟수와 `next_retry_at`으로
  관리한다.
- retry 한도를 넘은 fetch 실패는 무시 상태로 전환하고 로그를 남긴다.
- Codex 작업 실패는 기사 단위로 기록한다. 전체 세션 실패가 이미 처리된 문서를
  되돌리지는 않는다.

## 로그

작업 로그는 두 층이다.

### 서버 로그

- RSS source 수
- feed fetch 시작/완료
- 신규 기사 수
- 원문 fetch 성공/실패
- Codex 프로세스 시작/종료
- job 성공/실패

### Codex 진행 로그

Codex helper의 `progress()`가 DB에 기록한다.

- 현재 처리 중인 기사
- 생성/수정/삭제한 섹션
- 생성/수정한 위키 문서
- 연결한 출처
- 실패 이유
- 처리 카운트

잡 화면은 로그를 오래된 순서에서 최신 순서로 보여준다. 화면이 자동 갱신되더라도
사용자가 텍스트를 선택하고 복사할 수 있어야 한다.

## 테스트 기준

기본 검증:

```bash
rtk ./gradlew test
rtk ./gradlew bootJar
```

핵심 테스트:

- RSS feed 1개에서 기사 메타데이터가 저장된다.
- 기사 원문 HTML이 `article_raw_sources.raw_html`에 저장된다.
- 동일 canonical URL은 중복 저장되지 않는다.
- fetch 실패가 `article_fetch_failures`에 기록되고 retry 한도 이후 무시된다.
- helper가 pending 기사 1건을 claim하고 `wiki_status=RUNNING`으로 바꾼다.
- helper가 section을 생성, 수정, 삭제할 수 있다.
- helper가 wiki page를 생성하고 source와 revision을 남긴다.
- `DONE` 기사는 다시 claim되지 않는다.
- 오래된 `RUNNING` 기사는 복구 후 다시 claim된다.
- 홈/섹션/문서 화면은 repository를 직접 보지 않고 service를 통해 조회한다.
- section navigation은 hard-code 없이 DB 상태를 따른다.

## v1 범위 밖

- 전문 검색 엔진 도입
- 벡터 DB 도입
- 다중 사용자 권한
- 편집 UI
- 위키 문서 diff 렌더링
- 자동 품질 평가 모델
- 외부 raw 파일 기반 장기 아카이빙

이 항목들은 필요가 확인된 뒤 추가한다.
