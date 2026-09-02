# ADR-0002: 镜像构建从 Buildpacks 切换为 Jib

## 状态

已采纳（2026-09-02）

## 背景

ADR-0001 约定镜像构建使用 Cloud Native Buildpacks（`spring-boot:build-image`）。
W1 实际执行时发现：Paketo 默认构建器需要从 github.com 下载 BellSoft Liberica JRE，
而本机 github.com 直连超时不可达；registry-1.docker.io 直连同样不可达，
但 Docker daemon 配置了 registry mirror，`docker pull` docker.io 镜像可正常工作。

曾尝试按 Paketo 官方做法将 `paketobuildpacks/amazon-corretto` 置于 `paketobuildpacks/java`
之前（corretto.aws 可达），但 tiny builder 的 java 复合构建包仍优先解析 BellSoft 依赖，无效。

## 决定

镜像构建改用 Jib（jib-maven-plugin 3.5.2）：

- 免 Dockerfile，分层可复现，与 Buildpacks 同样满足"不要手写 Dockerfile"的初衷；
- 基础镜像 `eclipse-temurin:25-jre` 先 `docker pull` 预拉取（走 daemon 的 registry mirror），
  pom 中以 `docker://eclipse-temurin:25-jre` 引用，让 Jib 直接从本地 daemon 读取，
  避免 Jib 直连 registry-1.docker.io 校验 manifest（该地址本机不可达）；
- 构建命令：`DOCKER_HOST=unix:///var/run/docker.sock ./mvnw compile jib:dockerBuild -DskipTests`
  （本机需显式 DOCKER_HOST，否则插件会拼接出畸形 socket URL）。

## 后果

- AGENTS.md 中"用 Buildpacks"的约定按本 ADR 修订为"用 Jib"；
- CI 暂不构建镜像（GitHub Actions 网络无此限制，后续如需推送镜像仓库再议）；
- 若未来网络环境变化（github.com 可达），可重新评估回到 Buildpacks。
