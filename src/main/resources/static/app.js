const text = {
  nonJson: "非 JSON 响应",
  requestFailed: "请求失败",
  unknown: "未知",
  searchLoading: "正在搜索，请稍候...",
  noResults: "没有找到匹配的歌曲，请换个关键词再试。",
  unknownTrack: "未知歌曲",
  noTrackMeta: "暂无歌手或专辑信息",
  artist: "歌手",
  album: "专辑",
  selectValidTrack: "请先选择一首有效歌曲。",
  noTrackSelected: "尚未选择歌曲。",
  missingIdPrefix: "缺少",
  missingIdSuffix: "所需的 ID。",
  missingIdRawPrefix: "当前搜索结果没有返回",
  loading: "正在加载...",
  downloadFetching: "正在获取下载链接...",
  downloadReady: "下载链接已准备好。",
  downloadUnavailable: "网关没有返回可下载地址。",
  noLyric: "未返回歌词",
  platform: "平台",
  lyric: "歌词",
  title: "标题",
  quality: "音质",
  downloadUrl: "下载地址",
  noDownloadUrl: "未返回下载地址",
  fileFormat: "文件格式",
  filename: "文件名",
  summary: "摘要",
  cover: "封面",
  hint: "提示",
  noStructuredFields: "没有可展示的结构化字段。",
  loadPlatformsFailed: "平台列表加载失败",
  songId: "歌曲 ID",
  artistId: "歌手 ID",
  albumId: "专辑 ID",
  playlistId: "歌单 ID",
  unselected: "尚未选择歌曲",
  chooseTrack: "请选择一首歌曲",
  detailEmpty: "选择搜索结果后自动展示。",
  rawEmpty: "选择搜索结果后自动加载。",
  searchReadyDetail: "搜索结果已在左侧加载，选择一首歌曲查看详情。",
  rawSearchSummary: "搜索响应已收起",
  rawDetailSummary: "当前详情响应",
  rawErrorSummary: "请求失败响应",
  rawEmptySummary: "暂无调试响应",
  ready: "就绪。"
};

const state = {
  selected: null,
  tab: "song",
  lastResult: null,
  platforms: []
};

const $ = (id) => document.getElementById(id);
const qsa = (selector, root = document) => Array.from(root.querySelectorAll(selector));

async function api(path) {
  const response = await fetch(path, { headers: { Accept: "application/json" } });
  const json = await response.json().catch(() => ({
    success: false,
    message: `${text.nonJson}: ${response.status}`
  }));
  if (!response.ok || !json.success) {
    const error = new Error(json.message || `${text.requestFailed}: ${response.status}`);
    error.payload = json.data || json;
    throw error;
  }
  return json.data;
}

function pretty(value) {
  return JSON.stringify(value, null, 2);
}

function setStatus(message, tone = "normal") {
  const status = $("status");
  const statusText = $("statusText");
  if (!status) {
    return;
  }
  if (statusText) {
    statusText.textContent = message;
  } else {
    status.textContent = message;
  }
  status.className = `status ${tone}`;
}

function setBackendState(message, tone = "pending") {
  const badge = $("backendState");
  if (!badge) {
    return;
  }
  badge.textContent = message;
  badge.dataset.tone = tone;
}

function setRaw(value, isError = false) {
  $("content").classList.toggle("raw-error", isError);
  $("content").textContent = typeof value === "string" ? value : pretty(value);
  $("rawDisclosure").open = isError;
}

function setRawSummary(message) {
  $("rawSummary").textContent = message;
}

function setRawCollapsed(value, summary) {
  $("rawDisclosure").open = false;
  $("content").classList.remove("raw-error");
  $("content").textContent = typeof value === "string" ? value : pretty(value);
  setRawSummary(summary);
}

