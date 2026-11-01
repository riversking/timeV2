package com.rivers.nba.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rivers.core.client.WebClientUtil;
import com.rivers.core.vo.ResultVO;
import com.rivers.nba.DepthChartInfo;
import com.rivers.nba.GetDepthChartsReq;
import com.rivers.nba.GetDepthChartsRes;
import com.rivers.nba.entity.DepthChart;
import com.rivers.nba.repository.DepthChartRepository;
import com.rivers.nba.service.IDepthChartService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.rivers.core.util.DateTimeUtils.formatDateTime;
import static com.rivers.core.util.ValueUtils.defaultInt;
import static com.rivers.core.util.ValueUtils.defaultLong;
import static com.rivers.core.util.ValueUtils.defaultString;

/**
 * <p>
 * 阵容深度表 服务实现类
 * </p>
 *
 * @author rivers
 * @since 2026-10-02
 */
@Slf4j
@Service
public class DepthChartServiceImpl implements IDepthChartService {

    @Value("${nba.key}")
    private String nbaKey;

    @Value("${nba.url}")
    private String nbaUrl;

    private final DepthChartRepository depthChartRepository;

    private final WebClientUtil webClientUtil;

    public DepthChartServiceImpl(DepthChartRepository depthChartRepository, WebClientUtil webClientUtil) {
        this.depthChartRepository = depthChartRepository;
        this.webClientUtil = webClientUtil;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Mono<ResultVO<Void>> syncDepthCharts() {
        return depthChartRepository.logicDeleteAll()
                .then(webClientUtil.get(nbaUrl + "DepthCharts?key=" + nbaKey))
                .map(result -> {
                    log.info("depth charts result length:{}", result.length());
                    return Optional.of(result)
                            .map(i -> {
                                List<DepthChart> list = new ArrayList<>();
                                // 外层数组：每队一个对象，嵌套 DepthCharts 子数组
                                JSONArray teamArray = JSON.parseArray(i);
                                for (int t = 0; t < teamArray.size(); t++) {
                                    JSONObject teamObj = teamArray.getJSONObject(t);
                                    Integer teamId = teamObj.getInteger("TeamID");
                                    JSONArray charts = teamObj.getJSONArray("DepthCharts");
                                    if (charts == null) {
                                        continue;
                                    }
                                    for (int c = 0; c < charts.size(); c++) {
                                        DepthChart dc = JSON.parseObject(charts.getJSONObject(c).toJSONString(),
                                                DepthChart.class);
                                        DepthChart entity = new DepthChart();
                                        BeanUtils.copyProperties(dc, entity);
                                        entity.setTeamId(teamId);
                                        entity.setCreateUser("system");
                                        entity.setUpdateUser("system");
                                        list.add(entity);
                                    }
                                }
                                return list;
                            }).orElse(null);
                })
                .flatMap(list -> {
                    if (CollectionUtils.isEmpty(list)) {
                        return Mono.just(ResultVO.ok());
                    }
                    return depthChartRepository.saveAll(list).then(Mono.just(ResultVO.ok()));
                });
    }

    @Override
    public Mono<ResultVO<GetDepthChartsRes>> getDepthChartPage(GetDepthChartsReq req) {
        int currentPage = req.getCurrentPage();
        int pageSize = req.getPageSize();
        int teamId = req.getTeamId();
        long offset = (long) (currentPage - 1) * pageSize;
        Mono<Long> countMono = depthChartRepository.countAll();
        if (teamId > 0) {
            countMono = depthChartRepository.countByTeam(teamId);
        }
        return countMono
                .flatMap(total -> {
                    if (total == 0) {
                        return Mono.just(ResultVO.ok(GetDepthChartsRes.newBuilder().getDefaultInstanceForType()));
                    }
                    return getPageFlux(teamId, pageSize, offset)
                            .map(d -> DepthChartInfo.newBuilder()
                                    .setTeamId(defaultInt(d.getTeamId()))
                                    .setPlayerId(defaultLong(d.getPlayerId()))
                                    .setName(defaultString(d.getName()))
                                    .setPositionCategory(defaultString(d.getPositionCategory()))
                                    .setPosition(defaultString(d.getPosition()))
                                    .setDepthOrder(defaultInt(d.getDepthOrder()))
                                    .setDepthChartId(defaultInt(d.getDepthChartId()))
                                    .setUpdated(formatDateTime(d.getUpdated()))
                                    .build())
                            .collectList()
                            .map(list -> GetDepthChartsRes.newBuilder()
                                    .setTotal(total)
                                    .addAllDepthCharts(list)
                                    .build())
                            .map(ResultVO::ok);
                });
    }

    /**
     * 按条件选择分页查询流（teamId > 0 按球队过滤，否则全量）
     */
    private Flux<DepthChart> getPageFlux(int teamId, int pageSize, long offset) {
        if (teamId > 0) {
            return depthChartRepository.pageByTeam(teamId, pageSize, offset);
        }
        return depthChartRepository.pageAll(pageSize, offset);
    }
}
