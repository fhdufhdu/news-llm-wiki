# News Wiki 디자인 시스템

## 문서 목적

이 문서는 개인용 News Wiki 서비스의 웹 화면을 위한 디자인 시스템이다.
시각적 기준은 네이버 뉴스처럼 정보 밀도가 높고 텍스트 중심인 포털형
뉴스 UI를 참고하되, 목적은 단순 뉴스 소비가 아니라 RSS 기사, AI 요약,
주제별 위키 데이터, 제공사별 관점을 함께 탐색하는 데 둔다.

이 문서는 다음 화면군을 포함한다.

- 오늘의 뉴스 홈
- 주제 섹션 페이지
- 제공사별 요약 페이지
- 기사 상세/AI 노트 페이지
- 위키 토픽/엔티티/이벤트/클레임 페이지
- 작업 상태/설정 페이지

구현 코드는 아니며, Spring Boot MVC + 서버 렌더링 템플릿에서 일관되게
적용할 UI 구조, 토큰, 컴포넌트, 콘텐츠 규칙을 정의한다.

---

## 1. 제품 성격

News Wiki는 개인용 뉴스 지식 대시보드다. 사용자는 매시간 수집된 RSS
기사와 AI가 정리한 위키 데이터를 통해 오늘의 주요 흐름을 빠르게 보고,
필요하면 기사 원문, 주제, 관련 엔티티, 진행 중인 사건, 검증할 주장으로
들어간다.

원본 기사 수집은 RSS와 기사 HTML fetch를 통한 기계적 수집 작업이다. AI는
원본을 새로 크롤링하지 않고, 저장된 raw source를 바탕으로 위키 데이터와
요약을 생성한 결과로만 화면에 나타난다.

핵심 목표:

- 많은 기사와 제공사를 짧은 시간 안에 훑게 한다.
- “오늘의 주요 기사”와 “지속되는 지식 구조”를 분리해서 보여준다.
- 제공사별 관점과 주제별 흐름을 모두 탐색할 수 있게 한다.
- AI 요약 결과의 생성 시각, 근거 source_id, 원문 URL을 항상 남긴다.
- 개인용 서비스이므로 과도한 CTA, 마케팅 영역, 소셜 기능을 넣지 않는다.

---

## 2. 핵심 UX 원칙

### 2.1 텍스트 우선

- 기사 제목, 제공사, 발행 시각, AI 요약, 관련 주제가 썸네일보다 우선이다.
- 썸네일은 선택 사항이며 기본 리스트에서는 사용하지 않는다.
- 제목은 한눈에 스캔 가능해야 하며 2줄을 넘기지 않는다.

### 2.2 정보 밀도 유지

- 여백은 충분히 주되 카드형 장식을 과하게 쓰지 않는다.
- 페이지는 여러 “모듈 블록”의 조합으로 구성한다.
- 모듈은 제목, 메타, 리스트, 보조 액션 순서의 반복 구조를 가진다.

### 2.3 출처와 생성 상태 명시

- 모든 기사와 요약에는 제공사, 발행 시각, 수집 시각 또는 요약 생성 시각을 둔다.
- AI 요약에는 “근거 기사 수”와 “마지막 생성 시각”을 표시한다.
- 위키 페이지는 대표 source_id와 전체 인덱스 링크를 제공한다.

### 2.4 뉴스와 위키의 분리

- 뉴스 홈은 “오늘 무엇이 중요한가”를 보여준다.
- 위키 페이지는 “여러 기사가 만든 지속 지식 구조”를 보여준다.
- 단발성 기사는 source note에 남기되, 억지로 토픽을 만들지 않는다.

### 2.5 개인용 운영 우선

- 로그인, 댓글, 공유, 광고, 구독 유도는 기본 화면에서 제외한다.
- 작업 상태, 수집 실패, Codex 실행 실패는 숨기지 않고 운영 화면에서 확인 가능해야 한다.

---

## 3. 정보 구조

### 3.1 전역 구조

모든 주요 페이지는 다음 계층을 따른다.

1. 상단 헤더
   - 서비스명: News Wiki
   - 검색 입력
   - 오늘 / 섹션 / 제공사 / 위키 / 작업 / 설정 링크

2. 1차 섹션 내비게이션
   - 전체
   - DB `sections` 테이블에서 조회한 활성 섹션

3. 페이지 제목 영역
   - 페이지명
   - 기준 시각
   - 수집 기사 수
   - AI 요약 생성 상태

