# Kubernetes Deployment Manifests

This directory contains Kubernetes manifests for deploying VeriCrop to a Kubernetes cluster.

## Quick Start

```bash
# 1. Create namespace
kubectl apply -f namespace.yaml

# 2. Create secrets (DO NOT use the example file as-is)
kubectl create secret generic vericrop-secrets \
  --from-literal=postgres-username='vericrop_prod' \
  --from-literal=postgres-password='<STRONG-PASSWORD>' \
  --from-literal=jwt-secret='<RANDOM-SECRET>' \
  --namespace=vericrop

# 3. Apply ConfigMap
kubectl apply -f configmap.yaml

# 4. Deploy stateful services (Postgres, Kafka, Zookeeper)
kubectl apply -f postgres-statefulset.yaml
kubectl apply -f kafka-statefulset.yaml

# 5. Wait for stateful services to be ready
kubectl wait --for=condition=ready pod -l app=postgres -n vericrop --timeout=300s
kubectl wait --for=condition=ready pod -l app=kafka -n vericrop --timeout=300s

# 6. Deploy application services
kubectl apply -f ml-service-deployment.yaml
kubectl apply -f vericrop-gui-deployment.yaml

# 7. Apply autoscaling
kubectl apply -f hpa.yaml

# 8. Expose services (optional, for external access)
kubectl apply -f ingress.yaml
```

## Manifest Files

| File | Description |
|------|-------------|
| `namespace.yaml` | Namespace definition |
| `configmap.yaml` | Application configuration |
| `secrets.yaml.example` | Secret templates (DO NOT use as-is) |
| `postgres-statefulset.yaml` | PostgreSQL database |
| `kafka-statefulset.yaml` | Kafka and Zookeeper |
| `vericrop-gui-deployment.yaml` | Main application backend |
| `ml-service-deployment.yaml` | ML prediction service |
| `hpa.yaml` | Horizontal Pod Autoscalers |
| `ingress.yaml` | Ingress rules for external access |

## Prerequisites

1. **Kubernetes Cluster**: Version 1.24+
2. **kubectl**: Configured to access your cluster
3. **Storage Class**: Ensure `standard` storage class exists or modify manifests
4. **Ingress Controller**: nginx-ingress-controller installed (for ingress.yaml)
5. **Cert Manager**: For automatic TLS certificate management (optional)

## Configuration

### Secrets

Create secrets before deploying:

```bash
# Generate strong passwords
openssl rand -base64 32

# Create secret
kubectl create secret generic vericrop-secrets \
  --from-literal=postgres-username='vericrop_prod' \
  --from-literal=postgres-password='<generated-password>' \
  --from-literal=jwt-secret='<generated-secret>' \
  --namespace=vericrop
```

### Storage

The manifests use `standard` storage class. Verify it exists:

```bash
kubectl get storageclass
```

If you need to use a different storage class, update all PVC definitions:

```bash
sed -i 's/storageClassName: standard/storageClassName: your-class/' *.yaml
```

### Resource Limits

Adjust resource requests/limits based on your cluster capacity:

**vericrop-gui:**
- Default: 2Gi memory, 1 CPU
- Recommended: 4Gi memory, 2 CPUs

**ml-service:**
- Default: 2Gi memory, 1 CPU
- Recommended: 4Gi memory, 2 CPUs (model requires ~2GB)

**postgres:**
- Default: 1Gi memory, 500m CPU
- Recommended: 2Gi memory, 1 CPU

### Autoscaling

HPA scales based on CPU and memory:

- **vericrop-gui**: 3-10 replicas, scales at 70% CPU
- **ml-service**: 2-8 replicas, scales at 75% CPU

Ensure metrics-server is installed:

```bash
kubectl top nodes
kubectl top pods -n vericrop
```

## Deployment Order

1. **Namespace and Secrets**: Foundation
2. **StatefulSets**: Postgres, Kafka, Zookeeper (with persistent volumes)
3. **Deployments**: ML service, vericrop-gui
4. **Services**: Expose applications
5. **Ingress**: External access
6. **HPA**: Autoscaling

