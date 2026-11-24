package dev.jkcarino.extension.reddit.frontpage.frostfeed.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;

import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchiveItem;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchivedComment;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchivedPost;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.CrosspostInfo;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.FlairPart;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.GalleryItem;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SearchPage;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SortOrder;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SubredditInfo;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.UserStats;

/**
 * Standalone client for the public Arctic Shift API.
 *
 * Strictly isolated from Reddit's network stack: never sends cookies,
 * authentication headers, or requests to reddit.com.
 */
public class ArcticShiftClient {

    public static final String BASE_URL = "https://arctic-shift.photon-reddit.com/api";
    public static final long CACHE_TTL_MS = 5L * 60L * 1000L; // 5 minutes
    public static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CACHE_ENTRIES = 400;
    private static final int CACHE_EVICT_BATCH = 100;
    private static final int MAX_SUB_CACHE_ENTRIES = 2000;
    private static final int SUB_CACHE_EVICT_BATCH = 500;
    private static final int SUB_BATCH_SIZE = 50;
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-f]{3,8}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEAN_SUB_ID_PATTERN = Pattern.compile("^[a-z0-9]+$");

    private static volatile ArcticShiftClient instance;

    public static ArcticShiftClient getInstance() {
        if (instance == null) {
            synchronized (ArcticShiftClient.class) {
                if (instance == null) {
                    instance = new ArcticShiftClient(new DefaultHttpTransport());
                }
            }
        }
        return instance;
    }

    public interface HttpTransport {
        HttpResponse get(String url) throws IOException;
    }

    public static final class HttpResponse {
        public final int statusCode;
        public final String body;

        public HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private static final class CacheEntry<T> {
        final long storedAtMs;
        final T value;

        CacheEntry(long storedAtMs, T value) {
            this.storedAtMs = storedAtMs;
            this.value = value;
        }
    }

    private final HttpTransport transport;
    private final Object cacheLock = new Object();
    private final LinkedHashMap<String, CacheEntry<Object>> responseCache = new LinkedHashMap<>();
    private final ConcurrentHashMap<String, FutureTask<SearchPage>> inflightSearches = new ConcurrentHashMap<>();
    private final Object subCacheLock = new Object();
    private final LinkedHashMap<String, SubredditInfo> subCache = new LinkedHashMap<>();

    public ArcticShiftClient(HttpTransport transport) {
        this.transport = transport;
    }

    public static int clampLimit(int limit) {
        if (limit < 1) return MAX_PAGE_SIZE;
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    public static String buildSearchUrl(
            String kind,
            String author,
            SortOrder sort,
            Long before,
            Long after,
            int limit
    ) {
        StringBuilder sb = new StringBuilder(BASE_URL)
                .append('/').append("comments".equals(kind) ? "comments" : "posts")
                .append("/search?author=").append(urlEncode(author))
                .append("&limit=").append(clampLimit(limit))
                .append("&sort=").append(sort == SortOrder.OLD ? "asc" : "desc");
        if (before != null) {
            sb.append("&before=").append(before);
        }
        if (after != null) {
            sb.append("&after=").append(after);
        }
        return sb.toString();
    }

    public SearchPage search(
            String kind,
            String author,
            SortOrder sort,
            Long before,
            Long after,
            int limit
    ) throws IOException {
        final String normalizedKind = "comments".equals(kind) ? "comments" : "posts";
        final int effectiveLimit = clampLimit(limit);
        final String url = buildSearchUrl(normalizedKind, author, sort, before, after, effectiveLimit);

        SearchPage cached = getCached(url, SearchPage.class);
        if (cached != null) {
            return cached;
        }

        FutureTask<SearchPage> existing = inflightSearches.get(url);
        if (existing != null) {
            return awaitFuture(existing);
        }

        Callable<SearchPage> task = () -> {
            JSONObject json = requestJson(url);
            SearchPage page = parseSearchPage(normalizedKind, json, effectiveLimit);
            putCached(url, page);
            return page;
        };
        FutureTask<SearchPage> future = new FutureTask<>(task);
        FutureTask<SearchPage> prev = inflightSearches.putIfAbsent(url, future);
        if (prev != null) {
            return awaitFuture(prev);
        }

        try {
            future.run();
            return awaitFuture(future);
        } finally {
            inflightSearches.remove(url, future);
        }
    }

    public UserStats fetchUserStats(String author) throws IOException {
        String url = BASE_URL + "/users/search?author=" + urlEncode(author);
        UserStats cached = getCached(url, UserStats.class);
        if (cached != null) {
            return cached;
        }

        JSONObject json = requestJson(url);
        UserStats stats = parseUserStats(json);
        if (stats != null) {
            putCached(url, stats);
        }
        return stats;
    }

    public Map<String, SubredditInfo> fetchSubreddits(Collection<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, SubredditInfo> out = new HashMap<>();
        List<String> missing = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        synchronized (subCacheLock) {
            for (String raw : rawIds) {
                if (raw == null) continue;
                String clean = stripSubPrefix(raw);
                if (clean == null || !seen.add(clean)) continue;
                String key = "t5_" + clean;
                SubredditInfo hit = subCache.get(clean);
                if (hit != null) {
                    out.put(key, hit);
                } else {
                    missing.add(clean);
                }
            }
        }

        for (int i = 0; i < missing.size(); i += SUB_BATCH_SIZE) {
            int end = Math.min(i + SUB_BATCH_SIZE, missing.size());
            List<String> chunk = missing.subList(i, end);
            StringBuilder idsParam = new StringBuilder();
            for (int j = 0; j < chunk.size(); j++) {
                if (j > 0) idsParam.append(',');
                idsParam.append(chunk.get(j));
            }
            String url = BASE_URL + "/subreddits/ids?ids=" + idsParam;
            try {
                JSONObject json = requestJson(url);
                JSONArray rows = json.optJSONArray("data");
                Set<String> found = new HashSet<>();
                if (rows != null) {
                    for (int r = 0; r < rows.length(); r++) {
                        JSONObject row = rows.optJSONObject(r);
                        if (row == null) continue;
                        String id = stripSubPrefix(row.optString("id", ""));
                        if (id == null) continue;
                        found.add(id);
                        SubredditInfo entry = parseSubredditInfo(row);
                        putSubCache(id, entry);
                        out.put("t5_" + id, entry);
                    }
                }
                // Cache misses too so deleted/private subs are not re-requested.
                for (String id : chunk) {
                    if (!found.contains(id)) {
                        putSubCache(id, SubredditInfo.EMPTY);
                        out.put("t5_" + id, SubredditInfo.EMPTY);
                    }
                }
            } catch (IOException ignored) {
                // Icons are optional — never fail the feed.
            }
        }

        return out;
    }

    public SubredditInfo getCachedSubreddit(String rawId) {
        String clean = stripSubPrefix(rawId);
        if (clean == null) return null;
        synchronized (subCacheLock) {
            return subCache.get(clean);
        }
    }

    private void putSubCache(String cleanId, SubredditInfo entry) {
        synchronized (subCacheLock) {
            if (subCache.size() > MAX_SUB_CACHE_ENTRIES) {
                Iterator<String> it = subCache.keySet().iterator();
                int dropped = 0;
                while (it.hasNext() && dropped < SUB_CACHE_EVICT_BATCH) {
                    it.next();
                    it.remove();
                    dropped++;
                }
            }
            subCache.put(cleanId, entry);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getCached(String key, Class<T> type) {
        synchronized (cacheLock) {
            CacheEntry<Object> entry = responseCache.get(key);
            if (entry == null) return null;
            if (System.currentTimeMillis() - entry.storedAtMs > CACHE_TTL_MS) {
                responseCache.remove(key);
                return null;
            }
            return type.isInstance(entry.value) ? (T) entry.value : null;
        }
    }

    private void putCached(String key, Object value) {
        synchronized (cacheLock) {
            if (responseCache.size() > MAX_CACHE_ENTRIES) {
                Iterator<String> it = responseCache.keySet().iterator();
                int dropped = 0;
                while (it.hasNext() && dropped < CACHE_EVICT_BATCH) {
                    it.next();
                    it.remove();
                    dropped++;
                }
            }
            responseCache.put(key, new CacheEntry<>(System.currentTimeMillis(), value));
        }
    }

    private SearchPage awaitFuture(FutureTask<SearchPage> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Archive request interrupted.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException(cause != null ? cause.getMessage() : "Archive request failed.", cause);
        }
    }

    JSONObject requestJson(String url) throws IOException {
        for (int attempt = 0; attempt <= 2; attempt++) {
            HttpResponse res;
            try {
                res = transport.get(url);
            } catch (IOException err) {
                if (attempt < 2) {
                    sleepQuietly(800L * (attempt + 1));
                    continue;
                }
                throw new IOException("Network error reaching arctic-shift.photon-reddit.com", err);
            }

            if (res.statusCode == 429 || res.statusCode >= 500) {
                if (attempt < 2) {
                    sleepQuietly(1200L * (attempt + 1));
                    continue;
                }
                throw new IOException(res.statusCode == 429
                        ? "The archive is rate limiting requests. Try again in a moment."
                        : "Archive server returned " + res.statusCode + ".");
            }

            if (res.statusCode < 200 || res.statusCode >= 300) {
                String detail = "";
                try {
                    if (res.body != null && !res.body.isEmpty()) {
                        JSONObject errObj = new JSONObject(res.body);
                        JSONObject nested = errObj.optJSONObject("error");
                        if (nested != null && nested.has("message")) {
                            detail = nested.optString("message", "");
                        }
                        if (detail.isEmpty()) detail = errObj.optString("message", "");
                        if (detail.isEmpty()) detail = errObj.optString("detail", "");
                    }
                } catch (JSONException ignored) {
                }
                throw new IOException(!detail.isEmpty()
                        ? detail
                        : "Archive server returned " + res.statusCode + ".");
            }

            try {
                JSONObject json = new JSONObject(res.body != null ? res.body : "{}");
                if (json.has("error") && !json.isNull("error")) {
                    JSONObject err = json.optJSONObject("error");
                    String msg = err != null
                            ? firstNonEmpty(err.optString("message", ""), err.optString("type", ""), "Archive error")
                            : json.optString("error", "Archive error");
                    throw new IOException(msg);
                }
                return json;
            } catch (JSONException e) {
                throw new IOException("Invalid JSON from archive server.", e);
            }
        }
        throw new IOException("Network error reaching arctic-shift.photon-reddit.com");
    }

    private static void sleepQuietly(long ms) throws IOException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Archive request interrupted.", e);
        }
    }

    // ------------------------------------------------------------------ //
    // JSON Shaping (1:1 port of FrostFeed background.js)
    // ------------------------------------------------------------------ //

    public static SearchPage parseSearchPage(String kind, JSONObject json, int limit) {
        JSONArray data = json != null ? json.optJSONArray("data") : null;
        if (data == null || data.length() == 0) {
            return new SearchPage(Collections.emptyList(), false, null);
        }

        List<ArchiveItem> items = new ArrayList<>(data.length());
        boolean isComments = "comments".equals(kind);
        for (int i = 0; i < data.length(); i++) {
            JSONObject obj = data.optJSONObject(i);
            if (obj == null) continue;
            items.add(isComments ? shapeComment(obj) : shapePost(obj));
        }

        boolean hasMore = data.length() >= limit;
        Long nextCursor = items.isEmpty() ? null : items.get(items.size() - 1).getCreatedUtc();
        return new SearchPage(items, hasMore, nextCursor);
    }

    public static ArchivedComment shapeComment(JSONObject c) {
        String body = c.optString("body", "");
        boolean removed = "[deleted]".equals(body)
                || "[removed]".equals(body)
                || "[ Removed by Reddit ]".equals(body);

        return new ArchivedComment(
                c.optString("id", ""),
                c.optString("author", ""),
                body,
                c.optLong("created_utc", 0L),
                c.optString("subreddit", ""),
                optNullString(c, "subreddit_id"),
                optNullString(c, "permalink"),
                optNullableInt(c, "score"),
                optNullString(c, "distinguished"),
                optNullString(c, "author_flair_text"),
                shapeFlairRichtext(c.optJSONArray("author_flair_richtext")),
                optNullString(c, "author_flair_background_color"),
                optNullString(c, "author_flair_text_color"),
                c.optBoolean("is_submitter", false),
                optNullString(c, "parent_id"),
                isTruthyEdited(c),
                c.optBoolean("stickied", false),
                c.optBoolean("locked", false),
                c.optBoolean("archived", false),
                optNullString(c, "link_id"),
                optNullString(c, "link_title"),
                optNullString(c, "link_permalink"),
                optNullString(c, "link_author"),
                removed
        );
    }

    public static ArchivedPost shapePost(JSONObject p) {
        JSONObject secureMedia = p.optJSONObject("secure_media");
        JSONObject media = p.optJSONObject("media");
        JSONObject rv = secureMedia != null ? secureMedia.optJSONObject("reddit_video") : null;
        if (rv == null && media != null) {
            rv = media.optJSONObject("reddit_video");
        }

        CrosspostInfo crosspost = null;
        JSONArray xpList = p.optJSONArray("crosspost_parent_list");
        if (xpList != null && xpList.length() > 0) {
            JSONObject xp = xpList.optJSONObject(0);
            if (xp != null) {
                crosspost = new CrosspostInfo(
                        xp.optString("subreddit", ""),
                        xp.optString("author", ""),
                        xp.optString("title", ""),
                        xp.optLong("created_utc", 0L),
                        optNullableInt(xp, "score"),
                        optNullableInt(xp, "num_comments"),
                        optNullString(xp, "permalink"),
                        xp.optBoolean("over_18", false)
                );
            }
        }

        boolean isSelf = p.optBoolean("is_self", false);
        String selftext = p.optString("selftext", "");
        boolean removed = "[removed]".equals(selftext) || "[deleted]".equals(selftext);

        String videoUrl = rv != null ? unescapeUrl(optNullString(rv, "fallback_url")) : null;
        Integer videoDuration = rv != null ? optNullableInt(rv, "duration") : null;

        return new ArchivedPost(
                p.optString("id", ""),
                p.optString("author", ""),
                p.optString("title", ""),
                selftext,
                isSelf ? null : optNullString(p, "url"),
                p.optLong("created_utc", 0L),
                p.optString("subreddit", ""),
                optNullString(p, "subreddit_id"),
                optNullString(p, "permalink"),
                optNullableInt(p, "score"),
                optNullableInt(p, "num_comments"),
                p.optBoolean("over_18", false),
                p.optBoolean("spoiler", false),
                p.optBoolean("stickied", false) || p.optBoolean("pinned", false),
                p.optBoolean("locked", false),
                p.optBoolean("archived", false),
                isTruthyEdited(p),
                isSelf,
                p.optBoolean("is_video", false),
                p.optBoolean("is_gallery", false),
                optNullString(p, "post_hint"),
                extractThumbnail(optNullString(p, "thumbnail")),
                extractPreview(p),
                videoUrl,
                videoDuration,
                extractGallery(p),
                crosspost,
                optNullString(p, "distinguished"),
                optNullString(p, "link_flair_text"),
                shapeFlairRichtext(p.optJSONArray("link_flair_richtext")),
                optNullString(p, "link_flair_background_color"),
                optNullString(p, "link_flair_text_color"),
                optNullString(p, "domain"),
                removed
        );
    }

    public static UserStats parseUserStats(JSONObject json) {
        JSONArray data = json != null ? json.optJSONArray("data") : null;
        if (data == null || data.length() == 0) return null;
        JSONObject row = data.optJSONObject(0);
        if (row == null) return null;
        JSONObject meta = row.optJSONObject("_meta");
        if (meta == null) meta = new JSONObject();

        return new UserStats(
                optNullString(row, "author"),
                optNullString(row, "id"),
                optNullableLong(meta, "num_posts"),
                optNullableLong(meta, "num_comments"),
                optNullableLong(meta, "total_karma"),
                optNullableLong(meta, "earliest_post_at"),
                optNullableLong(meta, "earliest_comment_at")
        );
    }

    public static SubredditInfo parseSubredditInfo(JSONObject row) {
        String commRaw = optNullString(row, "community_icon");
        String stdRaw = optNullString(row, "icon_img");
        String commIcon = isHttpUrl(commRaw) ? unescapeUrl(commRaw) : null;
        String stdIcon = isHttpUrl(stdRaw) ? unescapeUrl(stdRaw) : null;
        String icon = commIcon != null ? commIcon : stdIcon;
        String fallback = (commIcon != null && stdIcon != null && !commIcon.equals(stdIcon)) ? stdIcon : null;
        String color = validHexColor(optNullString(row, "primary_color"));
        if (color == null) {
            color = validHexColor(optNullString(row, "key_color"));
        }
        return new SubredditInfo(icon, fallback, color);
    }

    public static String unescapeUrl(String u) {
        if (u == null || u.isEmpty()) return null;
        return u.replace("&amp;", "&");
    }

    public static String extractPreview(JSONObject p) {
        JSONObject preview = p.optJSONObject("preview");
        if (preview == null) return null;
        JSONArray images = preview.optJSONArray("images");
        if (images == null || images.length() == 0) return null;
        JSONObject img = images.optJSONObject(0);
        if (img == null) return null;

        JSONArray resolutions = img.optJSONArray("resolutions");
        JSONObject pick = null;
        if (resolutions != null && resolutions.length() > 0) {
            for (int i = 0; i < resolutions.length(); i++) {
                JSONObject r = resolutions.optJSONObject(i);
                if (r == null) continue;
                int w = r.optInt("width", 0);
                if (w >= 640 && w <= 1200) {
                    pick = r;
                    break;
                }
            }
            if (pick == null) {
                pick = resolutions.optJSONObject(resolutions.length() - 1);
            }
        }
        JSONObject source = img.optJSONObject("source");
        if (pick == null) pick = source;
        String pickedUrl = pick != null ? optNullString(pick, "url") : null;
        if (pickedUrl == null && source != null) {
            pickedUrl = optNullString(source, "url");
        }
        return unescapeUrl(pickedUrl);
    }

    public static List<GalleryItem> extractGallery(JSONObject p) {
        if (!p.optBoolean("is_gallery", false)) return null;
        JSONObject galleryData = p.optJSONObject("gallery_data");
        JSONObject mediaMeta = p.optJSONObject("media_metadata");
        if (galleryData == null || mediaMeta == null) return null;
        JSONArray items = galleryData.optJSONArray("items");
        if (items == null || items.length() == 0) return null;

        List<GalleryItem> out = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String mediaId = optNullString(item, "media_id");
            if (mediaId == null) continue;
            JSONObject meta = mediaMeta.optJSONObject(mediaId);
            if (meta == null || "failed".equals(meta.optString("status", ""))) continue;

            JSONArray previews = meta.optJSONArray("p");
            JSONObject pick = null;
            if (previews != null && previews.length() > 0) {
                for (int j = 0; j < previews.length(); j++) {
                    JSONObject r = previews.optJSONObject(j);
                    if (r == null) continue;
                    int x = r.optInt("x", 0);
                    if (x >= 640 && x <= 1200) {
                        pick = r;
                        break;
                    }
                }
                if (pick == null) {
                    pick = previews.optJSONObject(previews.length() - 1);
                }
            }
            JSONObject s = meta.optJSONObject("s");
            if (pick == null) pick = s;
            String url = pick != null ? optNullString(pick, "u") : null;
            if (url == null && s != null) {
                url = optNullString(s, "u");
                if (url == null) url = optNullString(s, "gif");
            }
            url = unescapeUrl(url);
            if (url != null) {
                out.add(new GalleryItem(url, optNullString(item, "caption")));
            }
        }
        return out.isEmpty() ? null : out;
    }

    public static String extractThumbnail(String t) {
        if (!isHttpUrl(t)) return null;
        return unescapeUrl(t);
    }

    public static List<FlairPart> shapeFlairRichtext(JSONArray rt) {
        if (rt == null || rt.length() == 0) return null;
        List<FlairPart> out = new ArrayList<>();
        for (int i = 0; i < rt.length(); i++) {
            JSONObject part = rt.optJSONObject(i);
            if (part == null) continue;
            String e = part.optString("e", "");
            if ("emoji".equals(e)) {
                String u = optNullString(part, "u");
                if (isHttpUrl(u)) {
                    out.add(new FlairPart(true, part.optString("a", ""), unescapeUrl(u)));
                }
            } else if ("text".equals(e)) {
                String t = part.optString("t", "");
                if (!t.isEmpty()) {
                    out.add(new FlairPart(false, t, null));
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    public static String validHexColor(String c) {
        if (c == null) return null;
        String trimmed = c.trim();
        return HEX_COLOR_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    public static String stripSubPrefix(String raw) {
        if (raw == null) return null;
        String clean = raw.trim().toLowerCase(Locale.US);
        if (clean.startsWith("t5_")) {
            clean = clean.substring(3);
        }
        if (clean.isEmpty() || !CLEAN_SUB_ID_PATTERN.matcher(clean).matches()) {
            return null;
        }
        return clean;
    }

    private static boolean isHttpUrl(String s) {
        return s != null && HTTP_URL_PATTERN.matcher(s).find();
    }

    private static boolean isTruthyEdited(JSONObject obj) {
        if (!obj.has("edited") || obj.isNull("edited")) return false;
        Object val = obj.opt("edited");
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).longValue() > 0L;
        return false;
    }

    private static String optNullString(JSONObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null;
        String val = obj.optString(key, "");
        return val.isEmpty() ? null : val;
    }

    private static Integer optNullableInt(JSONObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null;
        Object v = obj.opt(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return null;
    }

    private static Long optNullableLong(JSONObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null;
        Object v = obj.opt(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return null;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s != null ? s : "";
        }
    }

    /**
     * Default HttpURLConnection transport with zero Reddit cookies/auth headers.
     */
    public static final class DefaultHttpTransport implements HttpTransport {
        @Override
        public HttpResponse get(String urlString) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "FrostFeed-Android/1.0");
            // Explicitly avoid sending any cookies.
            conn.setRequestProperty("Cookie", "");

            try {
                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 400)
                        ? conn.getInputStream()
                        : conn.getErrorStream();
                String body = is != null ? readStream(is) : "";
                return new HttpResponse(code, body);
            } finally {
                conn.disconnect();
            }
        }

        private static String readStream(InputStream is) throws IOException {
            try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }
    }
}
