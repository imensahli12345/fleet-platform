# Learning Kubernetes with the fleet platform

_Written 2026-10-26. Goal: deploy this project on Kubernetes the way it is done in real companies,
learning each piece as we add it._

**My machine:** 12 GB RAM, 8 CPUs, Docker Desktop 4.93 (has a built-in Kubernetes).
**Installed tools:** `kubectl` (comes with Docker Desktop), `helm`, `k9s`, Headlamp.

---

## 0. The big picture: what happens in the background

A cluster = a **control plane** (the "brain") + **worker nodes** (the machines that run the containers).

```
                    YOU:  kubectl apply -f deployment.yaml
                                    │  (HTTPS request)
 ┌──────────────── CONTROL PLANE ───▼────────────────────────────────────┐
 │  kube-apiserver   the only door in. Checks auth, validates YAML,     │
 │        │          saves the "desired state".                         │
 │        ▼                                                             │
 │  etcd             the database of the cluster (key/value). Every     │
 │                   object (Deployment, Pod, Secret...) lives here.    │
 │                                                                      │
 │  controller-manager  loops forever: "desired state == real state?"   │
 │                   Deployment wants 2 pods, sees 0 → creates 2 Pods.  │
 │                                                                      │
 │  scheduler        sees Pods with no node → picks a node with enough  │
 │                   free CPU/RAM (uses your resource "requests").      │
 └──────────────────────────────┬───────────────────────────────────────┘
                                │ watches the API server
 ┌────────── WORKER NODE ───────▼───────────────────────────────────────┐
 │  kubelet          agent on each node: "a Pod is assigned to me" →    │
 │                   tells the container runtime to start it, runs the  │
 │                   liveness/readiness probes, reports status back.    │
 │  containerd       the container runtime (pulls images, runs them).   │
 │  kube-proxy       programs network rules so a Service IP reaches     │
 │                   one of its Pods.                                   │
 │  CNI plugin       gives each Pod its own IP.                         │
 │  CoreDNS (pod)    "fleet-service" → Service IP.                      │
 └──────────────────────────────────────────────────────────────────────┘
```

**The core idea: everything is a reconciliation loop.** You never say "start a container". You
write *what you want* (desired state) into etcd through the API server, and controllers keep
working until reality matches. If a pod dies, reality ≠ desired → a new pod is created.
This is why Kubernetes "heals itself".

**Exercise to *see* it:**
```
kubectl get events -w                 # in one terminal: watch what happens
kubectl apply -f k8s/...              # in another
kubectl delete pod <one-pod>          # watch the controller recreate it
kubectl get pods -n kube-system       # the control-plane components, running as pods
```

---

## 1. The objects we will use (and why)

| Object | What it is | Used for in our project |
|---|---|---|
| **Namespace** | a folder for objects | `fleet` |
| **Pod** | 1+ containers sharing an IP | one running service |
| **Deployment** | keeps N identical pods alive, does rolling updates | every Spring service, ai-service |
| **StatefulSet** | like Deployment but stable names + own disk per pod | Postgres, MongoDB, Kafka |
| **PersistentVolumeClaim** | "I need 5 GB of disk" | database data |
| **Service (ClusterIP)** | stable DNS name + IP in front of pods | `postgres:5432`, `kafka:9092`, `fleet-service:8082` |
| **Service (LoadBalancer)** | asks the infrastructure for an external IP | only for the ingress controller |
| **Ingress / Gateway API** | HTTP routing rules (host/path → Service) | `fleet.local/` → api-gateway |
| **ConfigMap** | non-secret config | URLs, Spring profile |
| **Secret** | passwords, keys (base64, NOT encrypted by default) | DB password, JWT secret |
| **HorizontalPodAutoscaler** | adds/removes pods on CPU load | api-gateway, shipment |
| **PodDisruptionBudget** | "never take down more than 1 pod at a time" | services with 2+ replicas |
| **NetworkPolicy** | firewall between pods | only services can reach Postgres |

---

## 2. How many pods?

**Rule of thumb:**
- **1 replica** = no availability: if it restarts or you deploy, there's downtime.
- **2 replicas** = minimum for a stateless service in production (one can die or be updated).
- **3 replicas** = needed for anything that votes (Kafka KRaft controllers, MongoDB replica set, etcd)
  because it needs a *majority* (quorum): 2 of 3 still works, 1 of 2 does not.
- Kafka consumers: useful pods ≤ number of partitions (1 partition → only 1 consumer works).

| Component | Local (my 12 GB PC) | Production |
|---|---|---|
| api-gateway | 1 | 2-3 + HPA |
| auth-service | 1 | 2 |
| fleet-service | 1 | 2 |
| shipment-service | 1 → try 2 to learn | 2 + HPA |
| notification-service | 1 | ≤ partitions (create topic with 3) |
| ai-service | 1 | 2 |
| config-server | 1 | 2, or **removed** (see §6) |
| discovery-server (Eureka) | 1 | 2 peers, or **removed** (see §6) |
| PostgreSQL | 1 (StatefulSet) | 1 primary + 1-2 replicas (CloudNativePG operator) or managed DB |
| MongoDB | 1 | 3-member replica set or MongoDB Atlas |
| Kafka | 1 broker | 3 brokers, replication factor 3 (Strimzi operator) or managed Kafka |
| Ingress controller (Traefik) | 1 | 2 |

