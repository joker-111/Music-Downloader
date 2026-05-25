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

远端网关地址、接口路径和 API Key 不写入代码。复制模板到本机 `.env` 后填写真实值：

```powershell
Copy-Item .env.example .env
```

需要配置的变量：

- `MUSIC_GATEWAY_BASE_URL`
- `MUSIC_GATEWAY_SEARCH_PATH`
- `MUSIC_GATEWAY_SONG_PATH`
- `MUSIC_GATEWAY_DOWNLOAD_PATH`
- `MUSIC_GATEWAY_LYRIC_PATH`
- `MUSIC_GATEWAY_ARTIST_PATH`
- `MUSIC_GATEWAY_PLAYLIST_PATH`
- `MUSIC_GATEWAY_ALBUM_PATH`
- `MUSIC_GATEWAY_API_KEY`

`.env` 已被 `.gitignore` 忽略，不会上传远端。后续换 API，只改 `.env` 或系统环境变量，不需要改 `application.yml` 或 Java 代码。

`.\run.cmd` 会自动读取项目根目录的 `.env`。如果没有配置 `MUSIC_GATEWAY_BASE_URL` 或对应接口路径，接口会返回明确的缺失配置提示。

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