4. 본문
   - 주요 요약 블록
   - 기사 리스트 또는 위키 리스트
   - 보조 패널

5. 하단 운영 정보
   - 마지막 수집
   - 마지막 AI 실행
   - 실패한 job 수

### 3.2 화면 유형

#### A. 오늘의 뉴스 홈

목적: 오늘 수집된 전체 기사에서 주요 흐름을 빠르게 파악한다.

구성:

- 오늘의 주요 기사
- 섹션별 요약
- 제공사별 주요 흐름
- 기술 섹션과 GeekNews 제공사 하이라이트
- 최근 작업 상태

#### B. 주제 섹션 페이지

목적: 특정 주제의 기사와 위키 연결을 탐색한다.

구성:

- 섹션 AI 요약
- 주요 기사
- 관련 토픽/엔티티/이벤트/클레임
- 최신 기사 리스트

#### C. 제공사별 요약 페이지

목적: 제공사마다 어떤 주제를 강조했는지 비교한다.

구성:

- 제공사 목록
- 선택 제공사의 주제별 요약
- 해당 제공사의 주요 기사
- 수집/실패 상태

#### D. 기사 상세/AI 노트 페이지

목적: 개별 기사 단위의 source note를 읽고 원문으로 이동한다.

구성:

- 제목, 제공사, 원문 URL, 발행 시각
- AI 요약
- 지속/일시 판정
- 관련 topic/entity/event/claim
- raw HTML 저장 상태

#### E. 위키 페이지

목적: 여러 기사에서 반복된 지속 지식 구조를 읽는다.

구성:

- 요약
- 대표 근거 기사
- 관련 주제/엔티티/사건/클레임
- 전체 source map 링크
- 열린 질문 또는 검증 필요 항목

#### F. 작업 상태/설정 페이지

목적: 개인 운영자가 수집과 AI 작업 상태를 확인한다.

구성:

- 최근 job run
- 실패한 feed/article/AI 작업
- 수동 실행 버튼
- Codex 로그인 상태
- RSS source 목록

---

## 4. 시각적 정체성

### 4.1 인상

- 포털형 뉴스 UI
- 텍스트 중심
- 고밀도지만 정돈된 리스트
- 흰 배경과 얇은 구분선
- 파스텔톤 하늘색을 브랜드/활성 상태에 제한적으로 사용

### 4.2 시각 키워드

- 구조화
- 신뢰
- 밀도
- 빠른 스캔
- 운영 가능성
- AI 근거성

---

## 5. 디자인 토큰

### 5.1 색상

```text
--color-brand-primary: #7cc7e8;
--color-brand-primary-hover: #5ab6df;
--color-brand-primary-strong: #2f8fcb;
--color-brand-primary-soft: #e9f7fd;
--color-brand-primary-softer: #f4fbfe;

--color-text-primary: #1c1c1c;
--color-text-primary-alt: #1e1e23;
--color-text-body: #303038;
--color-text-secondary: #595959;
--color-text-tertiary: #737373;
--color-text-muted: #8c8c8c;
--color-text-inverse: #ffffff;
--color-text-link: #2f8fcb;

--color-surface-page: #ffffff;
--color-surface-page-tint: #f5f9fc;
--color-surface-subtle: #f7f8fa;
--color-surface-subtle-alt: #fcfcfc;
--color-surface-raised: #ffffff;
--color-surface-selected: #e9f7fd;
--color-surface-hover: rgba(47, 143, 203, 0.06);

--color-border-subtle: #eef2f5;
--color-border-default: #dfe7ee;
--color-border-soft: #e8eef3;
--color-border-strong: #b8c7d3;
--color-border-selected: #7cc7e8;

--color-status-success: #16883c;
--color-status-warning: #b7791f;
--color-status-error: #d93025;
--color-status-info: #2f8fcb;
```

사용 규칙:

- 브랜드 하늘색은 로고, 활성 탭, 링크, AI 생성 상태 강조에만 쓴다.
- 기본 페이지는 흰색과 아주 옅은 하늘색 틴트로 구성한다.
- 성공 상태는 녹색을 유지하되 브랜드색처럼 사용하지 않는다.
- 기사 제목은 검정 계열을 유지한다.
- 배경은 흰색 중심, 보조 영역은 `--color-surface-page-tint` 또는 `--color-surface-subtle`을 사용한다.
- 섹션을 색상으로 과하게 구분하지 않는다.

