const text = {
  nonJson: "\u975e JSON \u54cd\u5e94",
  requestFailed: "\u8bf7\u6c42\u5931\u8d25",
  unknown: "\u672a\u77e5",
  searchLoading: "\u6b63\u5728\u641c\u7d22\uff0c\u8bf7\u7a0d\u5019...",
  noResults: "\u6ca1\u6709\u627e\u5230\u5339\u914d\u7684\u6b4c\u66f2\uff0c\u8bf7\u6362\u4e2a\u5173\u952e\u8bcd\u518d\u8bd5\u3002",
  unknownTrack: "\u672a\u77e5\u6b4c\u66f2",
  noTrackMeta: "\u6682\u65e0\u6b4c\u624b\u6216\u4e13\u8f91\u4fe1\u606f",
  artist: "\u6b4c\u624b",
  album: "\u4e13\u8f91",
  selectValidTrack: "\u8bf7\u5148\u9009\u62e9\u4e00\u9996\u6709\u6548\u6b4c\u66f2\u3002",
  noTrackSelected: "\u5c1a\u672a\u9009\u62e9\u6b4c\u66f2\u3002",
  missingIdPrefix: "\u7f3a\u5c11",
  missingIdSuffix: "\u6240\u9700\u7684 ID\u3002",
  missingIdRawPrefix: "\u5f53\u524d\u641c\u7d22\u7ed3\u679c\u6ca1\u6709\u8fd4\u56de",
  loading: "\u6b63\u5728\u52a0\u8f7d...",
  downloadFetching: "\u6b63\u5728\u83b7\u53d6\u4e0b\u8f7d\u94fe\u63a5...",
  downloadReady: "\u4e0b\u8f7d\u94fe\u63a5\u5df2\u51c6\u5907\u597d\u3002",
  downloadUnavailable: "\u7f51\u5173\u6ca1\u6709\u8fd4\u56de\u53ef\u4e0b\u8f7d\u5730\u5740\u3002",
  noLyric: "\u672a\u8fd4\u56de\u6b4c\u8bcd",
  platform: "\u5e73\u53f0",
  lyric: "\u6b4c\u8bcd",
  title: "\u6807\u9898",
  quality: "\u97f3\u8d28",
  downloadUrl: "\u4e0b\u8f7d\u5730\u5740",
  noDownloadUrl: "\u672a\u8fd4\u56de\u4e0b\u8f7d\u5730\u5740",
  filename: "\u6587\u4ef6\u540d",
  summary: "\u6458\u8981",
  cover: "\u5c01\u9762",
  hint: "\u63d0\u793a",
  noStructuredFields: "\u6ca1\u6709\u53ef\u5c55\u793a\u7684\u7ed3\u6784\u5316\u5b57\u6bb5\u3002",
  songId: "\u6b4c\u66f2 ID",
  artistId: "\u6b4c\u624b ID",
  albumId: "\u4e13\u8f91 ID",
  playlistId: "\u6b4c\u5355 ID",
  unselected: "\u5c1a\u672a\u9009\u62e9\u6b4c\u66f2",
  chooseTrack: "\u8bf7\u9009\u62e9\u4e00\u9996\u6b4c\u66f2",
  detailEmpty: "\u9009\u62e9\u641c\u7d22\u7ed3\u679c\u540e\u81ea\u52a8\u5c55\u793a\u3002",
  rawEmpty: "\u9009\u62e9\u641c\u7d22\u7ed3\u679c\u540e\u81ea\u52a8\u52a0\u8f7d\u3002",
  ready: "\u5c31\u7eea\u3002"
};

const state = {
  selected: null,
  tab: "song",
  lastResult: null
};

const $ = (id) => document.getElementById(id);

async function api(path) {
  const response = await fetch(path);
  const json = await response.json().catch(() => ({
    success: false,
    message: `${text.nonJson}: ${response.status}`
  }));
  if (!response.ok || !json.success) {
    const error = new Error(json.message || `${text.requestFailed}: ${response.status}`);
    error.payload = json.data;
    throw error;
  }
  return json.data;
}

function pretty(value) {
  return JSON.stringify(value, null, 2);
}

function setStatus(message, tone = "normal") {
  $("status").textContent = message;
  $("status").className = `status ${tone}`;
}

function setRaw(value, isError = false) {
  $("content").classList.toggle("raw-error", isError);
  $("content").textContent = typeof value === "string" ? value : pretty(value);
}

function showError(error) {
  setStatus(error.message, "error");
  setRaw(error.payload || error.message, true);
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
  const platforms = await api("/api/platforms");
  $("platform").innerHTML = platforms.map((platform) =>
    `<option value="${escapeHtml(platform.id)}">${escapeHtml(platform.name)} (${escapeHtml(platform.id)})</option>`
  ).join("");
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
  $("resultList").className = "result-list empty";
  $("resultList").textContent = text.searchLoading;
  setStatus(`\u6b63\u5728 ${platformLabel(platform)} \u641c\u7d22\u201c${keyword}\u201d...`);
  try {
    const result = await api(`/api/search?platform=${encodeURIComponent(platform)}&keyword=${encodeURIComponent(keyword)}&limit=30`);
    state.lastResult = result;
    renderResults(result.tracks || []);
    setStatus(`\u5df2\u52a0\u8f7d ${(result.tracks || []).length} \u6761\u641c\u7d22\u7ed3\u679c\u3002`);
    renderRaw(result);
  } catch (error) {
    $("resultList").className = "result-list empty";
    $("resultList").textContent = error.message;
    showError(error);
  }
}

