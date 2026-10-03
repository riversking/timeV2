package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.rivers.core.util.HttpClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.GetRefereesReq;
import com.rivers.nba.GetRefereesRes;
import com.rivers.nba.RefereeInfo;
import com.rivers.nba.entity.Referee;
import com.rivers.nba.repository.RefereeRepository;
import com.rivers.nba.service.IRefereeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * <p>
 * 裁判信息表 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Slf4j
@Service
public class RefereeServiceImpl implements IRefereeService {

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final RefereeRepository refereeRepository;

    public RefereeServiceImpl(RefereeRepository refereeRepository) {
        this.refereeRepository = refereeRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncReferees() {
        return refereeRepository.countByName("")
                .flatMap(count -> {
                    if (count > 0) {
                        return refereeRepository.logicDeleteAll().then();
                    }
                    return Mono.empty();
                })
                .then(Mono.fromFuture(() -> HttpClientUtil.getAsync(nbaUrl + "Referees?key=" + nbaKey)))
                .map(result -> {
                    log.info("referees result length:{}", StringUtils.length(result));
                    return Optional.ofNullable(result)
                            .map(i -> {
                                List<Referee> referees = JSON.parseArray(i, Referee.class);
                                return referees.stream()
                                        .map(r -> {
                                            Referee referee = new Referee();
                                            BeanUtils.copyProperties(r, referee);
                                            referee.setCreateUser("system");
                                            referee.setUpdateUser("system");
                                            return referee;
                                        })
                                        .toList();
                            }).orElse(null);
                })
                .flatMap(referees -> {
                    if (CollectionUtils.isEmpty(referees)) {
                        return Mono.just(ResultVO.ok());
                    }
                    return refereeRepository.saveAll(referees).then(Mono.just(ResultVO.ok()));
                });
    }

    @Override
    public Mono<ResultVO<GetRefereesRes>> getRefereePage(GetRefereesReq req) {
        int currentPage = req.getCurrentPage();
        int pageSize = req.getPageSize();
        String name = StringUtils.defaultIfBlank(req.getName(), "");
        long offset = (long) (currentPage - 1) * pageSize;
        return refereeRepository.countByName(name)
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(GetRefereesRes.newBuilder().getDefaultInstanceForType()));
                    }
                    return refereeRepository.pageByName(name, pageSize, offset)
                            .map(r -> RefereeInfo.newBuilder()
                                    .setRefereeId(defaultLong(r.getRefereeId()))
                                    .setName(defaultString(r.getName()))
                                    .setNumber(defaultInt(r.getNumber()))
                                    .setPosition(defaultString(r.getPosition()))
                                    .setCollege(defaultString(r.getCollege()))
                                    .setExperience(defaultInt(r.getExperience()))
                                    .build())
                            .collectList()
                            .map(list -> GetRefereesRes.newBuilder()
                                    .setTotal(total)
                                    .addAllReferees(list)
                                    .build())
                            .map(ResultVO::ok);
                });
    }

    /**
     * 字符串空值用默认值替换（null 转空串）
     */
    private static String defaultString(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }

    /**
     * 整数空值用默认值替换（null 转 0）
     */
    private static int defaultInt(Integer value) {
        if (value == null) {
            return 0;
        }
        return value;
    }

    /**
     * 长整数空值用默认值替换（null 转 0）
     */
    private static long defaultLong(Long value) {
        if (value == null) {
            return 0L;
        }
        return value;
    }
}