## Monitoring

### Health Checks

```bash
# Check pod health
kubectl get pods -n vericrop

# Check pod logs
kubectl logs -f deployment/vericrop-gui -n vericrop
kubectl logs -f deployment/ml-service -n vericrop

# Check resource usage
kubectl top pods -n vericrop
```

### Application Health Endpoints

```bash
# Port-forward to test locally
kubectl port-forward svc/vericrop-gui-service 8080:8080 -n vericrop

# Test health
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

## Scaling

### Manual Scaling

```bash
# Scale deployment
kubectl scale deployment vericrop-gui --replicas=5 -n vericrop

# Scale StatefulSet
kubectl scale statefulset postgres --replicas=1 -n vericrop
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/vericrop-gui \
  vericrop-gui=your-registry.com/vericrop-gui:1.1.0 \
  -n vericrop

# Monitor rollout
kubectl rollout status deployment/vericrop-gui -n vericrop

# Rollback
kubectl rollout undo deployment/vericrop-gui -n vericrop
```

## Backup and Restore

### Database Backup

```bash
# Create backup job
kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
  namespace: vericrop
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:15
            command:
            - /bin/sh
            - -c
            - pg_dump -h postgres-service -U \$POSTGRES_USER \$POSTGRES_DB | gzip > /backup/dump_\$(date +%Y%m%d).sql.gz
            env:
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: vericrop-secrets
                  key: postgres-username
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: vericrop-secrets
                  key: postgres-password
            - name: POSTGRES_DB
              value: vericrop
            volumeMounts:
            - name: backup-storage
              mountPath: /backup
          volumes:
          - name: backup-storage
            persistentVolumeClaim:
              claimName: postgres-backup-pvc
          restartPolicy: OnFailure
EOF
```

## Troubleshooting

### Pods Not Starting

```bash
# Describe pod to see events
kubectl describe pod <pod-name> -n vericrop

# Check logs
kubectl logs <pod-name> -n vericrop

# Check previous logs (if crashed)
kubectl logs <pod-name> -n vericrop --previous
```

### Database Connection Issues

```bash
# Test database connectivity from pod
kubectl exec -it deployment/vericrop-gui -n vericrop -- sh
nc -zv postgres-service 5432

# Check database logs
kubectl logs statefulset/postgres -n vericrop
```

### Kafka Issues

```bash
# List topics
kubectl exec -it kafka-0 -n vericrop -- \
  kafka-topics --list --bootstrap-server localhost:9092

# Check consumer groups
kubectl exec -it kafka-0 -n vericrop -- \
  kafka-consumer-groups --bootstrap-server localhost:9092 --describe --all-groups
```

### Storage Issues

```bash
# Check PVCs
kubectl get pvc -n vericrop

# Check PVs
kubectl get pv

# Describe PVC
kubectl describe pvc <pvc-name> -n vericrop
```

## Security Considerations

1. **Secrets**: Use external secrets manager (AWS Secrets Manager, Vault, etc.)
2. **Network Policies**: Apply network policies to restrict pod-to-pod communication
3. **RBAC**: Use Role-Based Access Control for service accounts
4. **Pod Security**: Enable Pod Security Standards
5. **TLS**: Use cert-manager for automatic certificate management
6. **Image Scanning**: Scan images for vulnerabilities before deployment

## Production Checklist

- [ ] Secrets created and stored securely
- [ ] Storage class configured
- [ ] Resource limits adjusted for your cluster
- [ ] Ingress controller installed
- [ ] TLS certificates configured
- [ ] Monitoring and alerting set up
- [ ] Backup strategy implemented
- [ ] Disaster recovery plan documented
- [ ] Load tested for expected traffic
- [ ] Security scanning completed

## Additional Resources

- [../DEPLOYMENT.md](../DEPLOYMENT.md) - Complete deployment guide
- [../README.md](../README.md) - Project overview
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Helm Charts](https://helm.sh/) - For more complex deployments

For questions or issues, please refer to the main [README](../README.md) or open an issue on GitHub.
