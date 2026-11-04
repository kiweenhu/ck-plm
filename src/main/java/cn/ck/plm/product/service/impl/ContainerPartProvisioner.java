/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.service.api.PartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 系列 / 型号创建时，顺带建一个<b>同名的 Part 主对象</b>（类型 {@code PART}）。
 *
 * <h3>为什么要这一步</h3>
 * BOM 的父件只能是一个 Part：没有容器级 Part，"这个产品用了哪些料"就无处挂载 ——
 * 用户得先手工建一个和系列同名的件，才能开始搭结构。这里把它变成建容器的副产品。
 *
 * <h3>口径</h3>
 * <ul>
 *   <li>{@code container_oid} = 系列 / 型号<b>自身</b>的 oid，{@code container_type} 如实标注
 *       （{@code PRODUCT_LINE} / {@code PRODUCT_MODEL}）—— 容器与件的归属要一一对应；</li>
 *   <li>{@code type_definition_code} = {@code PART}（根类型，不是某个软类型）：
 *       用户没选类型，就不该替他把件钉死成"结构件 / 电气件"；</li>
 *   <li>{@code number} 交给编号服务生成，{@code name} 与容器同名 —— 用户在列表里一眼能对上；</li>
 *   <li>{@code unit} 默认 {@code ea}（"个"）：装配根按个数计，与 BOM 行"未指定就用 ea"同一口径；</li>
 *   <li>{@code folder_oid} 留空：这是容器级主数据，不是某个阶段文件夹里的交付物。
 *       塞进"市场评估"之类的文件夹只会让研发资料列表凭空多一条。</li>
 * </ul>
 *
 * <h3>失败策略</h3>
 * 只记日志、<b>不阻断</b>系列 / 型号创建。Part 是附属物，编号服务或版本规则偶发异常时，
 * 不该让用户连产品都建不出来（代价是这条容器暂时没有同名件，补建一次即可）。
 */
@Component
public class ContainerPartProvisioner {

    private static final Logger log = LoggerFactory.getLogger(ContainerPartProvisioner.class);

    /** 部件根类型编码（ck_type_definition.root_type_code = PART 的那个根节点） */
    private static final String ROOT_PART_TYPE = "PART";

    /**
     * 同名件的默认计量单位：{@code ea}（单位字典里的"个"）。
     *
     * <p>与 BOM 行"未指定就用 ea"同一口径（见 {@code BomLinksServiceImpl#create}）：
     * 系列 / 型号的同名件是装配根，按个数计；不给默认值的话详情页「单位」是空的，
     * 后续拿它当父件搭 BOM 时用量也没有可用的单位。
     */
    private static final String DEFAULT_UNIT = "ea";

    /** ck_part.container_type 的取值：归属产品系列。注意别用 Stage.OWNER_TYPE_LINE —— 那个值是 "LINE" */
    public static final String CONTAINER_LINE = "PRODUCT_LINE";

    /** ck_part.container_type 的取值：归属产品型号 */
    public static final String CONTAINER_MODEL = "PRODUCT_MODEL";

    private final PartService partService;

    public ContainerPartProvisioner(PartService partService) {
        this.partService = partService;
    }

    /**
     * 为容器建同名 Part。
     *
     * @param containerName 系列 / 型号名称，同时作为 Part 名称
     * @param containerOid  系列 / 型号自身 oid
     * @param containerType {@code PRODUCT_LINE} / {@code PRODUCT_MODEL}
     * @param stageOid      容器初始化出来的第一个阶段（概念阶段），找不到时为 null
     * @param creator       创建人 username（Part 的初始迭代也记这个）
     */
    public void provision(String containerName, String containerOid, String containerType,
                          String stageOid, String creator) {
        try {
            Part part = new Part();
            part.setName(containerName);
            part.setTypeDefinitionCode(ROOT_PART_TYPE);
            part.setContainerOid(containerOid);
            part.setContainerType(containerType);
            part.setStageOid(stageOid);
            part.setCreator(creator);
            Part created = partService.create(part, null, null, DEFAULT_UNIT, null);
            log.info("容器已生成同名 Part: container={}({}), partOid={}, number={}",
                    containerOid, containerType, created.getOid(), created.getNumber());
        } catch (Exception e) {
            log.error("容器同名 Part 创建失败（不影响容器本身）: container={}({}), name={}, error={}",
                    containerOid, containerType, containerName, e.getMessage(), e);
        }
    }
}
