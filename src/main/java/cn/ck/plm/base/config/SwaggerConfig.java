/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 3 配置。
 *
 * <p>访问地址：
 * <ul>
 *   <li>Swagger UI：<a href="http://localhost:8082/swagger-ui.html">/swagger-ui.html</a></li>
 *   <li>API 文档 JSON：<a href="http://localhost:8082/v3/api-docs">/v3/api-docs</a></li>
 * </ul>
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI jhlcPlmOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JHLC-PLM API")
                        .description("JHLC-PLM 产品生命周期管理系统接口文档")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("UUID")
                                .description("登录接口获取 token，格式：Bearer <token>")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }
}
