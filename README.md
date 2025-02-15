tessellation
===========

![build](https://img.shields.io/github/workflow/status/Constellation-Labs/tessellation/Create%20Release?label=build)
![version](https://img.shields.io/github/v/release/Constellation-Labs/tessellation?sort=semver)

## Running L0 & L1 in Kubernetes

### Prerequisites

1. [sbt](https://www.scala-sbt.org/)
2. [Docker Desktop](https://www.docker.com/get-started/) with [Kubernetes](https://docs.docker.com/desktop/kubernetes/) enabled
3. [Skaffold CLI](https://skaffold.dev/docs/install/#standalone-binary)

### Starting clusters

```
skaffold dev --trigger=manual
```

This will start both L0 and L1 clusters on kubernetes using current kube-context.

Initial validators for L0 and L1 have their public ports mapped to local ports 9000 and 9100 respectively.

```
curl localhost:9000/cluster/info
curl localhost:9100/cluster/info
```

This will return a list of validators on L0 and L1. By default, L0 cluster starts with 2 validators (1 initial and 1 
regular) and L1 with 3 validators (1 initial and 2 regular).

### Scaling a cluster

```
kubectl scale deployment/l0-validator-deployment --replicas=2
```

This scales the L0 cluster to 3 validators total: 1 initial and 2 regular.
