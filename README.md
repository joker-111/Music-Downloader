# 音乐下载器

一个基于 Java Spring Boot + 独立 Vite 前端的音乐下载器，面向 Karpov Gateway 做统一适配。

## 功能

- 平台切换：网易云音乐、QQ 音乐
- 歌曲搜索
- 歌曲详情联动
- 歌手详情
- 专辑详情
- 歌单详情
- 歌词获取
- 下载链接获取
- 高品质下载代理

## 项目结构

```text
.
├── src/main/java                 # Spring Boot 后端
├── src/main/resources            # 后端配置
├── src/main/resources/static     # 旧版静态页面兜底，前端开发不再使用这里
├── frontend                      # 独立 Vite 前端
│   ├── index.html
│   ├── package.json
│   ├── vite.config.js
│   └── src
│       ├── main.js
│       └── styles.css
└── pom.xml
```

## 开发启动

先启动后端：

```powershell
.\run.cmd
```

后端默认运行在 `http://localhost:8080`，并提供 `/api/**` 接口。

再启动前端：

```powershell
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`。Vite 会把 `/api/**` 请求代理到 `http://localhost:8080`。

## 构建

后端构建：

```powershell
.\build.cmd
```

前端构建：

```powershell
cd frontend
npm install
npm run build
```

前端构建产物会生成到 `frontend/dist`，可以由任意静态服务器或反向代理托管；生产环境需要把 `/api/**` 转发到 Spring Boot 后端。

## 网关说明

配置 `src/main/resources/application.yml` 中的网关地址和候选路径。

当前默认通过 Karpov Console 的同源代理入口访问上游音乐 API：

- `music.gateway.base-url`: `https://gateway.karpov.cn/api/proxy`

代理入口会把下面这些路径转发到 Karpov Gateway 上游 `music.proto` 中定义的 REST 路由：

- `/v1/{provider}/search/songs`
- `/v1/{provider}/songs/{id}`
- `/v1/{provider}/songs/{id}/url`
- `/v1/{provider}/songs/{id}/lyric`
- `/v1/{provider}/albums/{id}`
- `/v1/{provider}/artists/{id}`
- `/v1/{provider}/playlists/{id}`

注意：`https://gateway.karpov.cn` 当前直接访问 `/v1/**` 返回的是 Karpov Console HTML 页面，不是公开音乐 API JSON；本项目因此默认使用 `/api/proxy/**`。

如果网关需要 API Key，请在本机环境变量或项目根目录 `.env` 中配置，不要写入 Git：

```powershell
setx MUSIC_GATEWAY_API_KEY "你的 API Key"
```

重新打开终端后再运行 `.\run.cmd`。如果搜索返回 `50200` 且提示 `all credentials exhausted` / `no candidate credential available`，说明请求已经到达远端网关，但 Karpov Gateway 的音乐平台凭据池没有可用的 QQ 音乐或网易云音乐账号凭据，需要在 Karpov Console 后台补充或刷新平台凭据。

## 本地 API

- `GET /api/platforms`
- `GET /api/search?platform=qqmusic&keyword=周杰伦`
- `GET /api/song/{platform}/{id}`
- `GET /api/song/{platform}/{id}/lyric`
- `GET /api/song/{platform}/{id}/download-info?quality=MP3_320`
- `GET /api/song/{platform}/{id}/download?quality=MP3_320`
- `GET /api/artist/{platform}/{id}`
- `GET /api/album/{platform}/{id}`
- `GET /api/playlist/{platform}/{id}`

## 说明

高品质下载是否可用，取决于上游网关与对应平台的可访问资源。
