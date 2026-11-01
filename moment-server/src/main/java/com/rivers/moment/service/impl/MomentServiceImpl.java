package com.rivers.moment.service.impl;

import com.rivers.core.vo.ResultVO;
import com.rivers.moment.*;
import com.rivers.moment.entity.Moment;
import com.rivers.moment.entity.MomentComment;
import com.rivers.moment.entity.MomentImage;
import com.rivers.moment.entity.MomentLike;
import com.rivers.moment.entity.MomentShare;
import com.rivers.moment.repository.MomentCommentRepository;
import com.rivers.moment.repository.MomentImageRepository;
import com.rivers.moment.repository.MomentLikeRepository;
import com.rivers.moment.repository.MomentRepository;
import com.rivers.moment.repository.MomentShareRepository;
import com.rivers.moment.service.IMomentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.rivers.core.util.DateTimeUtils.formatDateTime;
import static com.rivers.core.util.ValueUtils.defaultInt;
import static com.rivers.core.util.ValueUtils.defaultLong;
import static com.rivers.core.util.ValueUtils.defaultString;

/**
 * <p>
 * 用户动态 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2026-10-17
 */
@Slf4j
@Service
public class MomentServiceImpl implements IMomentService {

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_IMAGE_COUNT = 9;
    private static final int MAX_COMMENT_LENGTH = 500;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SHARE_USERS = 50;

    private final MomentRepository momentRepository;
    private final MomentImageRepository momentImageRepository;
    private final MomentLikeRepository momentLikeRepository;
    private final MomentCommentRepository momentCommentRepository;
    private final MomentShareRepository momentShareRepository;
    private final TransactionalOperator txOperator;

    public MomentServiceImpl(MomentRepository momentRepository,
                             MomentImageRepository momentImageRepository,
                             MomentLikeRepository momentLikeRepository,
                             MomentCommentRepository momentCommentRepository,
                             MomentShareRepository momentShareRepository,
                             TransactionalOperator txOperator) {
        this.momentRepository = momentRepository;
        this.momentImageRepository = momentImageRepository;
        this.momentLikeRepository = momentLikeRepository;
        this.momentCommentRepository = momentCommentRepository;
        this.momentShareRepository = momentShareRepository;
        this.txOperator = txOperator;
    }

    @Override
    public Mono<ResultVO<PublishMomentRes>> publishMoment(PublishMomentReq req) {
        LoginUser loginUser = req.getLoginUser();
        String content = StringUtils.trimToEmpty(req.getContent());
        int teamId = req.getTeamId();
        List<String> images = req.getImagesList().stream()
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .toList();
        if (content.isEmpty() && images.isEmpty()) {
            return Mono.just(ResultVO.fail("动态内容和图片不能同时为空"));
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            return Mono.just(ResultVO.fail("动态内容不能超过 " + MAX_CONTENT_LENGTH + " 字"));
        }
        if (images.size() > MAX_IMAGE_COUNT) {
            return Mono.just(ResultVO.fail("最多上传 " + MAX_IMAGE_COUNT + " 张图片"));
        }
        if (teamId < 0) {
            return Mono.just(ResultVO.fail("球队社区ID不正确"));
        }
        String userId = loginUser.getUserId();
        Moment moment = buildMoment(content, teamId, loginUser);
        Mono<MomentInfo> action = momentRepository.save(moment)
                .flatMap(saved -> momentImageRepository
                        .saveAll(buildImages(images, saved.getId(), loginUser))
                        .then(Mono.just(saved)))
                .map(saved -> toMomentInfo(saved, images, Set.of()));
        return txOperator.transactional(action)
                .map(info -> ResultVO.ok(PublishMomentRes.newBuilder().setMoment(info).build()))
                .onErrorResume(e -> {
                    log.error("发布动态失败: userId={}, {}", userId, e.getMessage());
                    return Mono.just(ResultVO.fail("发布动态失败"));
                });
    }

