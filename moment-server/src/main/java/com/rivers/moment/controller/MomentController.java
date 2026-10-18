package com.rivers.moment.controller;

import com.rivers.core.vo.ResultVO;
import com.rivers.moment.AddCommentReq;
import com.rivers.moment.AddCommentRes;
import com.rivers.moment.DeleteMomentReq;
import com.rivers.moment.GetCommentPageReq;
import com.rivers.moment.GetCommentPageRes;
import com.rivers.moment.GetMomentPageReq;
import com.rivers.moment.GetMomentPageRes;
import com.rivers.moment.PublishMomentReq;
import com.rivers.moment.PublishMomentRes;
import com.rivers.moment.ToggleLikeReq;
import com.rivers.moment.ToggleLikeRes;
import com.rivers.moment.service.IMomentService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 用户动态 前端控制器
 * </p>
 *
 * @author rivers
 * @since 2026-10-17
 */
@RestController
@RequestMapping("moment")
public class MomentController {

    private final IMomentService momentService;

    public MomentController(IMomentService momentService) {
        this.momentService = momentService;
    }

    /**
     * 发布动态（身份取网关注入报文的 loginUser）
     */
    @PostMapping("publishMoment")
    public Mono<ResultVO<PublishMomentRes>> publishMoment(@RequestBody PublishMomentReq req) {
        if (!hasLoginUser(req.hasLoginUser(), req.getLoginUser().getUserId())) {
            return Mono.just(ResultVO.fail("未获取到登录身份"));
        }
        return momentService.publishMoment(req);
    }

    /**
     * 动态分页列表（公开接口，可按用户过滤）
     */
    @PostMapping("getMomentPage")
    public Mono<ResultVO<GetMomentPageRes>> getMomentPage(@RequestBody GetMomentPageReq req) {
        return momentService.getMomentPage(req);
    }

    /**
     * 删除动态（仅作者本人可删）
     */
    @PostMapping("deleteMoment")
    public Mono<ResultVO<Void>> deleteMoment(@RequestBody DeleteMomentReq req) {
        if (!hasLoginUser(req.hasLoginUser(), req.getLoginUser().getUserId())) {
            return Mono.just(ResultVO.fail("未获取到登录身份"));
        }
        return momentService.deleteMoment(req);
    }

    /**
     * 点赞 / 取消点赞
     */
    @PostMapping("toggleLike")
    public Mono<ResultVO<ToggleLikeRes>> toggleLike(@RequestBody ToggleLikeReq req) {
        if (!hasLoginUser(req.hasLoginUser(), req.getLoginUser().getUserId())) {
            return Mono.just(ResultVO.fail("未获取到登录身份"));
        }
        return momentService.toggleLike(req);
    }

    /**
     * 评论分页（公开接口）
     */
    @PostMapping("getCommentPage")
    public Mono<ResultVO<GetCommentPageRes>> getCommentPage(@RequestBody GetCommentPageReq req) {
        return momentService.getCommentPage(req);
    }

    /**
     * 发表评论
     */
    @PostMapping("addComment")
    public Mono<ResultVO<AddCommentRes>> addComment(@RequestBody AddCommentReq req) {
        if (!hasLoginUser(req.hasLoginUser(), req.getLoginUser().getUserId())) {
            return Mono.just(ResultVO.fail("未获取到登录身份"));
        }
        return momentService.addComment(req);
    }

    private static boolean hasLoginUser(boolean present, String userId) {
        return present && StringUtils.isNotBlank(userId);
    }
}
