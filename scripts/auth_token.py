#!/usr/bin/env python3
"""W30 #2：脚本统一凭证来源——client_credentials 向认证中心换取 JWT。

主服务已是 JWT 资源服务器（只认 auth-center 签发的 token），共享 API Key 已退役；
脚本属机器通道，与 MCP 客户端同走客户端凭证流（mcp-client，scope=writer）。

环境变量：
  AUTH_CENTER_BASE_URL     认证中心地址（默认 http://localhost:19000）
  AUTH_CLIENT_ID           客户端 ID（默认 mcp-client）
  AUTH_CLIENT_MCP_SECRET   客户端密钥（必填，set -a; source .env; set +a）
"""

import base64
import json
import os
import urllib.request


def fetch_token() -> str:
    base = os.environ.get("AUTH_CENTER_BASE_URL", "http://localhost:19000")
    client_id = os.environ.get("AUTH_CLIENT_ID", "mcp-client")
    secret = os.environ.get("AUTH_CLIENT_MCP_SECRET", "")
    if not secret:
        raise SystemExit(
            "缺 AUTH_CLIENT_MCP_SECRET（先 set -a; source .env; set +a）"
            "——W30 #2 起脚本凭证 = client_credentials 向认证中心换 JWT"
        )
    basic = base64.b64encode(f"{client_id}:{secret}".encode()).decode()
    req = urllib.request.Request(
        f"{base}/oauth2/token",
        data=b"grant_type=client_credentials",
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "Authorization": f"Basic {basic}",
        },
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read())["access_token"]
