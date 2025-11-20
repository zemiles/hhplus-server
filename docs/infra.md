    flowchart LR
    user[User PC] -->|443 HTTPS| edge[CDN_WAF]
    edge --> lb[API_Gateway_or_L7_LB]

    -High-level 아키텍처
    subgraph Compute
        app1[App Server / Pod #1]
        appN[App Server / Pod #N]
        worker[Worker / Scheduler]
    end

    subgraph Cache_and_Queue
        redis[(Redis Cluster)]
    end

    subgraph DB
        mysqlw[(MySQL Primary Write)]
        mysqlr[(MySQL Read Replica)]
    end

    %% MQ는 선택(Outbox 먼저 → 필요 시 도입)
    mq[[Message_Queue (optional)]]

    lb --> app1
    lb --> appN
    app1 --> redis
    appN --> redis
    app1 --> mysqlw
    appN --> mysqlw
    mysqlw <--> mysqlr
    app1 --> mq
    appN --> mq
    mq --> worker
    worker --> mysqlw
    worker --> redis


    
    flowchart TB
    user[User PC] -->|443| cf[CloudFront_WAF]
    cf --> alb[ALB_HTTPS]

    배포 다이어그램 (AWS VPC / 멀티-AZ)
    subgraph VPC
        direction TB

        subgraph AZ_a
            direction TB
            subgraph Public_a
                alb
                natA[NAT_Gateway]
            end
            subgraph App_a
                asgA[App/Worker ASG or NodeGroup]
                redisA[(Redis_Node)]
            end
            subgraph DB_a
                rdsA[(MySQL_Primary)]
            end
        end

        subgraph AZ_b
            direction TB
            subgraph Public_b
                natB[NAT_Gateway]
            end
            subgraph App_b
                asgB[App/Worker ASG or NodeGroup]
                redisB[(Redis_Replica)]
            end
            subgraph DB_b
                rdsB[(MySQL_Standby/Replica)]
            end
        end

        asgA -->|6379| redisA
        asgB -->|6379| redisA
        redisA --> redisB

        asgA -->|3306| rdsA
        asgB -->|3306| rdsA

        alb --> asgA
        alb --> asgB
    end

    asgA -->|NAT| natA
    asgB -->|NAT| natB

--

    보안/네트워크 요약
    WAF/ALB: L7 필터링, DDoS 보호(Shield), HTTPS 종단

    보안 그룹

    ALB → App : 443/80 허용 (ALB SG만)

    App → Redis : 6379 (App SG → Redis SG)

    App → MySQL : 3306 (App SG → DB SG)

    App/Worker 아웃바운드 → NAT 통해 인터넷 (PG 등 외부 콜)

    비밀 관리: AWS Secrets Manager/SSM Parameter Store

--
   
     4) 운영/확장 포인트

    오토스케일: 요청/큐 길이/CPU/HPA 기반 Pod 확장

    멀티 AZ: RDS MySQL Multi-AZ, Redis 복제/클러스터

    백업/DR: RDS 자동백업, Redis 스냅샷, IaC(Terraform/CloudFormation)

    모니터링: CloudWatch + APM(OpenTelemetry/Jaeger)

    도메인 대시보드: 대기열 길이/READY 승격률/홀드 TTL 만료율/결제 실패율


    🧱 컴포넌트 인벤토리 (역할/사용 여부)

    패턴 기준: A = Gateway+Worker(+MQ), B = 단일 API.
    표기: ✅ 사용 / ☑️ 선택(옵션) / ❌ 미사용.

    | 컴포넌트                   | 역할(요약)                  |  A  |  B  | 매니지드 서비스 예시 (AWS / GCP / OCI)                           |
    | ------------------------- | -------------------------- | :-: | :-: | ------------------------------------------------------- |
    | L7 Load Balancer / API GW | HTTPS 종단, 라우팅, 보호     | ✅ | ✅ | ALB / Cloud LB / OCI LBaaS                              |
    | CDN / WAF                 | 엣지 캐시, L7 방어           | ☑️ | ☑️ | CloudFront+WAF / Cloud CDN+Armor / OCI CDN+WAF          |
    | Kubernetes Orchestrator   | 컨테이너 스케줄링/확장        | ✅ | ✅ | EKS / GKE / OKE                                         |
    | Containers (Docker)       | 앱 패키징/배포               | ✅ | ✅ | Docker / containerd                                     |
    | API Service (pod)         | 동기 HTTP API               | ✅ | ✅ | –                                                       |
    | Worker Service (pod)      | 비동기 작업·배치/스케줄러     | ✅ | ☑️ | –                                                       |
    | Message Queue             | 비동기/버퍼링/재시도(선택)    | ☑️ | ❌ | SQS/Rabbit/Kafka / Pub/Sub / OCI Streaming              |
    | Cache / Lock (Redis)      | 대기열 토큰·좌석 홀드 TTL·락  | ✅ | ✅ | ElastiCache Redis / Memorystore / OCI Cache Redis       |
    | RDB (MySQL, RW/RO)        | 영속 데이터, 트랜잭션         | ✅ | ✅ | RDS MySQL / Cloud SQL / OCI MySQL                       |
    | Object Storage            | 정적/백업/첨부               | ☑️ | ☑️ | S3 / GCS / OCI Object Storage                           |
    | Secrets Manager           | 키/자격증명 보관             | ✅ | ✅ | AWS SM / Secret Manager / OCI Vault                     |
    | Observability             | Log/Metric/Trace           | ✅ | ✅ | CloudWatch+OTel / Cloud Logging+Trace / OCI Logging+APM |
    | CI/CD                     | 빌드/배포 자동화             | ✅ | ✅ | GitHub Actions / Jenkins / Cloud Build / OCI DevOps     |
    | Autoscaling               | 수평 확장(HPA/ASG)           | ✅ | ✅ | HPA+Cluster Autoscaler / GKE AS / OKE AS                |
    | VPC/네트워킹               | 서브넷/보안그룹/NAT          | ✅ | ✅ | VPC / VPC / VCN                                         |
    | IAM                       | 권한/역할/정책               | ✅ | ✅ | IAM / IAM / IAM                                         |

--

    인프라 다이어그램
    flowchart TB
    user[User]
    subgraph cloud[Cloud]
    waf[CDN_WAF]
    lb[L7_Load_Balancer_or_API_GW]
    subgraph k8s[Kubernetes_Cluster]
    apisvc[API_Service]
    wsvc[Worker/Scheduler]
    mq[(Message_Queue optional)]
    redis[(Redis: TTL_Holds/Queue_Tokens/Lock)]
    end
    dbw[(MySQL_Primary_Write)]
    dbr[(MySQL_Read_Replica)]
    end

    user --> waf --> lb
    lb --> apisvc
    apisvc --> dbw
    dbw --- dbr
    apisvc --> redis
    apisvc --> mq
    mq --> wsvc
    wsvc --> dbw
    wsvc --> redis