function showError(error) {
  setStatus(error.message, "error");
  setRaw(error.payload || error.message, true);
  setRawSummary(text.rawErrorSummary);
  setBackendState("API 异常", "error");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function normalizeText(value) {
  return value === undefined || value === null || value === "" ? text.unknown : value;
}

async function loadPlatforms() {
  setBackendState("API 连接中", "pending");
  const platforms = await api("/api/platforms");
  state.platforms = Array.isArray(platforms) ? platforms : [];
  $("platform").innerHTML = state.platforms.map((platform) =>
    `<option value="${escapeHtml(platform.id)}">${escapeHtml(platform.name)} (${escapeHtml(platform.id)})</option>`
  ).join("");
  renderPlatformChoices(state.platforms);
  setBackendState("API 已连接", "ok");
}

function renderPlatformChoices(platforms) {
  if (!platforms.length) {
    $("platformChoices").innerHTML = `<span class="platform-empty">${text.loadPlatformsFailed}</span>`;
    return;
  }
  $("platformChoices").innerHTML = platforms.map((platform, index) => `
    <button type="button" class="platform-choice${index === 0 ? " active" : ""}" data-platform="${escapeHtml(platform.id)}" data-cursor="platform">
      <span>${escapeHtml(platform.name)}</span>
      <small>${escapeHtml(platform.id)}</small>
    </button>
  `).join("");
  qsa(".platform-choice").forEach((button) => {
    button.addEventListener("click", () => setPlatform(button.dataset.platform));
  });
  setPlatform($("platform").value || platforms[0].id);
}

function setPlatform(platform) {
  $("platform").value = platform;
  qsa(".platform-choice").forEach((button) => {
    button.classList.toggle("active", button.dataset.platform === platform);
  });
}

async function search(event) {
  event.preventDefault();
  const platform = $("platform").value;
  const keyword = $("keyword").value.trim();
  if (!keyword) {
    return;
  }
  state.selected = null;
  resetSelection();
  $("resultCount").textContent = "0";
  $("resultList").className = "result-list empty is-loading";
  $("resultList").innerHTML = `<div class="empty-state"><span class="empty-icon is-pulse" aria-hidden="true"></span><p>${text.searchLoading}</p></div>`;
  setStatus(`正在 ${platformLabel(platform)} 搜索“${keyword}”...`);
  setBackendState("搜索中", "pending");

  try {
    const result = await api(`/api/search?platform=${encodeURIComponent(platform)}&keyword=${encodeURIComponent(keyword)}&limit=30`);
    state.lastResult = result;
    const tracks = Array.isArray(result.tracks) ? result.tracks : [];
    renderResults(tracks);
    setStatus(`已加载 ${tracks.length} 条搜索结果。`);
    setBackendState("API 已连接", "ok");
    $("detailView").innerHTML = tracks.length ? `<div class="empty-detail"><p>${text.searchReadyDetail}</p></div>` : `<div class="empty-detail"><p>${text.detailEmpty}</p></div>`;
    setRawCollapsed(result, `${text.rawSearchSummary}: ${tracks.length} 条结果`);
  } catch (error) {
    $("resultList").className = "result-list empty";
    $("resultList").innerHTML = `<div class="empty-state"><span class="empty-icon" aria-hidden="true"></span><p>${escapeHtml(error.message)}</p></div>`;
    showError(error);
  }
}

function renderResults(tracks) {
  $("resultCount").textContent = String(tracks.length);
  if (!tracks.length) {
    $("resultList").className = "result-list empty";
    $("resultList").innerHTML = `<div class="empty-state"><span class="empty-icon" aria-hidden="true"></span><p>${text.noResults}</p></div>`;
    return;
  }
  $("resultList").className = "result-list";
  $("resultList").innerHTML = tracks.map((track, index) => {
    const title = track.title || track.id || text.unknownTrack;
    const meta = [track.artist, track.album, track.duration].filter(Boolean).join(" / ") || text.noTrackMeta;
    return `
      <button class="track" type="button" data-index="${index}" data-cursor="play" title="${escapeHtml(title)}">
        <span class="track-number">${String(index + 1).padStart(2, "0")}</span>
        <span class="thumb-wrap">
          <img class="thumb" src="${escapeHtml(track.cover || "")}" alt="">
          <span class="thumb-fallback" aria-hidden="true"></span>
        </span>
        <span class="track-copy">
          <span class="track-title">${escapeHtml(title)}</span>
          <span class="track-meta">${escapeHtml(meta)}</span>
        </span>
        <span class="track-arrow" aria-hidden="true">↗</span>
      </button>
    `;
  }).join("");
  qsa(".track").forEach((button, index) => {
    button.addEventListener("click", () => selectTrack(tracks[Number(button.dataset.index)], button));
    button.style.setProperty("--delay", `${Math.min(index * 34, 520)}ms`);
  });
}

async function selectTrack(track, button) {
  state.selected = track;
  qsa(".track").forEach((item) => item.classList.remove("active"));
  button.classList.add("active");
  $("cover").src = track.cover || "";
  updateCoverVisibility();
  $("selectedPlatform").textContent = `${platformLabel(track.platform)} · ${track.platform || $("platform").value}`;
  $("title").textContent = track.title || track.id || text.unknownTrack;
  $("artist").textContent = track.artist ? `${text.artist}: ${track.artist}` : "";
  $("album").textContent = track.album ? `${text.album}: ${track.album}` : "";
  renderLinkedIds(track);
  $("downloadInfoBtn").disabled = !track.id;
  resetDownload();
  setStatus(`已选择：${track.title || track.id}。`);
  await loadTab("song");
}

async function loadTab(tab) {
  state.tab = tab;
  qsa(".tab").forEach((button) => {
    button.classList.toggle("active", button.dataset.tab === tab);
  });
  if (!state.selected || !state.selected.id) {
    setRaw(text.selectValidTrack);
    setStatus(text.noTrackSelected, "warn");
    return;
  }
  const platform = state.selected.platform || $("platform").value;
  const idValue = idForTab(tab);
  if (!idValue) {
    setStatus(`${text.missingIdPrefix}${tabLabel(tab)}${text.missingIdSuffix}`, "warn");
    setRaw(`${text.missingIdRawPrefix}${tabLabel(tab)}${text.missingIdSuffix}`);
    return;
  }
  const id = encodeURIComponent(idValue);
  const routes = {
    song: `/api/song/${encodeURIComponent(platform)}/${id}`,
    lyric: `/api/song/${encodeURIComponent(platform)}/${id}/lyric`,
    artist: `/api/artist/${encodeURIComponent(platform)}/${id}`,
    album: `/api/album/${encodeURIComponent(platform)}/${id}`,
    playlist: `/api/playlist/${encodeURIComponent(platform)}/${id}`
  };
  setStatus(`正在加载${tabLabel(tab)}...`);
  setRaw(text.loading);
  try {
    const result = await api(routes[tab]);
    renderDetail(tab, result);
    renderRaw(result);
    setStatus(`${tabLabel(tab)}已加载。`);
    setBackendState("API 已连接", "ok");
  } catch (error) {
    showError(error);
  }
}

async function loadDownloadInfo() {
  if (!state.selected || !state.selected.id) {
    return;
  }
  const platform = state.selected.platform || $("platform").value;
  const id = encodeURIComponent(state.selected.id);
  const quality = $("quality").value;
  const query = downloadQuery(quality, state.selected);
  resetDownload();
  setStatus(`正在获取 ${qualityLabel(quality)} 下载链接...`);
  setRaw(text.downloadFetching);
  try {
    const info = await api(`/api/song/${encodeURIComponent(platform)}/${id}/download-info?${query}`);
    renderDetail("download", info);
    renderRaw(info);
    if (info.url) {
      $("downloadBtn").href = `/api/song/${encodeURIComponent(platform)}/${id}/download-package?${query}`;
      $("downloadBtn").classList.remove("disabled");
      $("downloadBtn").hidden = false;
      setStatus(text.downloadReady);
      setBackendState("链接已就绪", "ok");
    } else {
      setStatus(text.downloadUnavailable, "warn");
      setBackendState("无下载地址", "warn");
    }
  } catch (error) {
    showError(error);
  }
}

function renderDetail(tab, data) {
  if (tab === "lyric") {
    $("detailView").innerHTML = detailMarkup([
      [text.lyric, [data.lyric, data.translated].filter(Boolean).join("\n\n") || text.noLyric],
      [text.platform, platformLabel(data.platform) || data.platform],
      ["ID", data.id]
    ]);
    return;
  }

  if (tab === "download") {
    $("detailView").innerHTML = detailMarkup([
      [text.title, normalizeText(data.title)],
      [text.platform, platformLabel(data.platform) || data.platform],
      [text.quality, qualityLabel(data.quality)],
      [text.fileFormat, data.format || qualityLabel(data.quality)],
      [text.downloadUrl, data.url || text.noDownloadUrl],
      [text.filename, data.filename]
    ]);
    return;
  }

  const rows = [];
  if (data.title || data.id) {
    rows.push([text.title, normalizeText(data.title || data.id)]);
  }
  if (data.subtitle) {
    rows.push([text.summary, data.subtitle]);
  }
  if (data.cover) {
    rows.push([text.cover, data.cover]);
  }
  if (data.fields) {
    Object.entries(data.fields).forEach(([key, value]) => rows.push([key, value]));
  }
  $("detailView").innerHTML = detailMarkup(rows.length ? rows : [[text.hint, text.noStructuredFields]]);
}

function renderRaw(value) {
  setRaw(value?.raw || value);
  setRawSummary(text.rawDetailSummary);
}

function detailMarkup(rows) {
  return `
    <div class="detail-grid">
      ${rows.map(([key, value]) => `
        <div class="detail-row">
          <div class="detail-key">${escapeHtml(key)}</div>
          <div class="detail-value">${escapeHtml(formatDetailValue(value))}</div>
        </div>
      `).join("")}
    </div>
  `;
}

function formatDetailValue(value) {
  if (Array.isArray(value)) {
    return value.join("\n");
  }
  return value == null ? "" : String(value);
}

function renderLinkedIds(track) {
  const chips = [];
  if (track.id) {
    chips.push(chip(text.songId, track.id));
  }
  if (track.artistId) {
    chips.push(chip(text.artistId, track.artistId));
  }
  if (track.albumId) {
    chips.push(chip(text.albumId, track.albumId));
  }
  if (track.playlistId) {
    chips.push(chip(text.playlistId, track.playlistId));
  }
  $("linkedIds").innerHTML = chips.join("");
}

function chip(label, value) {
  return `<span class="id-chip">${escapeHtml(label)}: ${escapeHtml(value)}</span>`;
}

function resetSelection() {
  $("cover").removeAttribute("src");
  updateCoverVisibility();
  $("selectedPlatform").textContent = text.unselected;
  $("title").textContent = text.chooseTrack;
  $("artist").textContent = "";
  $("album").textContent = "";
  $("linkedIds").innerHTML = "";
  $("downloadInfoBtn").disabled = true;
  resetDownload();
  $("detailView").innerHTML = `<div class="empty-detail"><p>${text.detailEmpty}</p></div>`;
  setRawCollapsed(text.rawEmpty, text.rawEmptySummary);
}

function updateCoverVisibility() {
  const hasCover = Boolean($("cover").getAttribute("src"));
  $("cover").classList.toggle("has-cover", hasCover);
}

function resetDownload() {
  $("downloadBtn").classList.add("disabled");
  $("downloadBtn").href = "#";
  $("downloadBtn").hidden = true;
}

function downloadQuery(quality, track) {
  const params = new URLSearchParams({ quality });
  addParam(params, "title", track?.title);
  addParam(params, "artist", track?.artist);
  addParam(params, "album", track?.album);
  return params.toString();
}

function addParam(params, key, value) {
  if (value !== undefined && value !== null && value !== "") {
    params.set(key, value);
  }
}

function idForTab(tab) {
  const raw = state.selected?.raw || {};
  if (tab === "song" || tab === "lyric") {
    return state.selected.id;
  }
  if (tab === "artist") {
    return state.selected.artistId || pick(raw, ["artistId", "artist_id", "singerId", "singerid", "authorId", "author_id", "arid"]) ||
      pickNested(raw, ["artist", "artists", "singer", "author", "creator"], ["id", "mid"]);
  }
  if (tab === "album") {
    return state.selected.albumId || pick(raw, ["albumId", "album_id", "albummid", "albumMid", "albumid"]) ||
      pickNested(raw, ["album", "al"], ["id", "mid"]);
  }
  if (tab === "playlist") {
    return state.selected.playlistId || pick(raw, ["playlistId", "playlist_id", "sheetId", "sheetid", "songListId", "listId"]);
  }
  return state.selected.id;
}

function pick(object, keys) {
  for (const key of keys) {
    if (object && object[key] !== undefined && object[key] !== null && object[key] !== "") {
      return object[key];
    }
  }
  return null;
}

function pickNested(object, parents, keys) {
  for (const parent of parents) {
    const value = object?.[parent];
    if (Array.isArray(value)) {
      const found = value.map((item) => pick(item, keys)).find(Boolean);
      if (found) {
        return found;
      }
    }
    const nested = pick(value, keys);
    if (nested) {
      return nested;
    }
  }
  return null;
}

function platformLabel(id) {
  return {
    netease: "网易云音乐",
    qq: "QQ 音乐",
    qqmusic: "QQ 音乐",
    tencent: "QQ 音乐"
  }[id] || id || "未知平台";
}

function tabLabel(tab) {
  return {
    song: "歌曲详情",
    lyric: "歌词",
    artist: "歌手详情",
    album: "专辑详情",
    playlist: "歌单详情",
    download: "下载信息"
  }[tab] || tab;
}

function qualityLabel(value) {
  return {
    MP3_128: "标准 128k",
    MP3_320: "高品质 320k",
    FLAC: "无损 FLAC",
    MASTER: "最佳可用"
  }[String(value)] || normalizeText(value);
}

function bindEvents() {
  $("searchForm").addEventListener("submit", search);
  $("downloadInfoBtn").addEventListener("click", loadDownloadInfo);
  $("quality").addEventListener("change", resetDownload);
  $("platform").addEventListener("change", () => setPlatform($("platform").value));
  qsa(".tab").forEach((button) => {
    button.addEventListener("click", () => loadTab(button.dataset.tab));
  });
}

function init() {
  bindEvents();
  updateCoverVisibility();
  setRawCollapsed(text.rawEmpty, text.rawEmptySummary);
  loadPlatforms()
    .then(() => {
      setStatus(text.ready);
    })
    .catch((error) => {
      $("platformChoices").textContent = text.loadPlatformsFailed;
      setBackendState("API 未连接", "error");
      showError(error);
    });
}

window.addEventListener("DOMContentLoaded", init);
