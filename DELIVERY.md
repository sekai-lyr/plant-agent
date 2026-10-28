# Local source delivery

Sensitive values are read from environment variables or from the root-level `application-local.properties`, which must stay local.

Include only source files, Maven files, project documents, `.gitignore`, and this file. Exclude `.git/`, `target/`, `uploads/`, `tmp/`, `application-local.properties`, `login_state.json`, SQLite databases and IDE settings.

Before sharing, rotate every previously exposed credential: DashScope/OpenAI-compatible API, weather, Amap, Xunfei, Baidu Search, Web Push VAPID private key, and MySQL password. Credential rotation must be done by the account owner in each provider console.
