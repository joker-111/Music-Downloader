# 音乐下载器

一个基于 Java Spring Boot 的音乐下载器，面向 `gateway.karpov.cn` 做统一适配。

## 功能

- 平台切换：网易云音乐、QQ音乐、酷狗音乐、酷我音乐、咪咕音乐
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
.\run.ps1
```

## 网关说明

当前实现采用“候选路径探测”模式。因为 `gateway.karpov.cn` 的公开接口文档未在本环境中可直接获取，所以后端会按 `application.yml` 中配置的路径顺序逐个尝试。

如果你的网关实际路径不同，只需要调整候选项，不必改 Java 代码。

## 本地 API

- `GET /api/platforms`
- `GET /api/search?platform=qq&keyword=周杰伦`
- `GET /api/song/{platform}/{id}`
- `GET /api/song/{platform}/{id}/lyric`
- `GET /api/song/{platform}/{id}/download-info?quality=320`
- `GET /api/song/{platform}/{id}/download?quality=320`
- `GET /api/artist/{platform}/{id}`
- `GET /api/album/{platform}/{id}`
- `GET /api/playlist/{platform}/{id}`

## 说明

高品质下载是否可用，取决于上游网关与对应平台的可访问资源。
