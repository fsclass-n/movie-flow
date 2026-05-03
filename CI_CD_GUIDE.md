# CI/CD 자동화 설정 가이드

## 개요

이 프로젝트는 GitHub Actions를 사용하여 다음과 같은 CI/CD 파이프라인을 구성하고 있습니다:

- **Build & Test**: 빌드 및 테스트 자동화
- **Docker Build**: Docker 이미지 빌드 및 GitHub Container Registry 푸시
- **Render 배포**: Render에 자동 배포

## GitHub Actions 워크플로우

### 1. Build and Test (`.github/workflows/build.yml`)

**트리거 이벤트:**
- `main`, `develop` 브랜치에 push
- PR 생성/업데이트

**작업 내용:**
- JDK 21 설정
- Gradle로 빌드
- 단위 테스트 실행
- 테스트 결과 리포트 생성 및 업로드

**성공 시:**
- ✅ 빌드 및 테스트 완료
- 테스트 결과가 GitHub에 표시됨

**실패 시:**
- ❌ 빌드 또는 테스트 실패
- PR 체크가 실패 상태로 표시됨
- 배포 프로세스 중단

```yaml
# 수동 실행
git push origin main
# 또는 GitHub UI에서 "Run workflow" 클릭
```

### 2. Docker Build and Push (`.github/workflows/docker.yml`)

**트리거 이벤트:**
- `main` 브랜치에 push (src, build.gradle, Dockerfile 변경 시)
- 수동 실행 (workflow_dispatch)

**작업 내용:**
- Docker 이미지 빌드
- GitHub Container Registry (ghcr.io)에 푸시
- 이미지 태그: `latest`, 브랜치명, 버전, 커밋 SHA

**이미지 저장 위치:**
```
ghcr.io/{github-username}/movie-flow:latest
ghcr.io/{github-username}/movie-flow:main
ghcr.io/{github-username}/movie-flow:sha-{commit-sha}
```

**사용 권한 확인:**
```bash
# 개인 액세스 토큰(PAT) 생성 필요:
# GitHub → Settings → Developer settings → Personal access tokens
# scope: write:packages, read:packages
```

### 3. Deploy to Render (`.github/workflows/deploy.yml`)

**트리거 이벤트:**
- `main` 브랜치에 push
- 수동 실행 (workflow_dispatch)

**작업 내용:**
- 코드 체크아웃
- Gradle로 빌드
- Render Deploy Hook 호출
- 배포 상태 로깅

## 초기 설정 (필수)

### 1단계: GitHub 저장소 설정

```bash
# 로컬 저장소 생성 및 GitHub에 푸시
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/{github-username}/movie-flow.git
git push -u origin main
```

### 2단계: GitHub Secrets 설정

Render 배포를 위한 Deploy Hook 추가:

1. GitHub 저장소 → **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret** 클릭
3. 이름: `RENDER_DEPLOY_HOOK`
4. 값: Render Deploy Hook URL (아래 참조)

### 3단계: Render Deploy Hook 생성

