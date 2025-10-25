# InsightService - 사용자 분류 및 인사이트 생성

Python의 AI 모델링 프로젝트에서 통계 기반 사용자 분류 로직을 Java/Spring으로 이식한 프로젝트입니다.

## 주요 기능

### 1. 사용자 분류 (User Classification)

**행동 패턴 분류**
- `COMPULSIVE_CHECKER` (강박적 확인형): 높은 픽업 횟수(150회+) + 짧은 세션(30초 미만)
- `NORMAL_BEHAVIOR` (일반 행동): 정상 범위의 사용 패턴

**콘텐츠 선호 분류**
- `SOCIAL_FOCUSED` (소셜 집중형): 소셜/커뮤니케이션 비율 25% + 1시간 이상
- `GAME_FOCUSED` (게임 집중형): 게임 비율 30% + 2시간 이상
- `LEARNING_FOCUSED` (학습 집중형): 학습 비율 20% + 30분 이상
- `ENTERTAINMENT_FOCUSED` (엔터 집중형): 엔터 비율 25% + 1시간 이상
- `BALANCED` (균형잡힌 사용): 특정 카테고리 지배적이지 않음

### 2. Kafka 이벤트 구독

`DailyMetricsSaved` 이벤트를 구독하여 자동으로:
1. 일일 요약 데이터 저장 (`insight_daily_summary`)
2. 카테고리별 메트릭 저장 (`insight_daily_category_metrics`)
3. 사용자 분류 수행 및 저장 (`user_classification`)
4. 카테고리 트렌드 계산 및 저장 (`user_category_trends`)

### 3. 임계값 기준

Python의 `improved_threshold_calculator.py`에서 계산된 고정 임계값을 사용합니다.

#### 콘텐츠 선호 분류 임계값

| 분류                        | 비율 임계값 | 시간 임계값        | 설명                     |
|---------------------------|--------|---------------|------------------------|
| **SOCIAL_FOCUSED**        | 25% 이상 | 1시간(3600초) 이상 | 소셜/커뮤니케이션 앱 집중 사용      |
| **GAME_FOCUSED**          | 30% 이상 | 2시간(7200초) 이상 | 게임 앱 집중 사용 (소수 클래스 보호) |
| **LEARNING_FOCUSED**      | 20% 이상 | 30분(1800초) 이상 | 학습 앱 집중 사용             |
| **ENTERTAINMENT_FOCUSED** | 25% 이상 | 1시간(3600초) 이상 | 엔터테인먼트 앱 집중 사용         |
| **BALANCED**              | -      | -             | 특정 카테고리가 지배적이지 않음      |

#### 행동 패턴 분류 임계값

| 분류                     | 픽업 횟수   | 평균 세션 시간 | 설명                           |
|------------------------|---------|----------|------------------------------|
| **COMPULSIVE_CHECKER** | 150회 이상 | 30초 미만   | 강박적 확인형 (80th percentile 기준) |
| **NORMAL_BEHAVIOR**    | -       | -        | 정상 범위의 사용 패턴                 |

#### 임계값 계산 방법론

**비율 임계값:**
- 지배적 사용자 평균 (40%) + 75th percentile (30%) + Mean+0.5*Std (20%) + 실제 사용자 평균 (10%)

**시간 임계값:**
- 지배적 사용자 평균 (50%) + 중앙값 (50%)

**픽업 임계값:**
- 80th percentile 기준

## 프로젝트 구조 (Layered Architecture)

```
src/main/java/screentimeai/
├── presentation/                                 # 표현 계층
│   ├── controller/InsightController.java        # REST API 컨트롤러
│   └── dto/                                      # Response DTO
│       ├── BehaviorPatternResponse.java
│       └── ContentPreferenceResponse.java
├── application/                                  # 응용 계층
│   └── service/                                  # Use Case / Orchestration
│       ├── BehaviorPatternService.java          # 행동 패턴 조회
│       ├── ContentPreferenceService.java        # 콘텐츠 선호 조회
│       └── InsightPersistenceService.java       # 데이터 영속화
├── domain/                                       # 도메인 계층
│   ├── config/ClassificationThresholds.java     # 임계값 설정
│   ├── entity/                                   # JPA 엔티티
│   ├── model/UserClassification.java           # 분류 결과 모델
│   └── service/
│       └── UserClassificationService.java       # 분류 로직 (도메인)
└── infrastructure/                               # 인프라 계층
    ├── kafka/
    │   ├── DailyMetricsConsumer.java            # Kafka 이벤트 소비자
    │   └── KafkaProcessor.java
    ├── event/AbstractEvent.java
    └── repository/                               # JPA Repository
```

## 데이터베이스 스키마

**카테고리 ID 매핑:**
- 1: 게임 (game)
- 2: 소셜/커뮤니케이션 (social_comm)
- 3: 학습 (learning)
- 4: 엔터테인먼트 (entertainment)

## Running in local development environment

```
mvn spring-boot:run
```

## Packaging and Running in docker environment

```
mvn package -B -DskipTests
docker build -t username/insightservice:v1 .
docker run username/insightservice:v1
```

## Push images and running in Kubernetes

```
docker login 
# in case of docker hub, enter your username and password

docker push username/insightservice:v1
```

Edit the deployment.yaml under the /kubernetes directory:
```
    spec:
      containers:
        - name: insightservice
          image: username/insightservice:latest   # change this image name
          ports:
            - containerPort: 8080

```

Apply the yaml to the Kubernetes:
```
kubectl apply -f kubernetes/deployment.yaml
```

See the pod status:
```
kubectl get pods -l app=insightservice
```

If you have no problem, you can connect to the service by opening a proxy between your local and the kubernetes by using this command:
```
# new terminal
kubectl port-forward deploy/insightservice 8080:8080

# another terminal
http localhost:8080
```

If you have any problem on running the pod, you can find the reason by hitting this:
```
kubectl logs -l app=insightservice
```

Following problems may be occurred:

1. ImgPullBackOff:  Kubernetes failed to pull the image with the image name you've specified at the deployment.yaml. Please check your image name and ensure you have pushed the image properly.
1. CrashLoopBackOff: The spring application is not running properly. If you didn't provide the kafka installation on the kubernetes, the application may crash. Please install kafka firstly:

https://labs.msaez.io/#/courses/cna-full/full-course-cna/ops-utility