### 5.2 타이포그래피

```text
--font-family-base: system-ui, -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif;

--font-size-xs: 12px;
--font-size-sm: 13px;
--font-size-md: 14px;
--font-size-lg: 16px;
--font-size-xl: 20px;
--font-size-page-title: 24px;

--font-weight-regular: 400;
--font-weight-medium: 500;
--font-weight-semibold: 600;
--font-weight-bold: 700;

--line-height-tight: 1.25;
--line-height-normal: 1.45;
--line-height-readable: 1.65;
```

규칙:

- 리스트 제목은 `14-16px`, 상세 제목은 `24px` 수준으로 제한한다.
- 메타 정보는 `12-13px`로 작게 유지한다.
- 긴 AI 요약은 `line-height-readable`을 사용한다.

### 5.3 간격

```text
--space-1: 4px;
--space-2: 8px;
--space-3: 12px;
--space-4: 16px;
--space-5: 20px;
--space-6: 24px;
--space-8: 32px;
```

### 5.4 반경 / 그림자

```text
--radius-sm: 4px;
--radius-md: 6px;
--radius-lg: 8px;

--shadow-none: none;
--shadow-popover: 0 8px 24px rgba(0, 0, 0, 0.08);
```

규칙:

- 카드 radius는 8px 이하.
- 리스트 기본 블록에는 shadow를 쓰지 않는다.
- 그림자는 드롭다운/팝오버에만 제한한다.

---

## 6. 레이아웃 시스템

### 6.1 데스크톱

```text
page
└─ .site-shell max-width 1180px
   ├─ header
   ├─ nav
   └─ main grid
      ├─ content column 760px
      └─ aside column 360px
```

규칙:

- 기본 최대 폭은 `1180px`.
- 홈과 섹션은 2컬럼.
- 상세 페이지는 본문 중심 1컬럼 + 얇은 관련 패널.
- 운영 페이지는 테이블 폭을 우선한다.

### 6.2 모바일

```text
page
└─ 단일 컬럼
   ├─ sticky compact header
   ├─ 가로 섹션 내비게이션
   └─ stacked modules
```

규칙:

- 모바일에서는 사이드바를 본문 하단으로 내린다.
- 섹션 탭은 가로 스크롤을 허용한다.
- 기사 리스트의 메타 정보는 한 줄로 축약한다.

### 6.3 모듈 블록

모든 블록은 다음 구조를 따른다.

```text
section.module
├─ .module-header
│  ├─ title
│  ├─ meta/count
│  └─ optional action
└─ .module-body
   └─ list/card/table
```

구분은 배경색보다 구분선과 간격을 우선한다.

---

## 7. 내비게이션

### 7.1 전역 헤더

구성:

- 좌측: `News Wiki` 로고 텍스트
- 중앙: 검색
- 우측: 작업 상태, 설정

상태:

- 마지막 수집 성공 시각
- 마지막 AI 생성 시각
- 실패 상태가 있으면 작은 warning badge

### 7.2 주요 내비게이션

```text
오늘 | 섹션 | 제공사 | 위키 | 작업 | 설정
```

### 7.3 섹션 내비게이션

```text
전체 | {DB sections ordered by display_order}
```

규칙:

- `전체`는 홈/전체 기사로 가는 고정 링크다.
- 그 외 섹션 탭은 DB `sections` 테이블의 `enabled = 1` 데이터만 조회한다.
- 정렬은 `display_order ASC, title ASC`를 사용한다.
- 섹션 URL은 `slug` 기반으로 `/sections/{slug}`를 사용한다.
- 선택된 탭은 하늘색 밑줄 또는 연한 하늘색 배경.
- 탭은 버튼처럼 과하게 꾸미지 않는다.
- 긴 라벨은 줄바꿈하지 않고 가로 스크롤한다.

---

## 8. 컴포넌트

### 8.1 기사 목록 항목

용도: 기사 목록의 기본 단위.

필드:

- 제목
- 제공사
- 발행 시각
- AI 요약 1-2줄
- topic/entity/event/claim chip
- 지속/일시 badge

규칙:

- 제목은 가장 강하게.
- 제공사와 시간은 제목 아래 작은 메타 행.
- 원문 이동은 제목 링크 또는 “원문” 링크로 제공.

### 8.2 주요 기사 항목

용도: 오늘의 주요 기사.

필드:

- 순번
- 제목
- 왜 주요 기사인지 한 줄 설명
- 제공사/시간
- 관련 위키 링크