Locally we keep 1 of each because ~11 JVM/database containers + Kubernetes itself already need
~7-8 GB. Each Spring pod gets `requests: 256Mi / limits: 512Mi` and `-XX:MaxRAMPercentage=75`.

---

## 3. Traffic from outside: LoadBalancer + Ingress controller

```
 Browser  http://fleet.localhost
    │
    ▼
 [ LoadBalancer ]      cloud: AWS/GCP creates a real load balancer with a public IP
    │                  bare metal: MetalLB gives an IP from your network
    │                  Docker Desktop: published on localhost
    ▼
 [ Ingress controller pods ]  (Traefik) a reverse proxy running INSIDE the cluster.
    │                         Reads Ingress/HTTPRoute objects, terminates TLS.
    ▼
 [ Service api-gateway ] → [ api-gateway pods ] → Eureka/Services → other pods
```

- **Service type LoadBalancer** = layer 4 (TCP). One per cluster entry point, it costs money in the cloud.
- **Ingress controller** = layer 7 (HTTP): one LoadBalancer, many routes, TLS certificates
  (with **cert-manager** + Let's Encrypt in production).
- An `Ingress` object alone does nothing: it's just a rule. The **controller** is the program that applies it.

**Which controller in 2026?** `ingress-nginx` (the old default in every tutorial) was retired by the
Kubernetes project (maintenance ended March 2026), so don't build anything new on it. The direction
now is the **Gateway API** (`Gateway` + `HTTPRoute`, the successor of `Ingress`). We use **Traefik**:
it supports both the classic `Ingress` (to learn the concept) and the Gateway API (what new
production setups use).

---

## 4. Managing the cluster

- **kubectl**: the base. Learn it first: `get`, `describe`, `logs`, `exec`, `apply`, `rollout`.
- **k9s** (terminal): live view, `:pods`, `l` = logs, `d` = describe, `s` = shell. Fast for daily work.
- **Headlamp** (desktop app, official Kubernetes SIG UI project): graphical view of everything,
  good to *see* relations (Deployment → ReplicaSet → Pods). The old Kubernetes Dashboard is no
  longer maintained, and the Kubernetes project points to Headlamp.
- **metrics-server**: needed for `kubectl top pods` and for the autoscaler.
- **Later, real-world monitoring:** Prometheus + Grafana (kube-prometheus-stack Helm chart), logs with Loki.

---

## 5. Roadmap (each phase = something you can show and explain)

| # | Phase | You learn |
|---|---|---|
| 1 | Enable Kubernetes in Docker Desktop (kind, 1 control plane + 2 workers). Open it in Headlamp and k9s. | nodes, kube-system, kubectl contexts |
| 2 | Prepare the app: no `localhost` in config-repo, secrets out of files, run everything in Docker Compose. | 12-factor config |
| 3 | Data layer: Postgres, Mongo, Kafka as StatefulSets + PVCs. | StatefulSet, PVC, headless Service, DNS |
| 4 | config-server, Eureka, then the 5 services as Deployments + Services, with probes and resources. | Deployment, probes, requests/limits, rollout |
| 5 | Install Traefik with Helm, add the Ingress → api-gateway. Open the app from the browser. | Helm, LoadBalancer, Ingress, then Gateway API |
| 6 | Break things on purpose: kill pods, kill a node, deploy a broken image, run out of memory (OOMKilled). | self-healing, rolling update, rollback |
| 7 | Production hardening (see §6). | security, availability |
| 8 | Monitoring (Prometheus/Grafana), then CI/CD (GitHub Actions) and GitOps (Argo CD). | real-world operations |
| 9 | (Optional) deploy to a real cloud cluster for a few days. | cloud LoadBalancer, real DNS + TLS |

---

## 6. Will it be production-ready?

**What we can make production-grade (the manifests/Helm chart):**
- 2+ replicas, readiness/liveness/startup probes, rolling updates, PodDisruptionBudgets
- resource requests/limits, HorizontalPodAutoscaler
- Secrets not in Git (Sealed Secrets or External Secrets), containers running as non-root
- NetworkPolicies, TLS on the ingress, image tags = git SHA (never `latest`)
- monitoring + alerts, centralized logs, GitOps deployment

**What a laptop cluster can never be:** production. A real one needs:
- several real machines (a laptop dying = everything gone), usually a managed cluster (EKS, GKE, AKS,
  or cheaper: DigitalOcean, Scaleway, Hetzner)
- databases with backups and replicas (managed DB, or operators like CloudNativePG / Strimzi)
- a real domain + certificates

So the honest summary: **the same YAML/Helm chart could go to production; the cluster it runs on
locally cannot.** That's how companies work too: same chart, different values per environment
(`values-dev.yaml`, `values-prod.yaml`).

**Real-world design decisions in our app (good to discuss with the teacher):**
- **Eureka is redundant on Kubernetes**: Kubernetes Services already do service discovery + load
  balancing (`http://fleet-service:8082`). Many teams remove Eureka when moving to K8s.
- **Config Server vs ConfigMaps**: same thing, ConfigMaps/Secrets are the Kubernetes-native way.
- **Spring Cloud Gateway vs Ingress**: both route HTTP. Keeping the gateway is fine (it does JWT
  checks and circuit breaking); the Ingress then just exposes the gateway.
- **Kafka with 1 broker** is a single point of failure: production = 3 brokers.

Plan: phases 1-6 keep Eureka/Config Server (fewer changes, the app works as it does today).
In phase 7 we can remove them as a "migration to Kubernetes-native" exercise.