    @Override
    public Mono<ResultVO<GetMomentPageRes>> getMomentPage(GetMomentPageReq req) {
        int teamId = req.getTeamId();
        if (teamId < 0) {
            return Mono.just(ResultVO.fail("球队社区ID不正确"));
        }
        int pageSize = resolvePageSize(req.getPageSize());
        long offset = (long) (Math.max(req.getCurrentPage(), 1) - 1) * pageSize;
        String userId = StringUtils.trimToEmpty(req.getUserId());
        String viewerId = StringUtils.trimToEmpty(req.getViewerId());
        return momentRepository.countByFilter(userId, teamId)
                .flatMap(total -> momentRepository.pageByFilter(userId, teamId, pageSize, offset)
                        .collectList()
                        .flatMap(moments -> buildPage(total, moments, viewerId)))
                .map(ResultVO::ok)
                .onErrorResume(e -> {
                    log.error("获取动态列表失败:", e);
                    return Mono.just(ResultVO.fail("获取动态列表失败"));
                });
    }

    @Override
    public Mono<ResultVO<Void>> deleteMoment(DeleteMomentReq req) {
        if (req.getMomentId() <= 0) {
            return Mono.just(ResultVO.fail("动态ID不能为空"));
        }
        String userId = req.getLoginUser().getUserId();
        Mono<Long> action = momentRepository.softDelete(req.getMomentId(), userId)
                .flatMap(rows -> rows > 0
                        ? momentImageRepository.softDeleteByMomentId(req.getMomentId(), userId).thenReturn(rows)
                        : Mono.just(0L));
        return txOperator.transactional(action)
                .map(rows -> rows > 0 ? ResultVO.<Void>ok() : ResultVO.<Void>fail("动态不存在或无权删除"))
                .onErrorResume(e -> {
                    log.error("删除动态失败: momentId={}", req.getMomentId(), e);
                    return Mono.just(ResultVO.fail("删除动态失败"));
                });
    }

    @Override
    public Mono<ResultVO<ToggleLikeRes>> toggleLike(ToggleLikeReq req) {
        long momentId = req.getMomentId();
        if (momentId <= 0) {
            return Mono.just(ResultVO.fail("动态ID不能为空"));
        }
        String userId = req.getLoginUser().getUserId();
        String userName = StringUtils.trimToEmpty(req.getLoginUser().getUsername());
        return momentRepository.findById(momentId)
                .filter(moment -> Integer.valueOf(0).equals(moment.getIsDeleted()))
                .flatMap(_ -> {
                    Mono<Boolean> action = momentLikeRepository.toggle(momentId, userId, userName)
                            .then(momentRepository.refreshLikeCount(momentId))
                            .then(momentLikeRepository.findByMomentIdAndUserId(momentId, userId))
                            .map(like -> Integer.valueOf(0).equals(like.getIsDeleted()));
                    return txOperator.transactional(action)
                            .flatMap(liked -> momentLikeRepository
                                    .countByMomentIdAndIsDeleted(momentId, 0)
                                    .map(count -> ResultVO.ok(ToggleLikeRes.newBuilder()
                                            .setLiked(liked)
                                            .setLikeCount(Math.toIntExact(count))
                                            .build())));
                })
                .switchIfEmpty(Mono.just(ResultVO.<ToggleLikeRes>fail("动态不存在")))
                .onErrorResume(e -> {
                    log.error("点赞操作失败: momentId={}, {}", momentId, e.getMessage());
                    return Mono.just(ResultVO.fail("点赞操作失败"));
                });
    }

    @Override
    public Mono<ResultVO<GetCommentPageRes>> getCommentPage(GetCommentPageReq req) {
        long momentId = req.getMomentId();
        if (momentId <= 0) {
            return Mono.just(ResultVO.fail("动态ID不能为空"));
        }
        int pageSize = resolvePageSize(req.getPageSize());
        long offset = (long) (Math.max(req.getCurrentPage(), 1) - 1) * pageSize;
        return Mono.zip(
                        momentCommentRepository.countByMomentIdAndIsDeleted(momentId, 0),
                        momentCommentRepository.countByMomentIdAndParentIdAndIsDeleted(momentId, 0L, 0))
                .flatMap(counts -> momentCommentRepository.pageTopLevelByMomentId(momentId, pageSize, offset)
                        .collectList()
                        .flatMap(parents -> momentCommentRepository.findAllRepliesByMomentId(momentId)
                                .collectMultimap(MomentComment::getParentId)
                                .map(repliesByParent -> ResultVO.ok(GetCommentPageRes.newBuilder()
                                        .setTotal(counts.getT1())
                                        .setTopLevelTotal(counts.getT2())
                                        .addAllComments(toCommentInfos(parents, repliesByParent))
                                        .build()))))
                .onErrorResume(e -> {
                    log.error("获取评论列表失败: momentId={}, {}", momentId, e.getMessage());
                    return Mono.just(ResultVO.fail("获取评论列表失败"));
                });
    }