규칙:

- 순번은 랭킹처럼 보이되 과도한 경쟁 UI로 만들지 않는다.
- 주요 기사 선정 근거를 짧게 표시한다.

### 8.3 요약 블록

용도: AI가 생성한 섹션/제공사 요약.

필드:

- 제목
- 요약 본문
- 근거 기사 수
- 주요 기사 링크
- 생성 시각

상태:

- fresh: 최신 요약
- stale: 수집은 됐지만 요약이 오래됨
- failed: 마지막 생성 실패
- pending: 생성 대기

### 8.4 제공사별 주제 블록

용도: 제공사별 -> 주제별 요약.

구성:

```text
provider header
├─ provider name
├─ collected count
└─ last fetched

topic rows
├─ topic name
├─ summary
└─ major articles
```

### 8.5 위키 링크 chip

종류:

- topic
- entity
- event
- claim

규칙:

- topic은 기본 chip.
- event는 점선 테두리.
- claim은 warning 계열의 얇은 강조.
- entity는 회색 chip.

### 8.6 작업 상태 badge

상태:

- `SUCCESS`
- `RUNNING`
- `FAILED`
- `SKIPPED`
- `TIMEOUT`

규칙:

- 운영 화면 외에서는 작은 badge만 표시.
- 실패 상태는 상세 페이지에서 stdout/stderr excerpt를 볼 수 있게 한다.

### 8.7 테이블

용도:

- 작업 이력
- RSS source 상태
- 제공사 목록

규칙:

- 행 높이는 작게.
- 실패/경고만 색으로 강조.
- 긴 URL은 줄임 처리하고 title로 전체 확인.

---

## 9. 페이지별 패턴

### 9.1 홈

목적: 오늘의 주요 흐름을 한 화면에서 파악한다.

레이아웃:

```text
main
├─ 오늘요약블록
├─ MajorArticles
├─ SectionSummaryList
└─ LatestArticles

aside
├─ JobStatusCompact
├─ ProviderHighlights
└─ ProviderQuickLink(GeekNews)
```

필수 표시:

- 기준 날짜
- 수집 기사 수
- AI 요약 생성 시각
- 주요 기사 8-12개
- 섹션 요약

### 9.2 섹션

목적: 주제별로 오늘의 기사와 위키 연결을 본다.

구성:

- 섹션 탭
- 선택 섹션 요약
- 주요 기사
- 관련 topic/entity/event/claim
- 최신 기사 리스트

### 9.3 제공사

목적: 제공사별 보도 경향을 본다.

구성:

- DB `providers` 테이블에서 조회한 활성 제공사 목록
- 선택 제공사의 주제별 요약
- 해당 제공사의 주요 기사
- 수집 실패 여부

특히 GeekNews는 섹션이 아니라 제공사로 취급하며, 제공사 목록과 홈 aside에서 별도 빠른 진입을 제공한다.

규칙:

- 제공사 목록은 `enabled = 1`인 `providers` row만 표시한다.
- 정렬은 `display_order ASC, name ASC`를 사용한다.
- RSS feed는 `rss_sources.provider_id`로 제공사에 연결된다.
- 기사는 `articles.provider_id`로 제공사에 연결된다.

### 9.4 기사 상세

목적: source-level note를 확인한다.

구성:

- 기사 제목과 메타
- 원문 링크
- AI 요약
- 추출된 지속 지식
- 관련 링크
- raw 저장 정보

본문 HTML 전체를 웹 UI에 기본 노출하지 않는다. 필요 시 운영자용 raw 보기로 제한한다.

### 9.5 위키 주제 상세

목적: 여러 기사를 묶은 지식 구조를 읽는다.

구성:

- topic 요약
- 관련 entities/events/claims
- 대표 근거
- source 수
- 연결된 기사 목록

### 9.6 작업

목적: 운영 상태 확인.

구성:

- 현재 실행 중 job
- 최근 job run table
- 실패 job 상세
- 수동 실행 버튼

수동 실행 버튼은 개인용 내부 서비스 전제에서 허용하되, 실행 중 중복 실행은 DB lock으로 막는다.

---

## 10. 콘텐츠 규칙

### 10.1 기사 제목

- 원문 제목을 그대로 표시한다.
- 불필요한 따옴표나 말줄임은 데이터에서 제거하지 않는다.
- 리스트에서는 2줄 clamp.

### 10.2 AI 요약

