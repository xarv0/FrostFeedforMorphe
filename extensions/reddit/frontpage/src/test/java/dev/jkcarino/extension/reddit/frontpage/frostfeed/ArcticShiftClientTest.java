package dev.jkcarino.extension.reddit.frontpage.frostfeed;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import dev.jkcarino.extension.reddit.frontpage.frostfeed.api.ArcticShiftClient;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchivedComment;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchivedPost;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SearchPage;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SortOrder;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SubredditInfo;

public class ArcticShiftClientTest {

    @Test
    public void testShapePostWithGalleryFlairAndCrosspost() throws Exception {
        String json = "{"
                + "\"id\":\"abc123\","
                + "\"author\":\"alice\","
                + "\"title\":\"My Gallery Post\","
                + "\"selftext\":\"Hello **world**\","
                + "\"is_self\":false,"
                + "\"url\":\"https://www.reddit.com/gallery/abc123\","
                + "\"created_utc\":1700000000,"
                + "\"subreddit\":\"android\","
                + "\"subreddit_id\":\"t5_2qlqh\","
                + "\"permalink\":\"/r/android/comments/abc123/my_gallery_post/\","
                + "\"score\":420,"
                + "\"num_comments\":33,"
                + "\"over_18\":true,"
                + "\"spoiler\":false,"
                + "\"is_gallery\":true,"
                + "\"gallery_data\":{\"items\":[{\"media_id\":\"m1\",\"caption\":\"First\"},{\"media_id\":\"m2\"}]},"
                + "\"media_metadata\":{"
                + "  \"m1\":{\"status\":\"valid\",\"p\":[{\"x\":320,\"u\":\"https://preview.redd.it/small.jpg\"},{\"x\":960,\"u\":\"https://preview.redd.it/good.jpg?a=1&amp;b=2\"}]},"
                + "  \"m2\":{\"status\":\"valid\",\"s\":{\"u\":\"https://i.redd.it/full.png?x=1&amp;y=2\"}}"
                + "},"
                + "\"link_flair_text\":\":bot: News\","
                + "\"link_flair_background_color\":\"#ff4500\","
                + "\"link_flair_text_color\":\"light\","
                + "\"link_flair_richtext\":[{\"e\":\"emoji\",\"a\":\":bot:\",\"u\":\"https://emoji.redditmedia.com/bot&amp;v=1\"},{\"e\":\"text\",\"t\":\" News\"}]"
                + "}";

        ArchivedPost post = ArcticShiftClient.shapePost(new JSONObject(json));
        Assert.assertEquals("abc123", post.id);
        Assert.assertTrue(post.over18);
        Assert.assertTrue(post.isGallery);
        Assert.assertNotNull(post.gallery);
        Assert.assertEquals(2, post.gallery.size());
        Assert.assertEquals("https://preview.redd.it/good.jpg?a=1&b=2", post.gallery.get(0).url);
        Assert.assertEquals("First", post.gallery.get(0).caption);
        Assert.assertEquals("https://i.redd.it/full.png?x=1&y=2", post.gallery.get(1).url);

        Assert.assertNotNull(post.flairRich);
        Assert.assertEquals(2, post.flairRich.size());
        Assert.assertTrue(post.flairRich.get(0).isEmoji);
        Assert.assertEquals("https://emoji.redditmedia.com/bot&v=1", post.flairRich.get(0).url);
        Assert.assertFalse(post.flairRich.get(1).isEmoji);
        Assert.assertEquals(" News", post.flairRich.get(1).text);
    }

    @Test
    public void testShapeCommentRemovedDetection() throws Exception {
        JSONObject obj = new JSONObject("{"
                + "\"id\":\"c1\","
                + "\"author\":\"bob\","
                + "\"body\":\"[removed]\","
                + "\"created_utc\":1700000100,"
                + "\"subreddit\":\"test\","
                + "\"parent_id\":\"t1_parent\","
                + "\"is_submitter\":true,"
                + "\"edited\":1700000200"
                + "}");
        ArchivedComment c = ArcticShiftClient.shapeComment(obj);
        Assert.assertTrue(c.removed);
        Assert.assertTrue(c.isSubmitter);
        Assert.assertTrue(c.edited);
    }

    @Test
    public void testRetryOn429AndCaching() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        ArcticShiftClient client = new ArcticShiftClient(url -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                return new ArcticShiftClient.HttpResponse(429, "{\"error\":\"rate limited\"}");
            }
            return new ArcticShiftClient.HttpResponse(200,
                    "{\"data\":[{\"id\":\"p1\",\"author\":\"alice\",\"title\":\"Hi\",\"created_utc\":1000,\"subreddit\":\"test\"}]}");
        });

        SearchPage p1 = client.search("posts", "alice", SortOrder.NEW, null, null, 50);
        Assert.assertEquals(1, p1.items.size());
        Assert.assertEquals(2, calls.get()); // 1 retry after 429

        // Second identical request should hit 5-minute TTL cache.
        SearchPage p2 = client.search("posts", "alice", SortOrder.NEW, null, null, 50);
        Assert.assertEquals(1, p2.items.size());
        Assert.assertEquals(2, calls.get());
    }

    @Test
    public void testSubredditBatchingAndNegativeCache() {
        AtomicInteger calls = new AtomicInteger(0);
        ArcticShiftClient client = new ArcticShiftClient(url -> {
            calls.incrementAndGet();
            return new ArcticShiftClient.HttpResponse(200,
                    "{\"data\":[{\"id\":\"2qlqh\",\"community_icon\":\"https://img.example/comm.png?a=1&amp;b=2\",\"icon_img\":\"https://img.example/std.png\",\"primary_color\":\"#123456\"}]}");
        });

        Map<String, SubredditInfo> res = client.fetchSubreddits(Arrays.asList("t5_2qlqh", "t5_missing1"));
        Assert.assertEquals(1, calls.get());
        Assert.assertEquals("https://img.example/comm.png?a=1&b=2", res.get("t5_2qlqh").icon);
        Assert.assertEquals("https://img.example/std.png", res.get("t5_2qlqh").fallback);
        Assert.assertEquals("#123456", res.get("t5_2qlqh").color);
        Assert.assertNull(res.get("t5_missing1").icon);

        // Requesting the missing sub again should NOT trigger another network call.
        client.fetchSubreddits(Collections.singletonList("t5_missing1"));
        Assert.assertEquals(1, calls.get());
    }
}