function renderResults(tracks) {
  $("resultCount").textContent = String(tracks.length);
  if (!tracks.length) {
    $("resultList").className = "result-list empty";
    $("resultList").textContent = text.noResults;
    return;
  }
  $("resultList").className = "result-list";
  $("resultList").innerHTML = tracks.map((track, index) => `
    <button class="track" data-index="${index}" title="${escapeHtml(track.title || track.id || text.unknownTrack)}">
      <img class="thumb" src="${escapeHtml(track.cover || "")}" alt="">
      <span class="track-copy">
        <span class="track-title">${escapeHtml(track.title || track.id || text.unknownTrack)}</span>
        <span class="track-meta">${escapeHtml([track.artist, track.album, track.duration].filter(Boolean).join(" / ") || text.noTrackMeta)}</span>
      </span>
    </button>
  `).join("");
  document.querySelectorAll(".track").forEach((button) => {
    button.addEventListener("click", () => selectTrack(tracks[Number(button.dataset.index)], button));
  });
}

async function selectTrack(track, button) {
  state.selected = track;
  document.querySelectorAll(".track").forEach((item) => item.classList.remove("active"));
  button.classList.add("active");
  $("cover").src = track.cover || "";
  $("selectedPlatform").textContent = `${platformLabel(track.platform)} · ${track.platform || $("platform").value}`;
  $("title").textContent = track.title || track.id || text.unknownTrack;
  $("artist").textContent = track.artist ? `${text.artist}: ${track.artist}` : "";
  $("album").textContent = track.album ? `${text.album}: ${track.album}` : "";
  renderLinkedIds(track);
  $("downloadInfoBtn").disabled = !track.id;
  resetDownload();
  setStatus(`\u5df2\u9009\u62e9\uff1a${track.title || track.id}\u3002`);
  await loadTab("song");
}

async function loadTab(tab) {
  state.tab = tab;
  document.querySelectorAll(".tab").forEach((button) => {
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
    song: `/api/song/${platform}/${id}`,
    lyric: `/api/song/${platform}/${id}/lyric`,
    artist: `/api/artist/${platform}/${id}`,
    album: `/api/album/${platform}/${id}`,
    playlist: `/api/playlist/${platform}/${id}`
  };
  setStatus(`\u6b63\u5728\u52a0\u8f7d${tabLabel(tab)}...`);
  setRaw(text.loading);
  try {
    const result = await api(routes[tab]);
    renderDetail(tab, result);
    renderRaw(result);
    setStatus(`${tabLabel(tab)}\u5df2\u52a0\u8f7d\u3002`);
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
  resetDownload();
  setStatus(`\u6b63\u5728\u83b7\u53d6 ${qualityLabel(quality)} \u4e0b\u8f7d\u94fe\u63a5...`);
  setRaw(text.downloadFetching);
  try {
    const info = await api(`/api/song/${platform}/${id}/download-info?quality=${encodeURIComponent(quality)}`);
    renderDetail("download", info);
    renderRaw(info);
    if (info.url) {
      $("downloadBtn").href = `/api/song/${platform}/${id}/download?quality=${encodeURIComponent(quality)}`;
      $("downloadBtn").classList.remove("disabled");
      setStatus(text.downloadReady);
    } else {
      setStatus(text.downloadUnavailable, "warn");
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
  setRaw(value.raw || value);
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
  $("cover").src = "";
  $("selectedPlatform").textContent = text.unselected;
  $("title").textContent = text.chooseTrack;
  $("artist").textContent = "";
  $("album").textContent = "";
  $("linkedIds").innerHTML = "";
  $("downloadInfoBtn").disabled = true;
  resetDownload();
  $("detailView").textContent = text.detailEmpty;
  setRaw(text.rawEmpty);
}

function resetDownload() {
  $("downloadBtn").classList.add("disabled");
  $("downloadBtn").href = "#";
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
    netease: "\u7f51\u6613\u4e91\u97f3\u4e50",
    qq: "QQ\u97f3\u4e50",
    tencent: "QQ\u97f3\u4e50",
    kugou: "\u9177\u72d7\u97f3\u4e50",
    kuwo: "\u9177\u6211\u97f3\u4e50",
    migu: "\u54aa\u5495\u97f3\u4e50"
  }[id] || id || "\u672a\u77e5\u5e73\u53f0";
}

function tabLabel(tab) {
  return {
    song: "\u6b4c\u66f2\u8be6\u60c5",
    lyric: "\u6b4c\u8bcd",
    artist: "\u6b4c\u624b\u8be6\u60c5",
    album: "\u4e13\u8f91\u8be6\u60c5",
    playlist: "\u6b4c\u5355\u8be6\u60c5",
    download: "\u4e0b\u8f7d\u4fe1\u606f"
  }[tab] || tab;
}

function qualityLabel(value) {
  return {
    "128": "\u6807\u51c6 128k",
    "320": "\u9ad8\u54c1\u8d28 320k",
    "740": "\u65e0\u635f FLAC",
    "999": "\u6700\u4f73\u53ef\u7528"
  }[String(value)] || normalizeText(value);
}

$("searchForm").addEventListener("submit", search);
$("downloadInfoBtn").addEventListener("click", loadDownloadInfo);
$("quality").addEventListener("change", resetDownload);
document.querySelectorAll(".tab").forEach((button) => {
  button.addEventListener("click", () => loadTab(button.dataset.tab));
});

loadPlatforms()
  .then(() => setStatus(text.ready))
  .catch(showError);
