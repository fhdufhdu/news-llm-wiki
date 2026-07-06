# 수동 URL 기반 News Wiki 재설계

## 배경

News Wiki는 더 이상 RSS feed를 주기적으로 긁어오는 앱이 아니다. 사용자가 웹 화면에서 기사 URL을 여러 개 붙여넣으면 서버가 원문 HTML을 기계적으로 수집하고, Codex가 SQLite에 저장된 raw source를 읽어 기존 위키 문서를 참고하며 위키 데이터를 갱신한다.

LLM 사용 경계는 유지한다. URL fetch, canonical URL 정규화, HTML 저장, 중복 제거, 실패 재시도는 서버가 기계적으로 수행한다. Codex는 저장된 raw source를 읽고 `wiki_sections`, `wiki_pages`, `wiki_page_sources`, `wiki_revisions`를 갱신하는 작업에만 사용한다.

## 제거 범위

RSS 기능은 남겨두지 않는다. 다음 기능과 관련 코드는 제거한다.

- `rss-sources.yaml` 기반 source loader
- RSS parser와 RSS index expander
- RSS feed 주기 수집 scheduler
- RSS feed entry limit, RSS fetch timeout, RSS sources path 설정
- `rss_feeds` 중심의 ingest 흐름
- 화면 문구와 버튼의 `RSS 수집` 표현
- RSS source 개수, 직접 RSS, RSS index 같은 job log 문구

기존 DB migration은 이미 배포된 이력을 깨지 않기 위해 삭제하지 않는다. 대신 새 migration으로 수동 import용 테이블을 추가하고, 새 애플리케이션 코드에서는 RSS 테이블과 RSS source 설정을 사용하지 않는다.

## 새 사용자 흐름

1. 사용자는 웹 화면에서 기사 URL을 여러 줄로 붙여넣고 제출한다.
2. 서버는 import job을 만들고 URL마다 import item을 만든다.
3. 백그라운드 작업이 각 URL의 상태를 갱신하면서 HTML을 fetch한다.
4. fetch 성공 시 `articles`와 `article_raw_sources`에 원문 HTML을 저장한다.
5. 저장된 기사는 `wiki_status=PENDING` 상태가 된다.
6. Codex wiki job이 pending 기사를 claim한다.
7. Codex는 helper로 SQLite를 조회하며 기존 section/page를 먼저 확인하고, 기존 문서에 병합하거나 새 문서를 만든다.
8. 화면은 import job 전체 진행률과 URL별 상태를 계속 보여준다.
9. 완료 후 홈/섹션/위키 상세 화면은 위키 테이블만 기준으로 렌더링한다.

## 데이터 모델

### `article_import_jobs`

사용자가 한 번 제출한 URL 묶음을 나타낸다.

- `id`
- `status`: `PENDING`, `RUNNING`, `DONE`, `FAILED`, `PARTIAL_FAILED`
- `total_count`
- `fetched_count`
- `wiki_done_count`
- `failed_count`
- `created_at`
- `started_at`
- `finished_at`
- `last_message`

### `article_import_items`

URL별 진행 상태를 나타낸다.

- `id`
- `job_id`
- `input_url`
- `canonical_url`
- `article_id`
- `status`: `PENDING`, `FETCHING`, `FETCHED`, `WIKI_PENDING`, `WIKI_RUNNING`, `DONE`, `FAILED`
- `error_message`
- `attempt_count`
- `created_at`
- `updated_at`

`input_url` 중복은 같은 job 안에서 제거한다. `canonical_url`이 기존 `articles.canonical_url`과 같으면 원문을 다시 저장하지 않고 기존 article과 연결한다.

## 백그라운드 처리

수동 import는 submit 직후 비동기로 실행한다. 서버 재시작 시 `RUNNING`, `FETCHING`, `WIKI_RUNNING` 상태의 job/item은 재개 가능한 상태로 되돌린다.

원문 fetch는 서버 내부 executor에서 처리한다. 개인용 시스템이므로 처음 구현은 단순한 제한 병렬 처리로 충분하다. 실패한 item은 실패 사유와 attempt count를 저장하고, retry 버튼은 후속 확장으로 둔다.

Codex wiki 생성은 기존 `CodexWikiService` 구조를 유지한다. 다만 트리거는 RSS ingest 완료가 아니라 수동 import에서 raw 저장이 완료된 후 실행된다. job 화면에서는 fetch 진행과 wiki 진행을 모두 보여준다.

## 화면

### URL 제출 화면

- 여러 줄 URL textarea
- 제출 버튼
- 중복/빈 줄 제거 결과 안내
- 제출 후 job 상세 화면으로 이동

### Import Job 상세 화면

상단에 전체 요약을 표시한다.

- 전체 URL 수
- 원문 저장 완료 수
- 위키 반영 완료 수
- 실패 수
- 현재 상태 메시지

하단에는 URL별 상태표를 표시한다.

- 입력 URL
- canonical URL
- 상태
- 연결된 article/wiki 정보
- 실패 사유
- 갱신 시각

진행 화면은 자동 갱신하되, 사용자가 로그나 표 텍스트를 선택 중이면 DOM을 갈아끼우지 않는다.

## Codex 위키 생성 규칙

Codex는 각 기사 처리 전에 기존 위키를 반드시 확인한다.

- `list_sections()`로 현재 section 구조를 확인한다.
- 기사 제목, 핵심 엔티티, 주제 키워드로 `search_pages()`를 호출한다.
- 후보 문서가 있으면 `get_page()`로 기존 내용을 읽는다.
- 기존 문서가 있으면 새 기사 정보를 기존 durable knowledge에 병합한다.
- 새 문서는 기존 section/page로 설명하기 어려운 경우에만 만든다.
- 처리 결과는 `wiki_page_sources`와 `wiki_revisions`에 남긴다.

## 테스트

- URL 여러 줄 입력 파싱 테스트
- import job/item repository 테스트
- fetch 성공 시 raw HTML이 SQLite에 저장되는 테스트
- 중복 URL이 기존 article과 연결되는 테스트
- RSS service/parser/source loader 제거 후 컴파일 테스트
- controller가 repository를 직접 참조하지 않는 구조 확인
- `rtk ./gradlew test`
- `rtk ./gradlew bootJar`

## 비목표

- RSS 수집 유지
- 브라우저 확장/북마클릿
- 사용자 계정/권한
- 원문 본문 추출 품질 최적화
- 자동 재시도 UI
