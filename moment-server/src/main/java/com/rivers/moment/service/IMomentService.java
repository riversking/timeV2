package com.rivers.moment.service;

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
import reactor.core.publisher.Mono;

/**
 * <p>
 * 用户动态 服务接口
 * </p>
 *
 * @author rivers
 * @since 2026-10-17
 */
public interface IMomentService {

    /**
     * 发布动态（身份取网关注入的 loginUser）
     */
    Mono<ResultVO<PublishMomentRes>> publishMoment(PublishMomentReq req);

    /**
     * 动态分页列表（公开，userId 非空时仅返回该用户的动态）
     */
    Mono<ResultVO<GetMomentPageRes>> getMomentPage(GetMomentPageReq req);

    /**
     * 删除动态（软删除动态及其图片，仅作者本人可删）
     */
    Mono<ResultVO<Void>> deleteMoment(DeleteMomentReq req);

    /**
     * 点赞切换（已赞取消，未赞点赞）
     */
    Mono<ResultVO<ToggleLikeRes>> toggleLike(ToggleLikeReq req);

    /**
     * 评论分页（公开）
     */
    Mono<ResultVO<GetCommentPageRes>> getCommentPage(GetCommentPageReq req);

    /**
     * 发表评论
     */
    Mono<ResultVO<AddCommentRes>> addComment(AddCommentReq req);
}
