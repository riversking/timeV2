package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetNewsReq;
import com.rivers.nba.GetNewsRes;
import com.rivers.nba.NewsItem;
import com.rivers.nba.service.INewsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>
 * NBA 新闻资讯 服务实现类（数据源 ESPN 公开接口，带 5 分钟内存缓存）
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
    private static final long CACHE_MILLIS = 5 * 60 * 1000L;
    private static final int DEFAULT_LIMIT = 10;

    private final AtomicReference<CachedNews> cache = new AtomicReference<>();

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
        return Mono.fromFuture(() -> HttpClientUtil.getAsync(NEWS_URL))
                .doOnNext(json -> {
                    if (StringUtils.isNotBlank(json)) {
                        cache.set(new CachedNews(json, System.currentTimeMillis()));
                    }
                });
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
