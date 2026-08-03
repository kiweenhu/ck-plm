/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PLM 数据库类型配置，对应 application.yml 中的 {@code plm.database} 节点。
 *
 * <p>当需要扩展新的数据库支持时（如 MySQL、Oracle），只需：
 * <ol>
 *   <li>在 {@code mapper.impl} 下新增对应 Mapper 实现</li>
 *   <li>在其上添加 {@code @ConditionalOnProperty(name = "plm.database.type", havingValue = "mysql")}</li>
 *   <li>修改配置 {@code plm.database.type: mysql} 即可自动切换</li>
 * </ol>
 */
@Component
@ConfigurationProperties(prefix = "plm.database")
public class DatabaseProperties {

    /** 数据库类型，默认 postgresql */
    private String type = "postgresql";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
