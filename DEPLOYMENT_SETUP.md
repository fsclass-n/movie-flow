# 🎬 MovieFlow - 배포 및 CI/CD 완료 설정

완료된 모든 작업의 요약 및 설정 체크리스트입니다.

## ✅ 완료된 작업

### 1. 명당 좌석 선정 기준 개선

**파일**: `rpa/scripts/seat_logic.py`

**변경사항:**
- ✅ 명당 좌석 기준: `전체 좌석의 20%` (기존: C~E행의 중앙 5석)
- ✅ 선정 기준: Center 기준 나선형 선택
- ✅ 함수 인터페이스:
  - `get_premium_seats(total_rows, seats_per_row)` - 명당 좌석 집합 반환
  - `is_premium_seat(row, seat_number, premium_seats)` - 명당 확인
  - `calculate_good_seats(seat_list, premium_seats)` - 명당 개수 계산

**적용된 파일:**
- `rpa/scripts/theater_crawler.py` - 새 함수 호출 구현

**테스트 방법:**
```bash
# 로컬에서 RPA 테스트
python3 rpa/main.py

# 명당 좌석이 전체 좌석의 약 20% 범위인지 확인
```

---

### 2. Render 배포용 Docker 설정

**파일들:**
- ✅ `Dockerfile` - 멀티 스테이지 빌드로 최적화
- ✅ `.dockerignore` - 불필요한 파일 제외
- ✅ `render.yaml` - Render 자동 배포 설정
- ✅ `RENDER_DEPLOYMENT.md` - 배포 가이드

**Dockerfile 개선사항:**
- 멀티 스테이지 빌드: Gradle 빌드 환경 분리
- 경량 이미지: `openjdk:21-slim` 기반
- 헬스 체크: 자동 재시작 설정
- 환경 변수: 동적 설정 지원
- 타임존: Asia/Seoul 설정

**배포 단계:**
1. GitHub에 코드 푸시
2. Render 대시보드에서 GitHub 저장소 연결
3. 자동으로 `render.yaml` 감지 및 배포
4. 또는 Deploy Hook으로 자동 배포

**배포 확인:**
```bash
# 배포 URL 확인
https://<your-app>.onrender.com

# 헬스 체크
curl https://<your-app>.onrender.com/health
```

---

### 3. CI/CD 자동화 (GitHub Actions)

**생성된 워크플로우:**

#### 3-1. Build & Test (`.github/workflows/build.yml`)
- **트리거**: main, develop 브랜치 push 및 PR
- **작업**: 빌드, 테스트, 리포트 생성
- **산출물**: 테스트 결과 아티팩트

#### 3-2. Docker Build and Push (`.github/workflows/docker.yml`)
- **트리거**: main 브랜치 push
- **작업**: Docker 이미지 빌드 및 GitHub Container Registry 푸시
- **저장소**: `ghcr.io/{username}/movie-flow`

#### 3-3. Deploy to Render (`.github/workflows/deploy.yml`)
- **트리거**: main 브랜치 push
- **작업**: Render Deploy Hook 호출
- **결과**: 자동 배포 트리거

**가이드 문서:**
- ✅ `CI_CD_GUIDE.md` - 상세 설정 및 사용 가이드
- ✅ `RENDER_DEPLOYMENT.md` - Render 배포 가이드

---

## 🚀 빠른 시작

### Step 1: GitHub 저장소 준비

```bash
# 저장소 클론 또는 초기화
git init
git add .
git commit -m "Initial commit with CI/CD setup"
git branch -M main
git remote add origin https://github.com/{username}/movie-flow.git
git push -u origin main
```

### Step 2: GitHub Secrets 설정

1. 저장소 → **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret** 추가:
   - **Name**: `RENDER_DEPLOY_HOOK`
   - **Value**: `https://api.render.com/deploy/srv-{id}?key={key}`

Render Deploy Hook 찾기:
1. Render 대시보드 → Web Service
2. Settings → Deploy Hooks → Deploy Hook URL 복사

### Step 3: Render 배포 연결

```bash
# 방법 1: GitHub 직접 연결 (권장)
1. Render 대시보드 → New Web Service
2. GitHub 연결
3. 저장소 선택
4. Dockerfile 선택

# 방법 2: 수동 연결
1. Render에서 Docker Web Service 생성
2. Docker Hub 또는 GHCR에서 이미지 선택
3. Deploy
```

### Step 4: 배포 자동화 확인

```bash
# 코드 변경 후 push
git add .
git commit -m "Add new feature"
git push origin main

# GitHub Actions 자동 실행
# → Build & Test → Docker Build → Deploy to Render
```

