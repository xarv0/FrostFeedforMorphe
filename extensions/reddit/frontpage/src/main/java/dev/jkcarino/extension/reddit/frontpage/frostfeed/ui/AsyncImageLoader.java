package dev.jkcarino.extension.reddit.frontpage.frostfeed.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight asynchronous image loader with LruCache, downsampling, and no cookies/referrer.
 */
public final class AsyncImageLoader {

    public interface BitmapCallback {
        void onResult(Bitmap bitmap);
    }

    private static volatile AsyncImageLoader instance;

    public static AsyncImageLoader getInstance() {
        if (instance == null) {
            synchronized (AsyncImageLoader.class) {
                if (instance == null) {
                    instance = new AsyncImageLoader();
                }
            }
        }
        return instance;
    }

    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AsyncImageLoader() {
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024L);
        int cacheSizeKb = Math.max(4096, Math.min(maxKb / 10, 24 * 1024));
        this.memoryCache = new LruCache<String, Bitmap>(cacheSizeKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public void loadInto(
            ImageView imageView,
            String url,
            String fallbackUrl,
            int maxDimPx,
            boolean circular,
            Runnable onFailure
    ) {
        if (imageView == null) return;
        imageView.setTag(url);
        if (url == null || url.isEmpty()) {
            if (onFailure != null) onFailure.run();
            return;
        }

        String cacheKey = (circular ? "c:" : "r:") + maxDimPx + ":" + url;
        Bitmap cached = memoryCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            imageView.setImageBitmap(cached);
            return;
        }

        executor.execute(() -> {
            Bitmap bmp = fetchAndDecode(url, maxDimPx, circular);
            if (bmp == null && fallbackUrl != null && !fallbackUrl.isEmpty() && !fallbackUrl.equals(url)) {
                bmp = fetchAndDecode(fallbackUrl, maxDimPx, circular);
            }
            if (bmp != null) {
                memoryCache.put(cacheKey, bmp);
            }
            Bitmap finalBmp = bmp;
            mainHandler.post(() -> {
                Object currentTag = imageView.getTag();
                if (!url.equals(currentTag)) return;
                if (finalBmp != null) {
                    imageView.setImageBitmap(finalBmp);
                } else if (onFailure != null) {
                    onFailure.run();
                }
            });
        });
    }

    public void loadBitmap(String url, int maxDimPx, BitmapCallback callback) {
        if (url == null || url.isEmpty()) {
            if (callback != null) mainHandler.post(() -> callback.onResult(null));
            return;
        }
        String cacheKey = "r:" + maxDimPx + ":" + url;
        Bitmap cached = memoryCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            if (callback != null) mainHandler.post(() -> callback.onResult(cached));
            return;
        }
        executor.execute(() -> {
            Bitmap bmp = fetchAndDecode(url, maxDimPx, false);
            if (bmp != null) {
                memoryCache.put(cacheKey, bmp);
            }
            if (callback != null) {
                mainHandler.post(() -> callback.onResult(bmp));
            }
        });
    }

    private static Bitmap fetchAndDecode(String urlString, int maxDimPx, boolean circular) {
        try {
            byte[] bytes = downloadBytes(urlString);
            if (bytes == null || bytes.length == 0) return null;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

            int sampleSize = 1;
            int maxSide = Math.max(opts.outWidth, opts.outHeight);
            while (maxDimPx > 0 && (maxSide / sampleSize) > maxDimPx * 2) {
                sampleSize *= 2;
            }

            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = sampleSize;
            Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOpts);
            if (decoded == null) return null;
            return circular ? toCircle(decoded) : decoded;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte[] downloadBytes(String urlString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Cookie", "");
        conn.setRequestProperty("User-Agent", "FrostFeed-Android/1.0");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                int total = 0;
                // Cap at 8MB per image to prevent OOM.
                while ((n = is.read(buf)) != -1) {
                    total += n;
                    if (total > 8 * 1024 * 1024) return null;
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static Bitmap toCircle(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        if (size <= 0) return src;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = size / 2f;
        canvas.drawCircle(r, r, r, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        int left = (src.getWidth() - size) / 2;
        int top = (src.getHeight() - size) / 2;
        canvas.drawBitmap(src, new Rect(left, top, left + size, top + size),
                new Rect(0, 0, size, size), paint);
        if (out != src) {
            src.recycle();
        }
        return out;
    }
}
