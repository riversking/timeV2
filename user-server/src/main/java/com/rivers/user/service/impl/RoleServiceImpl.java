package com.rivers.user.service.impl;

import com.rivers.user.mapper.TimerRoleMapper;
import com.rivers.user.service.IRoleService;
import org.springframework.stereotype.Service;

@Service
public class RoleServiceImpl implements IRoleService {

    private final TimerRoleMapper timerRoleMapper;

    public RoleServiceImpl(TimerRoleMapper timerRoleMapper) {
        this.timerRoleMapper = timerRoleMapper;
    }
}
