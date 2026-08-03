/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.Unit;
import cn.ck.plm.base.mapper.UnitMapper;
import cn.ck.plm.base.service.api.UnitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UnitServiceImpl implements UnitService {

    private final UnitMapper mapper;

    public UnitServiceImpl(UnitMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Unit create(Unit unit) {
        if (unit.getName() == null || unit.getName().isBlank()) {
            throw new IllegalArgumentException("单位名称（name）不能为空");
        }
        if (mapper.existsByName(unit.getName()) > 0) {
            throw new IllegalArgumentException("单位名称已存在: " + unit.getName());
        }
        unit.setOid(UUID.randomUUID().toString());
        if (unit.getIsSI() == null) unit.setIsSI(false);
        if (unit.getFactor() == null) unit.setFactor(1.0);
        if (unit.getOffset() == null) unit.setOffset(0.0);
        if (unit.getSortOrder() == null) unit.setSortOrder(0);
        mapper.insert(unit);
        return unit;
    }

    @Override
    @Transactional
    public Unit update(String oid, Unit unit) {
        Unit existing = mapper.selectByOid(oid);
        if (existing == null) throw new IllegalArgumentException("单位不存在: " + oid);

        if (unit.getName() != null && !unit.getName().equals(existing.getName())) {
            if (mapper.existsByName(unit.getName()) > 0) {
                throw new IllegalArgumentException("单位名称已存在: " + unit.getName());
            }
            existing.setName(unit.getName());
        }
        if (unit.getDisplay() != null) existing.setDisplay(unit.getDisplay());
        if (unit.getQuantityType() != null) existing.setQuantityType(unit.getQuantityType());
        if (unit.getIsSI() != null) existing.setIsSI(unit.getIsSI());
        if (unit.getBaseUnitName() != null) existing.setBaseUnitName(unit.getBaseUnitName());
        if (unit.getFactor() != null) existing.setFactor(unit.getFactor());
        if (unit.getOffset() != null) existing.setOffset(unit.getOffset());
        if (unit.getSortOrder() != null) existing.setSortOrder(unit.getSortOrder());
        if (unit.getDescription() != null) existing.setDescription(unit.getDescription());

        mapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(String oid) {
        Unit unit = mapper.selectByOid(oid);
        if (unit == null) throw new IllegalArgumentException("单位不存在: " + oid);
        // 检查是否有其他单位以它为基准
        List<Unit> dependents = mapper.selectByBaseUnitName(unit.getName());
        if (dependents != null && !dependents.isEmpty()) {
            throw new IllegalArgumentException(
                    "无法删除该单位，以下单位以其为基准: " +
                    dependents.stream().map(Unit::getName).collect(Collectors.joining(", ")));
        }
        mapper.deleteByOid(oid);
    }

    @Override
    public Unit getByName(String name) {
        return mapper.selectByName(name);
    }

    @Override
    public Unit getByOid(String oid) {
        return mapper.selectByOid(oid);
    }

    @Override
    public List<Unit> listByQuantityType(String quantityType) {
        return mapper.selectByQuantityType(quantityType);
    }

    @Override
    public Map<String, List<Unit>> listAllGrouped() {
        return mapper.selectAll().stream()
                .collect(Collectors.groupingBy(
                        u -> u.getQuantityType() != null ? u.getQuantityType() : "CUSTOM",
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    @Override
    public List<Unit> listAll() {
        return mapper.selectAll();
    }

    @Override
    public double convertFactor(String fromUnitName, String toUnitName) {
        Unit from = mapper.selectByName(fromUnitName);
        Unit to = mapper.selectByName(toUnitName);
        if (from == null) throw new IllegalArgumentException("单位不存在: " + fromUnitName);
        if (to == null) throw new IllegalArgumentException("单位不存在: " + toUnitName);
        if (!Objects.equals(from.getQuantityType(), to.getQuantityType())) {
            throw new IllegalArgumentException(
                    "不同量纲单位不可换算: " + from.getQuantityType() + " → " + to.getQuantityType());
        }
        // 先转到基准，再从基准转到目标
        double toBase = 1.0 / toBaseFactor(to);
        double fromBase = toBaseFactor(from);
        return fromBase * toBase;
    }

    /** 从当前单位到基准的因子：base = value × factor + offset → value = (base - offset) / factor */
    private double toBaseFactor(Unit unit) {
        double factor = unit.getFactor() != null ? unit.getFactor() : 1.0;
        return factor == 0 ? 1.0 : 1.0 / factor;
    }
}
