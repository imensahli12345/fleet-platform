# Kafka in our project & Kubernetes deployment plan

_Notes saved from a Claude Code session, 2026-10-22 (branch `imen-sahli`)._

**Short version:** 1 topic (`incident-events`), 1 partition, no replication, 1 Kafka broker.
Producer = `shipment-service`, consumer = `notification-service`. Notifications are not really
sent to anyone: they are written to a log line and saved in MongoDB.

---

## 1. Kafka in our project (diagram)

```
                         (Postgres: fleet_db)
                                ▲
                                │ 1. save ShipmentException (@Transactional)
┌───────────────────────────────┴──────────────────┐
│ shipment-service  (port 8083)      PRODUCER      │
│  ShipmentService ──publishEvent()──► (waits)     │
│  2. DB COMMIT ✔ ──► IncidentEventPublisher       │
│       key   = shipmentId                         │
│       value = IncidentEvent (JSON)               │
│       acks  = all                                │
└──────────────────────────┬───────────────────────┘
                           │ 3. send(topic, key, event)
                           ▼
┌──────────────────────────────────────────────────┐
│ Kafka BROKER "fleet-kafka" (1 node, KRaft,       │
│ broker + controller in the same process)         │
│   localhost:9092                                 │
│                                                  │
│  Topic: incident-events                          │
│  ┌── Partition 0 (leader, no replica) ────────┐  │
│  │ off 0      off 1      off 2      off 3     │  │
│  │ CREATED A  CREATED B  RESOLVED A RESOLVED B│→ │
│  └────────────────────────────────────────────┘  │
│  __consumer_offsets: "notification-service       │
│                       group is at offset 3"      │
└──────────────────────────┬───────────────────────┘
                           │ 4. poll (group-id = notification-service)
                           ▼
┌──────────────────────────────────────────────────┐
│ notification-service (port 8084)    CONSUMER     │
│  IncidentEventListener → NotificationService     │
│   5. id = exceptionId:eventType                  │
│   6. already in Mongo? → skip (duplicate)        │
│   7. pick message, status SENT / NO_RECIPIENT    │
│   8. log "NOTIFY customer=..."  (fake channel)   │
│   9. save to MongoDB notification_db             │
│  10. offset committed                            │
└──────────────────────────┬───────────────────────┘
                           ▼
         GET /api/notifications (via api-gateway 8089)
```

## 2. Questions & answers

**Topic:** one topic, `incident-events`, carrying two event types: `INCIDENT_CREATED` and `INCIDENT_RESOLVED`.