    @Override
    public Mono<ResultVO<AddCommentRes>> addComment(AddCommentReq req) {
        long momentId = req.getMomentId();
        if (momentId <= 0) {
            return Mono.just(ResultVO.fail("动态ID不能为空"));
        }
        long parentId = req.getParentId();
        if (parentId < 0) {
            return Mono.just(ResultVO.fail("父评论ID不正确"));
        }
        String content = StringUtils.trimToEmpty(req.getContent());
        if (content.isEmpty()) {
            return Mono.just(ResultVO.fail("评论内容不能为空"));
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            return Mono.just(ResultVO.fail("评论内容不能超过 " + MAX_COMMENT_LENGTH + " 字"));
        }
        LoginUser loginUser = req.getLoginUser();
        return momentRepository.findById(momentId)
                .filter(moment -> Integer.valueOf(0).equals(moment.getIsDeleted()))
                .flatMap(ignored -> {
                    if (parentId == 0) {
                        return saveComment(momentId, content, loginUser, 0L, "", "");
                    }
                    return momentCommentRepository.findById(parentId)
                            .filter(parent -> Integer.valueOf(0).equals(parent.getIsDeleted())
                                    && Objects.equals(parent.getMomentId(), momentId))
                            .flatMap(parent -> {
                                if (Objects.equals(parent.getUserId(), loginUser.getUserId())) {
                                    return Mono.just(ResultVO.<AddCommentRes>fail("不能回复自己的评论"));
                                }
                                long rootId = defaultLong(parent.getParentId()) > 0
                                        ? parent.getParentId() : parentId;
                                return saveComment(momentId, content, loginUser, rootId,
                                        defaultString(parent.getUserId()),
                                        defaultString(parent.getUserName()));
                            })
                            .switchIfEmpty(Mono.just(ResultVO.<AddCommentRes>fail("被回复的评论不存在")));
                })
                .switchIfEmpty(Mono.just(ResultVO.<AddCommentRes>fail("动态不存在")))
                .onErrorResume(e -> {
                    log.error("发表评论失败: momentId={}, {}", momentId, e.getMessage());
                    return Mono.just(ResultVO.fail("发表评论失败"));
                });
    }

    private Mono<ResultVO<AddCommentRes>> saveComment(long momentId, String content,
                                                      LoginUser loginUser, long parentId,
                                                      String replyToUserId, String replyToUserName) {
        MomentComment comment = buildComment(momentId, content, loginUser, parentId,
                replyToUserId, replyToUserName);
        Mono<MomentComment> action = momentCommentRepository.save(comment)
                .flatMap(saved -> momentRepository.refreshCommentCount(momentId)
                        .thenReturn(saved));
        return txOperator.transactional(action)
                .flatMap(saved -> momentCommentRepository
                        .countByMomentIdAndIsDeleted(momentId, 0)
                        .map(count -> ResultVO.ok(AddCommentRes.newBuilder()
                                .setComment(toCommentInfo(saved))
                                .setCommentCount(Math.toIntExact(count))
                                .build())));
    }

