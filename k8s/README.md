# K8s 部署说明

## 一键部署（Minikube）

```bash
# 1. 构建镜像（本机需显式 DOCKER_HOST，见 ADR-0002）
DOCKER_HOST=unix:///var/run/docker.sock ./mvnw compile jib:dockerBuild -DskipTests

# 2. 加载镜像到 minikube 并应用清单（Qdrant 镜像也需提前 docker pull 后载入）
minikube image load ai-evolution:0.1.0-w1-SNAPSHOT
minikube image load qdrant/qdrant:v1.15.1
kubectl apply -f k8s/

# 3. 创建密钥（Secret 清单不进 git，密钥取自本地 .env）
set -a && source .env && set +a
kubectl create secret generic ai-evolution-secret \
  --from-literal=AI_API_KEY="$AI_API_KEY" \
  --from-literal=SILICONFLOW_API_KEY="$SILICONFLOW_API_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl rollout status deploy/qdrant
kubectl rollout status deploy/ai-evolution
```

## 配置分层原则

| 类型 | 载体 | 是否入库 | 示例 |
|---|---|---|---|
| 非敏感配置 | ConfigMap | ✅ 入库 | `SPRINGDOC_SWAGGER_UI_ENABLED=false` |
| 敏感凭据 | Secret（命令式创建） | ❌ 永不入库 | `AI_API_KEY` |

- ConfigMap 明文存储、同命名空间可读，绝不放密钥；
- Secret 至少 base64 编码、支持静态加密与独立 RBAC/审计；
- 生产进阶路径：External Secrets Operator / Vault 注入，本项目从简。