배포 상태 확인:
- **GitHub**: 저장소 → Actions 탭
- **Render**: 대시보드 → Web Service → Deploys 탭

---

## 📋 초기화 체크리스트

배포 전에 다음을 확인하세요:

### 필수 항목
- [ ] `build.gradle` - Java 버전 21 확인
- [ ] `application.properties` - 데이터베이스 설정
- [ ] `.github/workflows/*.yml` - 워크플로우 파일 생성 확인
- [ ] `Dockerfile` - 멀티 스테이지 빌드 설정
- [ ] `render.yaml` - 환경 변수 설정 완료

### Render 설정
- [ ] GitHub 저장소 공개(Public) 또는 Render 권한 부여
- [ ] 데이터베이스 생성 또는 외부 DB 연결 정보 준비
- [ ] 환경 변수 설정:
  ```
  SPRING_DATASOURCE_URL
  SPRING_DATASOURCE_USERNAME
  SPRING_DATASOURCE_PASSWORD
  SPRING_PROFILES_ACTIVE=prod
  ```

### GitHub 설정
- [ ] Secrets 추가: `RENDER_DEPLOY_HOOK`
- [ ] 브랜치 보호 규칙 설정 (선택)
- [ ] CI/CD 상태 체크 활성화

---

## 📁 생성된 파일 구조

```
movie-flow/
├── .github/workflows/
│   ├── build.yml                    # Build & Test 워크플로우
│   ├── docker.yml                   # Docker Build & Push
│   └── deploy.yml                   # Render 배포
├── rpa/
│   └── scripts/
│       ├── seat_logic.py           # ✅ 명당 좌석 로직 개선
│       └── theater_crawler.py      # ✅ 새 함수 적용
├── Dockerfile                       # ✅ 최적화된 멀티 스테이지 빌드
├── .dockerignore                    # ✅ Docker 빌드 최적화
├── render.yaml                      # ✅ Render 자동 배포 설정
├── CI_CD_GUIDE.md                  # ✅ CI/CD 상세 가이드
├── RENDER_DEPLOYMENT.md            # ✅ Render 배포 가이드
└── src/
    └── main/
        ├── java/com/onrender/movieflow/
        │   ├── controller/
        │   ├── service/
        │   ├── repository/
        │   └── dto/
        └── resources/
            ├── application.properties
            ├── schema.sql
            └── data.sql
```

---

## 🔍 배포 문제 해결

### 빌드 실패
```
❌ "gradlew: command not found"
✅ 해결: Dockerfile의 chmod +x gradlew 확인

❌ 테스트 실패
✅ 해결: 로컬에서 ./gradlew test 실행하여 확인

❌ 메모리 부족
✅ 해결: JAVA_OPTS 환경 변수 조정
```

### Render 배포 실패
```
❌ "RENDER_DEPLOY_HOOK not set"
✅ 해결: GitHub Secrets 추가 (위의 Step 2 참조)

❌ 데이터베이스 연결 오류
✅ 해결: 환경 변수 확인, 방화벽 설정

❌ 배포 시간 초과
✅ 해결: 이전 배포 취소, 메모리 설정 확인
```

### Docker 빌드 실패
```
❌ "base image not found"
✅ 해결: Docker Hub 연결 확인

❌ "Authentication failed"
✅ 해결: GitHub Token 권한 확인 (write:packages)
```

---

## 📚 참고 자료

### 공식 문서
- [GitHub Actions](https://docs.github.com/en/actions)
- [Render Docs](https://render.com/docs)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Spring Boot Deployment](https://spring.io/guides/gs/spring-boot-docker/)

### 추가 설정
- **Slack 알림**: CI_CD_GUIDE.md 참고
- **코드 품질 분석**: SonarQube 연동 설정
- **자동 릴리스**: GitHub Releases 자동 생성

---

## 💡 다음 단계 (선택)

### 성능 최적화
- [ ] CDN 설정 (Cloudflare)
- [ ] Redis 캐싱 추가
- [ ] 데이터베이스 인덱싱 최적화

### 모니터링
- [ ] 로그 수집 (ELK Stack)
- [ ] 성능 모니터링 (New Relic, DataDog)
- [ ] 알림 설정 (Slack, Email)

### 보안 강화
- [ ] SSL/TLS 설정
- [ ] 취약점 스캔 (Trivy)
- [ ] 의존성 보안 검사 (Snyk)

---

## 📞 지원

문제 발생 시:
1. `CI_CD_GUIDE.md` - CI/CD 문제 해결
2. `RENDER_DEPLOYMENT.md` - Render 배포 문제
3. GitHub Actions 로그 확인
4. Render 대시보드 로그 확인

---

**완성일**: 2026-05-03
**상태**: ✅ 완료 및 테스트 준비 완료
