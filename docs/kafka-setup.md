# Kafka 로컬 실행 가이드

## 1. Docker Compose로 Kafka 클러스터 실행

```bash
docker-compose -f docker-compose.kafka.yaml up -d
```

- **Zookeeper**: localhost:2181
- **Broker 1**: localhost:9092
- **Broker 2**: localhost:9093
- **Broker 3**: localhost:9094

애플리케이션은 기본적으로 `localhost:9092`로 연결합니다.

## 2. 애플리케이션 실행

Kafka가 실행된 상태에서 애플리케이션을 기동합니다.

```bash
./gradlew bootRun
```

또는 `local` 프로파일 사용 시:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 3. 토픽 생성 (선택)

Kafka는 존재하지 않는 토픽에 메시지를 보내면 자동으로 생성합니다.  
수동으로 생성하려면:

```bash
docker exec -it broker1 kafka-topics --create \
  --topic payment-completed \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1
```

## 4. Consumer 모니터링 (선택)

```bash
docker exec -it broker1 kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment-completed \
  --from-beginning
```

## 5. 정리

```bash
docker-compose -f docker-compose.kafka.yaml down
```

## 6. 테스트 시 Kafka 없이 실행

테스트는 `h2` 프로파일을 사용하며 `app.event.provider=spring-event`로 설정되어 있어  
Kafka 없이 Spring Event로 동작합니다.

## 7. Kafka E2E 통합 테스트 (Docker 필요)

실제 Kafka 브로커를 사용한 E2E 테스트는 `KafkaPaymentE2EIntegrationTest`에 구현되어 있습니다.

- **실행 조건**: Docker 실행 중
- **실행 방법**: `./gradlew test -PincludeKafkaE2e`
- **검증 내용**: Producer 발행 → Broker → Consumer 수신, 직렬화/역직렬화
