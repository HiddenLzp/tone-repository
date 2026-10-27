package com.lizp.tone.module.system.dept;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.module.system.dept.entity.SysDept;
import com.lizp.tone.module.system.dept.mapper.SysDeptMapper;
import com.lizp.tone.module.system.user.entity.SysUser;
import com.lizp.tone.module.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 部门服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    public List<SysDept> list(String deptName, Integer status) {
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .like(StrUtil.isNotBlank(deptName), SysDept::getDeptName, deptName)
                .eq(status != null, SysDept::getStatus, status)
                .orderByAsc(SysDept::getOrderNum)
                .orderByAsc(SysDept::getId));
    }

    public SysDept getById(Long id) {
        return deptMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(SysDept dept) {
        checkNameUnique(dept.getDeptName(), dept.getParentId(), null);
        buildAncestors(dept);
        deptMapper.insert(dept);
        return dept.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysDept dept) {
        if (dept.getId().equals(dept.getParentId())) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "上级部门不能是自己");
        }
        checkNameUnique(dept.getDeptName(), dept.getParentId(), dept.getId());
        buildAncestors(dept);
        deptMapper.updateById(dept);
        // 父级变了，子孙部门的祖级链也要跟着刷新，否则数据权限查下级会漏
        refreshChildrenAncestors(dept);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id)
                .eq(SysDept::getDelFlag, Constants.DEL_FLAG_NORMAL));
        if (childCount > 0) {
            throw new BaseException(ErrorCode.DEPT_HAS_CHILD);
        }
        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, id)
                .eq(SysUser::getDelFlag, Constants.DEL_FLAG_NORMAL));
        if (userCount > 0) {
            throw new BaseException(ErrorCode.DEPT_HAS_USER);
        }
        deptMapper.deleteById(id);
    }

    /** 批量查部门名称，避免列表页 N+1 */
    public java.util.Map<Long, String> deptNameMap(Set<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return java.util.Map.of();
        }
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .in(SysDept::getId, ids))
                .stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
    }

    private void checkNameUnique(String deptName, Long parentId, Long excludeId) {
        Long count = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getDeptName, deptName)
                .eq(SysDept::getParentId, parentId)
                .ne(excludeId != null, SysDept::getId, excludeId));
        if (count > 0) {
            throw new BaseException(ErrorCode.DATA_EXIST, "同级下已存在同名部门");
        }
    }

    /** 祖级链形如 "0,100,101"，查所有下级时用 FIND_IN_SET 一次搞定，不用递归 */
    private void buildAncestors(SysDept dept) {
        Long parentId = dept.getParentId() == null ? Constants.TOP_PARENT_ID : dept.getParentId();
        if (Constants.TOP_PARENT_ID.equals(parentId)) {
            dept.setParentId(Constants.TOP_PARENT_ID);
            dept.setAncestors("0");
            return;
        }
        SysDept parent = deptMapper.selectById(parentId);
        if (parent == null) {
            throw new BaseException(ErrorCode.DATA_NOT_FOUND, "上级部门不存在");
        }
        dept.setAncestors(parent.getAncestors() + "," + parentId);
    }

    private void refreshChildrenAncestors(SysDept parent) {
        List<SysDept> children = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .apply("FIND_IN_SET({0}, ancestors)", parent.getId()));
        for (SysDept child : children) {
            String newAncestors = parent.getAncestors() + "," + parent.getId();
            if (!newAncestors.equals(child.getAncestors())) {
                child.setAncestors(newAncestors);
                deptMapper.updateById(child);
            }
        }
    }
}
