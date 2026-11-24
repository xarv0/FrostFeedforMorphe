package dev.jkcarino.extension.reddit.frontpage.frostfeed;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import dev.jkcarino.extension.reddit.frontpage.frostfeed.api.ArcticShiftClient;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.data.DualCursorStreamMerger;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchiveItem;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.FeedSection;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SortOrder;

public class DualCursorStreamMergerTest {

    @Test
    public void testInterleavesSkewedPostsAndCommentsChronologicallyDesc() {
        // User has 2 posts (ts 950, 500) and 6 comments (ts 1000, 900, 800, 700, 600, 400).
        // With refillSize=3 and pageSize=5, the merger must accurately interleave both streams.
        ArcticShiftClient client = new ArcticShiftClient(url -> {
            if (url.contains("/posts/search")) {
                if (url.contains("before=")) {
                    return new ArcticShiftClient.HttpResponse(200, "{\"data\":[]}");
                }
                return new ArcticShiftClient.HttpResponse(200, "{\"data\":["
                        + "{\"id\":\"p1\",\"author\":\"u\",\"title\":\"P1\",\"created_utc\":950,\"subreddit\":\"s\"},"
                        + "{\"id\":\"p2\",\"author\":\"u\",\"title\":\"P2\",\"created_utc\":500,\"subreddit\":\"s\"}"
                        + "]}");
            }
            if (url.contains("/comments/search")) {
                if (url.contains("before=800")) {
                    return new ArcticShiftClient.HttpResponse(200, "{\"data\":["
                            + "{\"id\":\"c4\",\"author\":\"u\",\"body\":\"C4\",\"created_utc\":700,\"subreddit\":\"s\"},"
                            + "{\"id\":\"c5\",\"author\":\"u\",\"body\":\"C5\",\"created_utc\":600,\"subreddit\":\"s\"},"
                            + "{\"id\":\"c6\",\"author\":\"u\",\"body\":\"C6\",\"created_utc\":400,\"subreddit\":\"s\"}"
                            + "]}");
                }
                if (url.contains("before=400")) {
                    return new ArcticShiftClient.HttpResponse(200, "{\"data\":[]}");
                }
                return new ArcticShiftClient.HttpResponse(200, "{\"data\":["
                        + "{\"id\":\"c1\",\"author\":\"u\",\"body\":\"C1\",\"created_utc\":1000,\"subreddit\":\"s\"},"
                        + "{\"id\":\"c2\",\"author\":\"u\",\"body\":\"C2\",\"created_utc\":900,\"subreddit\":\"s\"},"
                        + "{\"id\":\"c3\",\"author\":\"u\",\"body\":\"C3\",\"created_utc\":800,\"subreddit\":\"s\"}"
                        + "]}");
            }
            return new ArcticShiftClient.HttpResponse(404, "{}");
        });

        DualCursorStreamMerger merger = new DualCursorStreamMerger(
                client, "u", FeedSection.OVERVIEW, SortOrder.NEW, 5, 3);

        List<ArchiveItem> all = new ArrayList<>();
        DualCursorStreamMerger.PageResult p1 = merger.loadNextPage();
        Assert.assertNull(p1.error);
        Assert.assertEquals(5, p1.newItems.size());
        Assert.assertTrue(p1.hasMore);
        all.addAll(p1.newItems);

        DualCursorStreamMerger.PageResult p2 = merger.loadNextPage();
        Assert.assertNull(p2.error);
        Assert.assertEquals(3, p2.newItems.size());
        all.addAll(p2.newItems);

        long[] expectedTs = {1000, 950, 900, 800, 700, 600, 500, 400};
        Assert.assertEquals(expectedTs.length, all.size());
        for (int i = 0; i < expectedTs.length; i++) {
            Assert.assertEquals("Mismatch at index " + i, expectedTs[i], all.get(i).getCreatedUtc());
        }
    }
}
