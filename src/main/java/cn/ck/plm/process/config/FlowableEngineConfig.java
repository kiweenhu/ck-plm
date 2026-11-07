/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.process.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flowable 引擎调优 —— 覆盖 starter 默认值中不适合 PLM 服务端的项。
 *
 * <h3>为什么关闭「部署时生成流程图」</h3>
 * <p>Flowable 默认 {@code createDiagramOnDeploy=true}：部署 BPMN 时用 java.awt
 * （Graphics2D/Font）给每个流程定义自动渲染一张 PNG 存进 ACT_GE_BYTEARRAY。
 * 服务器环境通常<b>没有安装字体</b>（尤其容器/最小化系统），会抛
 * {@code RuntimeException: Fontconfig head is null, check your fonts or fonts configuration}——
 * 行业包安装器部署流程资源、以及 {@code ProcessDeploymentSupport} 按租户部署内置流程
 * 都会因此失败。
 *
 * <p>而本项目前端流程图用的是 BPMN 渲染库（bpmn-js）直接画 XML，根本不消费这张 PNG；
 * 流程监控的节点高亮走 history 接口 + 前端叠加，也不依赖它。关掉只有收益没有损失。
 * 若某部署环境确需该图片（如外部系统集成导出），给服务器装字体即可
 * （{@code dnf install -y fontconfig dejavu-sans-fonts}），无需改回此开关。
 */
@Configuration
public class FlowableEngineConfig {

    private static final Logger log = LoggerFactory.getLogger(FlowableEngineConfig.class);

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableEngineTuning() {
        return engineConfiguration -> {
            engineConfiguration.setCreateDiagramOnDeploy(false);
            log.info("Flowable 引擎调优：createDiagramOnDeploy=false（无字体环境部署 BPMN 不再触发 Fontconfig 崩溃，前端渲染不依赖该图）");
        };
    }
}
