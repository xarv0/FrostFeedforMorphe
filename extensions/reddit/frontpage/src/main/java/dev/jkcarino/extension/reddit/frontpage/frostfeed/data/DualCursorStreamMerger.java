package dev.jkcarino.extension.reddit.frontpage.frostfeed.data;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.jkcarino.extension.reddit.frontpage.frostfeed.api.ArcticShiftClient;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchiveItem;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.FeedSection;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SearchPage;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SortOrder;

/**
 * Dual-cursor chronological stream merger (1:1 port of FrostFeed's content.js).
 *
 * Overview interleaves posts and comments using independent cursors so accounts
 * with thousands of comments and only a few posts still merge accurately.
 */
public class DualCursorStreamMerger {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int DEFAULT_REFILL_SIZE = 50;

    private static final class StreamState {
        Long cursor = null;
        final ArrayDeque<ArchiveItem> buffer = new ArrayDeque<>();
        boolean hasMore = true;
        boolean failed = false;
    }

    public static final class PageResult {
        public final List<ArchiveItem> newItems;
        public final boolean hasMore;
        public final String error;

        public PageResult(List<ArchiveItem> newItems, boolean hasMore, String error) {
            this.newItems = Collections.unmodifiableList(newItems);
            this.hasMore = hasMore;
            this.error = error;
        }
    }

    private final ArcticShiftClient client;
    private final String author;
    private final FeedSection section;
    private final SortOrder sort;
    private final int pageSize;
    private final int refillSize;

    private final Map<String, StreamState> streams = new LinkedHashMap<>();
    private final Set<String> seenKeys = new HashSet<>();
    private boolean hasMore = true;

    public DualCursorStreamMerger(
            ArcticShiftClient client,
            String author,
            FeedSection section,
            SortOrder sort
    ) {
        this(client, author, section, sort, DEFAULT_PAGE_SIZE, DEFAULT_REFILL_SIZE);
    }

    public DualCursorStreamMerger(
            ArcticShiftClient client,
            String author,
            FeedSection section,
            SortOrder sort,
            int pageSize,
            int refillSize
    ) {
        this.client = client;
        this.author = author;
        this.section = section;
        this.sort = sort;
        this.pageSize = pageSize;
        this.refillSize = refillSize;

        if (section == FeedSection.POSTS) {
            streams.put("posts", new StreamState());
        } else if (section == FeedSection.COMMENTS) {
            streams.put("comments", new StreamState());
        } else {
            streams.put("posts", new StreamState());
            streams.put("comments", new StreamState());
        }
    }

    public boolean hasMore() {
        return hasMore;
    }

    public FeedSection getSection() {
        return section;
    }

    public SortOrder getSort() {
        return sort;
    }

    /**
     * Loads the next chronological page of items. Must be called from a background thread.
     */
    public synchronized PageResult loadNextPage() {
        if (!hasMore) {
            return new PageResult(Collections.emptyList(), false, null);
        }

        List<ArchiveItem> batch = new ArrayList<>();
        String firstError = null;
        int emitted = 0;

        while (emitted < pageSize) {
            for (Map.Entry<String, StreamState> entry : streams.entrySet()) {
                StreamState s = entry.getValue();
                if (s.buffer.isEmpty() && s.hasMore) {
                    String err = refill(entry.getKey(), s);
                    if (err != null && firstError == null) {
                        firstError = err;
                    }
                }
            }

            ArchiveItem next = takeNext();
            if (next == null) {
                break;
            }

            if (seenKeys.add(next.getDedupeKey())) {
                batch.add(next);
                emitted++;
            }
        }

        boolean anyRemaining = false;
        for (StreamState s : streams.values()) {
            if (s.hasMore || !s.buffer.isEmpty()) {
                anyRemaining = true;
                break;
            }
        }

        // Guard against spinning on a cursor that is not advancing.
        if (emitted == 0) {
            anyRemaining = false;
        }
        hasMore = anyRemaining;

        // Only surface an error if nothing was emitted or all sources failed.
        String surfacedError = (emitted == 0 && firstError != null) ? firstError : null;
        return new PageResult(batch, hasMore, surfacedError);
    }

    private String refill(String kind, StreamState s) {
        try {
            Long before = (sort == SortOrder.NEW) ? s.cursor : null;
            Long after = (sort == SortOrder.OLD) ? s.cursor : null;
            SearchPage res = client.search(kind, author, sort, before, after, refillSize);
            if (res == null || res.items == null) {
                throw new IOException("Unexpected archive response.");
            }
            s.buffer.addAll(res.items);
            if (!res.items.isEmpty()) {
                s.cursor = res.items.get(res.items.size() - 1).getCreatedUtc();
            }
            s.hasMore = res.hasMore && !res.items.isEmpty();
            return null;
        } catch (IOException e) {
            s.failed = true;
            s.hasMore = false;
            return e.getMessage() != null ? e.getMessage() : "Archive request failed.";
        }
    }

    private ArchiveItem takeNext() {
        StreamState bestStream = null;
        ArchiveItem best = null;
        for (StreamState s : streams.values()) {
            ArchiveItem head = s.buffer.peekFirst();
            if (head == null) continue;
            if (best == null || isBetter(head, best)) {
                best = head;
                bestStream = s;
            }
        }
        if (bestStream == null) return null;
        return bestStream.buffer.pollFirst();
    }

    private boolean isBetter(ArchiveItem candidate, ArchiveItem currentBest) {
        if (sort == SortOrder.OLD) {
            return candidate.getCreatedUtc() < currentBest.getCreatedUtc();
        }
        return candidate.getCreatedUtc() > currentBest.getCreatedUtc();
    }
}
