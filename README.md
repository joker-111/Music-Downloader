# 音乐下载器

一个基于 Java Spring Boot 的音乐下载器，面向 Karpov Gateway 做统一适配。

## 功能

- 平台切换：网易云音乐、QQ音乐
- 歌曲搜索
- 歌曲详情联动
- 歌手详情
- 专辑详情
- 歌单详情
- 歌词获取
- 下载链接获取
- 高品质下载代理

## 使用方式

1. 配置 `src/main/resources/application.yml` 中的网关地址和候选路径
2. 启动 Spring Boot
3. 打开 `http://localhost:8080`

推荐使用仓库内置脚本：

```powershell
.\run.cmd
```

## 网关说明

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

如果网关需要 API Key，请在本机环境变量中配置，不要写入 Git：

```powershell
setx MUSIC_GATEWAY_API_KEY "你的 API Key"
```

重新打开终端后再运行 `.\run.cmd`。

如果搜索返回 `50200` 且提示 `all credentials exhausted` / `no candidate credential available`，说明请求已经到达远端网关，但 Karpov Gateway 的音乐平台凭据池没有可用的 QQ 音乐或网易云音乐账号凭据，需要在 Karpov Console 后台补充或刷新平台凭据。

如果你的网关实际路径不同，只需要调整候选项，不必改 Java 代码。

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