- 사실 요약과 해석을 섞지 않는다.
- “보도에 따르면”, “주장했다”, “예상된다”처럼 확정성과 주장성을 구분한다.
- 기사 본문을 길게 인용하지 않는다.
- source_id 또는 기사 링크를 통해 근거로 이동할 수 있어야 한다.

### 10.3 주요 기사 선정

우선순위:

1. 여러 제공사가 반복 보도한 사건.
2. topic/entity/event/claim을 동시에 연결하는 기사.
3. 경제·정치·사회 영향이 큰 정책/사건.
4. GeekNews 같은 기술 제공사의 중요한 릴리스/논쟁.
5. 단발성 연예/스포츠/날씨는 해당 섹션 내부에서만 주요 기사로 승격.

### 10.4 제공사별 요약

- 제공사의 전체 기사 수를 먼저 보여준다.
- 주제별 편향 또는 집중도를 요약한다.
- 제공사별 요약은 “좋다/나쁘다”가 아니라 “무엇을 많이 다뤘는가” 중심으로 쓴다.

---

## 11. 상태 설계

### 11.1 요약 상태

```text
PENDING
GENERATING
READY
STALE
FAILED
```

### 11.2 기사 AI 상태

```text
PENDING_AI
PROCESSING_AI
AI_READY
AI_FAILED
AI_SKIPPED
```

### 11.3 수집 상태

```text
DISCOVERED
FETCHED
FETCH_FAILED
DUPLICATE
```

### 11.4 빈 상태

- 신규 기사 없음: “새로 수집된 기사가 없습니다.”
- 요약 대기: “AI 요약 생성 대기 중입니다.”
- 제공사 기사 없음: “선택한 제공사의 오늘 기사가 없습니다.”
- Codex 미로그인: “Codex 로그인이 필요합니다. 컨테이너에서 `codex login`을 실행하세요.”

---

## 12. 접근성

- 모든 주요 영역은 `header`, `nav`, `main`, `aside`, `footer` landmark를 사용한다.
- 탭은 키보드 이동 가능해야 한다.
- 기사 제목 링크는 독립적으로 의미가 있어야 한다.
- 색상만으로 상태를 전달하지 않는다.
- 작은 메타 텍스트도 최소 12px 이상.
- 리스트 dense UI에서도 행간을 1.35 이상 유지한다.

---

## 13. 반응형 규칙

### 데스크톱

- 2컬럼 레이아웃 사용.
- aside에는 운영 상태, 제공사 하이라이트, GeekNews 같은 주요 제공사 빠른 진입을 배치.

### 태블릿

- 1컬럼과 2컬럼 사이에서 aside를 아래로 이동.
- 섹션 탭은 가로 스크롤.

### 모바일

- 모든 모듈 단일 컬럼.
- 기사 항목은 제목, 메타, 요약, chips 순서.
- 제공사/주제 비교 테이블은 카드 리스트로 전환.

---

## 14. 구현 가이드

Spring MVC 템플릿 구성:

```text
templates/
├─ layout/base.html
├─ fragments/header.html
├─ fragments/nav.html
├─ fragments/article-list-item.html
├─ fragments/summary-block.html
├─ fragments/wiki-chip.html
├─ pages/home.html
├─ pages/sections.html
├─ pages/providers.html
├─ pages/article-detail.html
├─ pages/wiki-detail.html
├─ pages/jobs.html
└─ pages/settings.html
```

CSS 구성:

```text
static/css/
├─ tokens.css
├─ base.css
├─ layout.css
├─ components.css
└─ pages.css
```

규칙:

- 컴포넌트별 CSS 클래스는 의미 기반으로 작성한다.
- Tailwind나 대형 UI 프레임워크 없이도 구현 가능해야 한다.
- JS는 최소화하고, 필요하면 HTMX를 작업 상태 갱신에만 사용한다.

---

## 15. 금지 사항

- 마케팅 랜딩 페이지처럼 큰 hero를 만들지 않는다.
- 기사 카드 안에 또 다른 카드 구조를 중첩하지 않는다.
- gradient blob, decorative orb, 과한 그림자 사용 금지.
- AI 요약을 원문 기사처럼 보이게 하지 않는다.
- 출처 없는 요약을 노출하지 않는다.
- 웹 요청에서 즉시 Codex 작업을 실행하지 않는다.
- 제공사/주제 비교 화면을 랭킹 경쟁처럼 과장하지 않는다.