1. [Render 대시보드](https://dashboard.render.com) 로그인
2. Web Service 선택 → **Settings**
3. **Deploy Hooks** 섹션
4. **Create Deploy Hook** 클릭
5. 생성된 URL 복사

```
https://api.render.com/deploy/srv-{service-id}?key={deploy-key}
```

6. GitHub의 `RENDER_DEPLOY_HOOK` Secret에 URL 저장

### 4단계: Docker Registry 인증 (선택)

Docker 이미지를 GitHub Container Registry에 푸시하기 위해:

```bash
# 개인 액세스 토큰(PAT) 생성
# GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
# scope: write:packages, read:packages, delete:packages

# 로컬에서 로그인 (선택)
echo "{YOUR_PAT_TOKEN}" | docker login ghcr.io -u {username} --password-stdin
```

## 워크플로우 실행

### 자동 실행

```bash
# main 브랜치에 push하면 모든 워크플로우 자동 실행
git add .
git commit -m "Update feature"
git push origin main
```

### 수동 실행

GitHub UI에서:
1. 저장소 → **Actions** 탭
2. 워크플로우 선택
3. **Run workflow** 버튼 클릭

또는 GitHub CLI:
```bash
gh workflow run build.yml --ref main
gh workflow run docker.yml --ref main
gh workflow run deploy.yml --ref main
```

## 모니터링

### GitHub Actions 대시보드
- 저장소 → **Actions** 탭
- 각 워크플로우의 상태 확인
- 실패 시 로그 상세 확인

### 실시간 로그 확인
```bash
# GitHub CLI로 실시간 로그 보기
gh run watch {run-id}

# 또는 최신 워크플로우
gh run list --limit 1
gh run view {latest-run-id} --log
```

### 배포 상태 확인

#### Render 대시보드
1. Render 대시보드 로그인
2. Web Service 선택
3. **Deploys** 탭에서 배포 상태 확인
4. **Logs** 탭에서 상세 로그 확인

#### 건강 상태 확인
```bash
# 배포된 애플리케이션 헬스 체크
curl https://{your-app}.onrender.com/health
```

## 문제 해결

### 1. 빌드 실패

**에러: "gradlew: command not found"**
```
해결: Dockerfile에 chmod +x gradlew 추가됨
```

**에러: "테스트 실패"**
```bash
# 로컬에서 테스트 재실행
./gradlew test

# 실패한 테스트만 실행
./gradlew test --tests {TestClass}
```

**에러: "메모리 부족"**
```yaml
# .github/workflows/build.yml의 JAVA_OPTS 조정
env:
  JAVA_OPTS: -Xmx256m -Xms128m
```

### 2. Docker 빌드 실패

**에러: "Docker buildx not found"**
```
해결: setup-buildx-action 자동 설정 완료
```

**에러: "Authentication failed to GHCR"**
```
해결: GITHUB_TOKEN은 자동으로 제공됨
```

### 3. Render 배포 실패

**에러: "RENDER_DEPLOY_HOOK not set"**
```
해결: 위의 "초기 설정 → 2단계" 참조
```

**에러: "Render 배포 시간 초과"**
```
해결: 
1. Render 대시보드에서 이전 배포 취소
2. 메모리 설정 확인 (JAVA_OPTS)
3. 데이터베이스 연결 확인
```

## 성능 최적화

### 1. 빌드 캐시 활용

현재 설정에서 자동으로 GitHub Actions 캐시 사용:
```yaml
- uses: actions/setup-java@v4
  with:
    cache: gradle  # Gradle 캐시 자동 활용
```

### 2. 병렬 테스트 실행

```bash
# build.gradle
tasks.withType(Test) {
    useJUnitPlatform()
    maxParallelForks = Runtime.runtime.availableProcessors()
}
```

### 3. Docker 레이어 캐시

```yaml
- uses: docker/build-push-action@v5
  with:
    cache-from: type=gha  # GitHub Actions 캐시 사용
    cache-to: type=gha,mode=max
```

## 보안 고려사항

### 1. 비밀 정보 관리

❌ 하지 말 것:
- 코드에 비밀번호 포함
- 커밋 메시지에 민감 정보

✅ 올바른 방법:
- GitHub Secrets 사용
- 환경 변수로 전달
- 사전 커밋 훅으로 검증

### 2. 이미지 취약점 검사

```yaml
# Docker 빌드 전 base 이미지 검사
- name: Scan image vulnerabilities
  run: |
    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
      aquasec/trivy image movie-flow:latest
```

### 3. 액세스 제어

- RENDER_DEPLOY_HOOK은 protected branch에만 적용
- PAT 토큰 주기적 갱신 (최대 90일)
- 필요한 최소 권한만 부여

## 고급 설정

### 1. Slack 알림 추가

```yaml
- name: Notify Slack
  if: failure()
  uses: slackapi/slack-github-action@v1
  with:
    webhook-url: ${{ secrets.SLACK_WEBHOOK }}
    payload: |
      {
        "text": "❌ Build failed: ${{ github.ref }} by ${{ github.actor }}"
      }
```

### 2. SonarQube 코드 품질 분석

```yaml
- name: SonarQube scan
  uses: SonarSource/sonarcloud-github-action@master
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
```

### 3. 자동 리리스 생성

```yaml
- name: Create Release
  if: startsWith(github.ref, 'refs/tags/')
  uses: actions/create-release@v1
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## 참고 자료

- [GitHub Actions 공식 문서](https://docs.github.com/en/actions)
- [Render 배포 가이드](./RENDER_DEPLOYMENT.md)
- [Docker 빌드 best practices](https://docs.docker.com/develop/dev-best-practices/)
- [Gradle 빌드 최적화](https://docs.gradle.org/current/userguide/build_performance.html)
