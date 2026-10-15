package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rivers.core.client.WebClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetNewsDetailReq;
import com.rivers.nba.GetNewsDetailRes;
import com.rivers.nba.GetNewsReq;
import com.rivers.nba.GetNewsRes;
import com.rivers.nba.NewsDetail;
import com.rivers.nba.NewsItem;
import com.rivers.nba.service.INewsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>
 * NBA 新闻资讯 服务实现类（数据源 ESPN 公开接口，列表 5 分钟 / 正文 10 分钟内存缓存）
 * </p>
 *
 * @author rivers
 * @since 2026-10-03
 */
@Slf4j
@Service
public class NewsServiceImpl implements INewsService {

    // ESPN 默认仅返回 6 篇，携带 limit 参数可获取更多（前端按需截取）
    private static final String NEWS_URL = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/news?limit=50";
    // ESPN 文章正文接口（列表条目 links.api.self 指向的地址，headlines[0].story 为正文 HTML）
    private static final String NEWS_DETAIL_URL_PREFIX = "https://content.core.api.espn.com/v1/sports/news/";
    private static final long CACHE_MILLIS = 5 * 60 * 1000L;
    private static final long DETAIL_CACHE_MILLIS = 10 * 60 * 1000L;
    private static final int DETAIL_CACHE_MAX = 200;
    private static final int DEFAULT_LIMIT = 10;

    private final AtomicReference<CachedNews> cache = new AtomicReference<>();

    private final Map<String, CachedDetail> detailCache = new ConcurrentHashMap<>();

    private final WebClientUtil webClientUtil;

    public NewsServiceImpl(WebClientUtil webClientUtil) {
        this.webClientUtil = webClientUtil;
    }

    @Override
    public Mono<ResultVO<GetNewsRes>> getNews(GetNewsReq req) {
        int limit = resolveLimit(req.getLimit());
        return loadNewsJson()
                .map(json -> ResultVO.ok(GetNewsRes.newBuilder()
                        .addAllNews(parseNews(json, limit))
                        .build()))
                .onErrorResume(e -> {
                    log.error("拉取 NBA 新闻失败: {}", e.getMessage());
                    return Mono.just(ResultVO.ok(GetNewsRes.newBuilder().getDefaultInstanceForType()));
                });
    }

    private Mono<String> loadNewsJson() {
        CachedNews cached = cache.get();
        if (cached != null && System.currentTimeMillis() - cached.time() < CACHE_MILLIS) {
            return Mono.just(cached.json());
        }
        return webClientUtil.get(NEWS_URL)
                .doOnNext(json -> {
                    if (StringUtils.isNotBlank(json)) {
                        cache.set(new CachedNews(json, System.currentTimeMillis()));
                    }
                });
    }

    @Override
    public Mono<ResultVO<GetNewsDetailRes>> getNewsDetail(GetNewsDetailReq req) {
        String id = StringUtils.trimToEmpty(req.getId());
        if (StringUtils.isBlank(id) || !StringUtils.isNumeric(id)) {
            return Mono.just(ResultVO.fail("新闻ID不合法"));
        }
        return loadDetailJson(id)
                .map(json -> ResultVO.ok(GetNewsDetailRes.newBuilder()
                        .setDetail(parseDetail(id, json))
                        .build()))
                .onErrorResume(e -> {
                    log.error("拉取 NBA 新闻详情失败, id={}: {}", id, e.getMessage());
                    return Mono.just(ResultVO.ok(GetNewsDetailRes.newBuilder().getDefaultInstanceForType()));
                });
    }

    private Mono<String> loadDetailJson(String id) {
        CachedDetail cached = detailCache.get(id);
        if (cached != null && System.currentTimeMillis() - cached.time() < DETAIL_CACHE_MILLIS) {
            return Mono.just(cached.json());
        }
        return webClientUtil.get(NEWS_DETAIL_URL_PREFIX + id)
                .doOnNext(json -> {
                    if (StringUtils.isNotBlank(json)) {
                        if (detailCache.size() >= DETAIL_CACHE_MAX) {
                            detailCache.clear();
                        }
                        detailCache.put(id, new CachedDetail(json, System.currentTimeMillis()));
                    }
                });
    }

    private NewsDetail parseDetail(String id, String json) {
        NewsDetail.Builder builder = NewsDetail.newBuilder().setId(id);
        JSONObject headline = Optional.ofNullable(JSON.parseObject(json))
                .map(o -> o.getJSONArray("headlines"))
                .filter(items -> !items.isEmpty())
                .map(items -> items.getJSONObject(0))
                .orElse(null);
        if (headline == null) {
            return builder.build();
        }
        return builder
                .setTitle(StringUtils.defaultString(headline.getString("headline")))
                .setDescription(StringUtils.defaultString(headline.getString("description")))
                .setPublished(StringUtils.defaultString(headline.getString("published")))
                .setByline(StringUtils.defaultString(headline.getString("byline")))
                .setImageUrl(extractImageUrl(headline))
                .setLink(extractLink(headline))
                .setContent(sanitizeStory(headline.getString("story")))
                .build();
    }

    /**
     * 正文 HTML 白名单清洗：仅保留排版类标签，剥离脚本/样式/内联属性，外链改为新窗口打开
     */
    private String sanitizeStory(String storyHtml) {
        if (StringUtils.isBlank(storyHtml)) {
            return "";
        }
        Safelist safelist = Safelist.relaxed().addTags("hr", "figure", "figcaption");
        Document doc = Jsoup.parse(Jsoup.clean(storyHtml, safelist));
        doc.outputSettings().prettyPrint(false);
        doc.select("a").attr("target", "_blank").attr("rel", "noopener noreferrer");
        doc.select("img").attr("loading", "lazy");
        return doc.body().html();
    }

    private List<NewsItem> parseNews(String json, int limit) {
        JSONArray articles = Optional.ofNullable(JSON.parseObject(json))
                .map(o -> o.getJSONArray("articles"))
                .orElse(null);
        if (articles == null) {
            return List.of();
        }
        return articles.stream()
                .limit(limit)
                .map(item -> (JSONObject) item)
                .map(jo -> NewsItem.newBuilder()
                        .setTitle(StringUtils.defaultString(jo.getString("headline")))
                        .setDescription(StringUtils.defaultString(jo.getString("description")))
                        .setPublished(StringUtils.defaultString(jo.getString("published")))
                        .setImageUrl(extractImageUrl(jo))
                        .setLink(extractLink(jo))
                        .setType(StringUtils.defaultString(jo.getString("type")))
                        .setId(StringUtils.defaultString(jo.getString("id")))
                        .build())
                .toList();
    }

    private String extractImageUrl(JSONObject jo) {
        JSONArray images = jo.getJSONArray("images");
        if (images == null || images.isEmpty()) {
            return "";
        }
        return StringUtils.defaultString(images.getJSONObject(0).getString("url"));
    }

    private String extractLink(JSONObject jo) {
        return Optional.ofNullable(jo.getJSONObject("links"))
                .map(links -> links.getJSONObject("web"))
                .map(web -> web.getString("href"))
                .orElse("");
    }

    private record CachedNews(String json, long time) {
    }

    private record CachedDetail(String json, long time) {
    }

    /**
     * 解析有效的返回条数（非正数时取默认值）
     */
    private static int resolveLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return limit;
    }
}
