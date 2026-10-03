package com.rivers.nba.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetNewsReq;
import com.rivers.nba.GetNewsRes;
import com.rivers.nba.service.INewsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * NBA 新闻资讯 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-03
 */
@RestController
@RequestMapping("/news")
public class NewsController {

    private final INewsService newsService;

    public NewsController(INewsService newsService) {
        this.newsService = newsService;
    }

    @PostMapping("getNews")
    public Mono<ResultVO<GetNewsRes>> getNews(@RequestBody GetNewsReq req) {
        return newsService.getNews(req);
    }
}