**Number of partitions: 1.** No code creates the topic (no `NewTopic` bean). Kafka auto-creates it on
the first send (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"`) with the default `num.partitions=1`.
To check while Kafka is running:

```
docker exec fleet-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic incident-events
```

**Producer:** `shipment-service` → `IncidentEventPublisher.java`, triggered when an exception is created or
resolved in `ShipmentService.java`.

**Consumer:** `notification-service` → `IncidentEventListener.java`, consumer group `notification-service`.

**What is the shipmentId used for?** It does **not** choose the topic (the topic is fixed in config).
It is the **message key**, and the key chooses the **partition**:

```
partition = hash(shipmentId) % numberOfPartitions
```

- **Why:** Kafka guarantees order only *inside one partition*. Same shipmentId → same partition →
  for shipment A, `CREATED` is always processed before `RESOLVED`. Without a key they could land
  on two partitions and `RESOLVED` could be processed first.
- **Today**, with 1 partition, everything is ordered anyway. The key matters once we add partitions
  to scale: shipments are spread across partitions, each shipment stays in order.
- **How we partitioned:** no custom partitioner; we set a key and Kafka's default partitioner hashes it.

**Order in the partition:** append-only log, read in order (FIFO). Order is guaranteed per partition,
not across partitions.

**Replication: none.** 1 broker → replication factor 1 (you cannot have more copies than brokers).
The compose file sets `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1`. If that broker's disk is lost,
the events are lost. `acks=all` only gives real safety once there are replicas.

**Kafka instances / brokers:** exactly **1 broker**, container `fleet-kafka`, in **KRaft** mode
(no ZooKeeper). The same process is both broker (stores messages) and controller (manages the
cluster). Ports: 9092 for clients, 9093 for the controller.

**Offsets (with our example):** an offset is the position number of a message in a partition: 0, 1, 2, 3…

- When shipment-service publishes, it logs `partition=0 offset=2` → "your event is the 3rd message of partition 0".
- The consumer group stores "processed up to offset X" in the internal topic `__consumer_offsets`.
  If notification-service restarts, it continues from there.
- `auto-offset-reset: earliest` → the very first time the group starts (no saved offset), it reads from offset 0.
- If the service crashes after saving to Mongo but before committing the offset, Kafka delivers the
  message again. That's why the Mongo id is `exceptionId:eventType`: the duplicate is detected and
  skipped (**idempotent consumer**).

**Are notifications sent? No.** The channel is `LOG` (`Notification.java`, `NotificationService.java`).
"Sending" = a `log.info("NOTIFY customer=...")` line. The notification is then stored in MongoDB with
status `SENT` (or `NO_RECIPIENT` if the shipment has no linked customer). Customers/dispatchers read
them through `GET /api/notifications`. No email/SMS yet.

**How the consumer processes an event:**

1. Deserialize JSON (`ErrorHandlingDeserializer` skips a malformed message instead of blocking the partition)
2. Duplicate check
3. Pick the message (AI text for CREATED, fixed text for RESOLVED)
4. Set the status
5. Write the log line
6. Save to Mongo
7. Commit the offset

**Where everything lives:**

| What | File |
|---|---|
| Broker | `docker-compose.yml` (service `kafka`) |
| Producer config (serializers, acks, timeouts) | `backend/config-repo/shipment-service.yml` |
| Consumer config (group, offset reset, deserializers) | `backend/config-repo/notification-service.yml` |
| Producer code | `backend/shipment-service/.../event/IncidentEventPublisher.java`, `IncidentEvent.java` |
| Consumer code | `backend/notification-service/.../kafka/IncidentEventListener.java`, `service/NotificationService.java` |
| Dependency | `spring-kafka` in both services' `pom.xml` |

**Is Kafka important here?** Moderately. It is not on the critical path: if Kafka is down, shipments
still work (the publish failure is logged and ignored). Its value is **decoupling**: shipment-service
doesn't wait for or depend on notification-service, and if notification-service is down, events wait
in Kafka until it comes back. Weak spots:

- If Kafka is down at publish time, that event is lost (fix: an "outbox" table).
- If Mongo is down, Spring retries a few times, then skips the event.

## 3. Commits to make (state on 2026-10-22)

The branch had 16 modified and 12 new files uncommitted. Suggested split:

1. **Build:** `pom.xml` fix (repackage plugin + UTF-8).
2. **Docker:** the 7 `Dockerfile`s + `.dockerignore`.
3. **Feature "unassign driver from truck":**
   - fleet-service: controller, service, test, `client/`, Feign config, `fleet-service.yml` timeouts, `pom.xml`
   - shipment-service: `/trucks/{id}/active` endpoint and repository method
   - only ASSIGNED trucks get auto-assigned
4. **Config:** gateway route to notification-service, Eureka `prefer-ip-address`, `DB_PASSWORD:admin` default.

Git flags the folder as "dubious ownership". Fix once:
`git config --global --add safe.directory "D:/Plateforme de Suivi de Flotte de Camions"`

## 4. Deploying on Kubernetes

**Infrastructure:**

```
             Internet / browser
                     │
              ┌──────▼──────┐
              │   Ingress   │  (entry point, one URL)
              └──────┬──────┘
   namespace: fleet  │
   ┌─────────────────▼──────────────────────────────────────┐
   │ Service → Deployment api-gateway (2 pods)              │
   │                 │                                      │
   │  config-server (1-2 pods)   discovery-server/Eureka    │
   │                 │                                      │
   │  auth  fleet  shipment  notification   ai-service      │
   │  (each = Deployment + Service, 2 pods for HA)          │
   │                 │                                      │
   │  ── stateful layer (StatefulSet + PersistentVolume) ── │
   │   Postgres        MongoDB        Kafka                 │
   │                                                        │
   │  ConfigMap (URLs, non-secret)   Secret (passwords, JWT)│
   └────────────────────────────────────────────────────────┘
```

- **Pod:** one running container.
- **Deployment:** keeps N copies of a pod alive and replaces any that die.
- **Service:** a stable DNS name (e.g. `kafka:9092`) in front of pods that come and go.
- **StatefulSet + PersistentVolume:** for databases and Kafka, so data survives a restart.

**Steps, in order:**

1. **Commit and push** the current work.
2. **Remove hardcoded `localhost`.** Postgres, Mongo, Kafka, Eureka (`localhost:8761`) and the AI service
   (`localhost:8000`) are hardcoded. Use placeholders in `config-repo`, e.g.
   `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`. Important: properties from the Config Server override
   plain env variables, so the placeholder is required.
3. **Move secrets out of config files** (JWT secret, DB passwords → Kubernetes Secrets).
4. **Test the whole stack in Docker Compose first.** Add all services to `docker-compose.yml`. Fix Kafka's
   `ADVERTISED_LISTENERS` (`localhost` doesn't work between containers). If it works in Compose,
   Kubernetes is mostly translation.
5. **Containerize `ai-service`** (lives outside this repo, `D:\ai-service`).
6. **Pick a local cluster** (Minikube or Docker Desktop Kubernetes); push images to Docker Hub or GHCR.
7. **Write the manifests:**
   - Namespace, ConfigMap, Secret
   - Postgres, Mongo, Kafka as StatefulSets with PVCs (for Kafka, the Bitnami Helm chart or Strimzi saves a lot of work)
   - Deployment + Service per microservice, readiness/liveness probes on `/actuator/health`, resource requests/limits
   - Ingress for the gateway
8. **Deploy in order:** data layer → config-server → discovery → services → gateway.
   Check with `kubectl get pods`, `kubectl logs`, `kubectl port-forward`.
9. **Then CI/CD** (GitHub Actions): build + test → build images → push → `kubectl apply`.

**CI/CD before or after?** After. For a first deployment, deploy by hand to understand each piece;
automating something you haven't done manually is very hard to debug. A small CI (`mvn test` on every
push) can be added at any time.

## 5. Availability, scalability, fault tolerance — which two?

Focus on **availability** and **fault tolerance**.

- **Availability:**
  - 2 replicas for stateless services (gateway, auth, fleet, shipment)
  - readiness probes, so traffic only goes to ready pods
  - rolling updates, so no downtime on deploy
- **Fault tolerance:**
  - Kubernetes restarts crashed pods
  - PersistentVolumes keep the data
  - already in our code: Resilience4j circuit breaker, Kafka publish after commit that never breaks
    the API, idempotent consumer
- **Why not scalability for now:** no real load yet, and the architecture limits it. With 1 Kafka
  partition, a 2nd notification-service pod would sit idle (one partition is read by only one consumer
  of the group). Scaling later = create the topic with 3 partitions, then add pods. Kafka itself would
  need 3 brokers with replication factor 3 to be fault-tolerant. For a first deployment 1 broker is
  acceptable, as long as we know it is a single point of failure.
