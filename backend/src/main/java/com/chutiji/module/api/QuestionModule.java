package com.chutiji.module.api;

/**
 * 模块契约：整个项目唯一的稳定边界。新增模块 = 新增一个实现类并注册为 Spring Bean。
 */
public interface QuestionModule {

    /** 模块唯一标识，如 "arithmetic"，用于路由与落库。 */
    String key();

    /** 模块元信息：名称、题型、难度轴定义。 */
    ModuleMeta meta();

    /** 按请求出题。返回对象中不得包含答案给到客户端。 */
    GeneratedQuestion generate(GenRequest request);

    /** 服务端判题。答案只在这里被读取。 */
    GradeResult grade(GradeRequest request);
}