    private static MomentComment buildComment(long momentId, String content, LoginUser loginUser,
                                              long parentId, String replyToUserId,
                                              String replyToUserName) {
        LocalDateTime now = LocalDateTime.now();
        String userId = loginUser.getUserId();
        String username = loginUser.getUsername();
        MomentComment comment = new MomentComment();
        comment.setMomentId(momentId);
        comment.setUserId(userId);
        comment.setUserName(username);
        comment.setContent(content);
        comment.setParentId(parentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setReplyToUserName(replyToUserName);
        comment.setCreateUser(userId);
        comment.setCreateTime(now);
        comment.setUpdateUser(userId);
        comment.setUpdateTime(now);
        comment.setIsDeleted(0);
        return comment;
    }

    @Override
    public Mono<ResultVO<GetMomentDetailRes>> getMomentDetail(GetMomentDetailReq req) {
        long momentId = req.getMomentId();
        if (momentId <= 0) {
            return Mono.just(ResultVO.fail("动态ID不能为空"));
        }
        String viewerId = StringUtils.trimToEmpty(req.getViewerId());
        return momentRepository.findById(momentId)
                .filter(moment -> Integer.valueOf(0).equals(moment.getIsDeleted()))
                .flatMap(moment -> {
                    Mono<List<String>> imagesMono = momentImageRepository
                            .findByMomentIdInOrderBySortAscIdAsc(List.of(momentId))
                            .map(MomentImage::getUrl)
                            .collectList();
                    Mono<List<SocialUser>> shareUsersMono = momentShareRepository
                            .findShareUsers(momentId, MAX_SHARE_USERS)
                            .map(share -> socialUser(share.getUserId(), share.getUserName()))
                            .collectList();
                    return Mono.zip(imagesMono, shareUsersMono,
                                    likedMomentIds(viewerId, List.of(momentId)))
                            .map(tuple -> ResultVO.ok(GetMomentDetailRes.newBuilder()
                                    .setMoment(toMomentInfo(moment, tuple.getT1(), tuple.getT3()))
                                    .addAllShareUsers(tuple.getT2())
                                    .build()));
                })
                .switchIfEmpty(Mono.just(ResultVO.<GetMomentDetailRes>fail("动态不存在")))
                .onErrorResume(e -> {
                    log.error("获取动态详情失败: momentId={}, {}", momentId, e.getMessage());
                    return Mono.just(ResultVO.fail("获取动态详情失败"));
                });
    }

    @Override
    public Mono<ResultVO<ShareMomentRes>> shareMoment(ShareMomentReq req) {
        long momentId = req.getMomentId();
        if (momentId <= 0) {
            return Mono.just(ResultVO.fail("动态ID不能为空"));
        }
        LoginUser loginUser = req.getLoginUser();
        String userId = loginUser.getUserId();
        String userName = StringUtils.trimToEmpty(loginUser.getUsername());
        return momentRepository.findById(momentId)
                .filter(moment -> Integer.valueOf(0).equals(moment.getIsDeleted()))
                .flatMap(moment -> txOperator.transactional(
                                momentShareRepository.upsert(momentId, userId, userName)
                                        .then(momentRepository.refreshShareCount(momentId)))
                        .then(momentShareRepository.countByMomentIdAndIsDeleted(momentId, 0))
                        .map(count -> ResultVO.ok(ShareMomentRes.newBuilder()
                                .setShareCount(Math.toIntExact(count))
                                .build())))
                .switchIfEmpty(Mono.just(ResultVO.<ShareMomentRes>fail("动态不存在")))
                .onErrorResume(e -> {
                    log.error("分享动态失败: momentId={}, {}", momentId, e.getMessage());
                    return Mono.just(ResultVO.fail("分享动态失败"));
                });
    }

    private static Moment buildMoment(String content, int teamId, LoginUser loginUser) {
        LocalDateTime now = LocalDateTime.now();
        String userId = loginUser.getUserId();
        String username = loginUser.getUsername();
        Moment moment = new Moment();
        moment.setContent(content);
        moment.setTeamId(teamId);
        moment.setLikeCount(0);
        moment.setCommentCount(0);
        moment.setShareCount(0);
        moment.setUserId(userId);
        moment.setUserName(StringUtils.trimToEmpty(username));
        moment.setCreateUser(userId);
        moment.setCreateTime(now);
        moment.setUpdateUser(userId);
        moment.setUpdateTime(now);
        moment.setIsDeleted(0);
        return moment;
    }

    private static List<MomentImage> buildImages(List<String> images, Long momentId,
                                                 LoginUser loginUser) {
        LocalDateTime now = LocalDateTime.now();
        String userId = loginUser.getUserId();
        return IntStream.range(0, images.size())
                .mapToObj(index -> {
                    MomentImage image = new MomentImage();
                    image.setMomentId(momentId);
                    image.setUrl(images.get(index));
                    image.setSort(index);
                    image.setCreateUser(userId);
                    image.setCreateTime(now);
                    image.setUpdateUser(userId);
                    image.setUpdateTime(now);
                    image.setIsDeleted(0);
                    return image;
                })
                .toList();
    }

    private Mono<GetMomentPageRes> buildPage(long total, List<Moment> moments, String viewerId) {
        if (moments.isEmpty()) {
            return Mono.just(GetMomentPageRes.newBuilder().setTotal(total).build());
        }
        List<Long> momentIds = moments.stream()
                .map(Moment::getId)
                .filter(Objects::nonNull)
                .toList();
        Mono<Map<Long, List<String>>> imagesMono = momentImageRepository
                .findByMomentIdInOrderBySortAscIdAsc(momentIds)
                .collect(Collectors.groupingBy(MomentImage::getMomentId,
                        Collectors.mapping(MomentImage::getUrl, Collectors.toList())));
        Mono<Set<Long>> likedMono = likedMomentIds(viewerId, momentIds);
        return Mono.zip(imagesMono, likedMono)
                .map(tuple -> GetMomentPageRes.newBuilder()
                        .setTotal(total)
                        .addAllMoments(moments.stream()
                                .map(moment -> toMomentInfo(moment,
                                        imageUrls(tuple.getT1(), moment.getId()), tuple.getT2()))
                                .toList())
                        .build());
    }

    private static List<String> imageUrls(Map<Long, List<String>> imagesByMoment, Long momentId) {
        return imagesByMoment.getOrDefault(momentId, List.of());
    }

    private Mono<Set<Long>> likedMomentIds(String viewerId, List<Long> momentIds) {
        if (StringUtils.isBlank(viewerId)) {
            return Mono.just(Set.of());
        }
        return momentLikeRepository.findByMomentIdInAndUserIdAndIsDeleted(momentIds, viewerId, 0)
                .map(MomentLike::getMomentId)
                .collect(Collectors.toSet());
    }

    private static MomentInfo toMomentInfo(Moment moment, List<String> images, Set<Long> likedIds) {
        return MomentInfo.newBuilder()
                .setId(defaultLong(moment.getId()))
                .setContent(defaultString(moment.getContent()))
                .addAllImages(images)
                .setUserId(defaultString(moment.getUserId()))
                .setUserName(defaultString(moment.getUserName()))
                .setCreateTime(formatDateTime(moment.getCreateTime()))
                .setTeamId(defaultInt(moment.getTeamId()))
                .setLikeCount(defaultInt(moment.getLikeCount()))
                .setCommentCount(defaultInt(moment.getCommentCount()))
                .setShareCount(defaultInt(moment.getShareCount()))
                .setLiked(likedIds.contains(moment.getId()))
                .build();
    }

    private static List<CommentInfo> toCommentInfos(List<MomentComment> parents,
                                                    Map<Long, Collection<MomentComment>> repliesByParent) {
        return parents.stream()
                .map(parent -> toCommentInfo(parent,
                        repliesByParent.getOrDefault(parent.getId(), List.of())))
                .toList();
    }

    private static CommentInfo toCommentInfo(MomentComment comment) {
        return toCommentInfo(comment, List.of());
    }

    private static CommentInfo toCommentInfo(MomentComment comment, Collection<MomentComment> replies) {
        CommentInfo.Builder builder = CommentInfo.newBuilder()
                .setId(defaultLong(comment.getId()))
                .setMomentId(defaultLong(comment.getMomentId()))
                .setUserId(defaultString(comment.getUserId()))
                .setUserName(defaultString(comment.getUserName()))
                .setContent(defaultString(comment.getContent()))
                .setCreateTime(formatDateTime(comment.getCreateTime()))
                .setParentId(defaultLong(comment.getParentId()))
                .setReplyToUserId(defaultString(comment.getReplyToUserId()))
                .setReplyToUserName(defaultString(comment.getReplyToUserName()));
        replies.forEach(reply -> builder.addReplies(toCommentInfo(reply)));
        return builder.build();
    }

    private static SocialUser socialUser(String userId, String userName) {
        return SocialUser.newBuilder()
                .setUserId(defaultString(userId))
                .setUserName(defaultString(userName))
                .build();
    }

    private static int resolvePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
