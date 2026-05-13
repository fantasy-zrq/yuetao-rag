package com.rag.cn.yuetaoragbackend.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.rag.cn.yuetaoragbackend.framework.database.MyMetaObjectHandler;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;


/**
 * 数据库持久层配置类
 * 配置 MyBatis-Plus 相关分页插件等
 */
@Configuration
public class DataBaseConfiguration {

    /**
     * MyBatis-Plus PostgreSQL 分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    /**
     * MyBatis-Plus 源数据自动填充类
     */
    @Bean
    public MetaObjectHandler myMetaObjectHandler() {
        return new MyMetaObjectHandler();
    }

    @Bean
    public ApplicationRunner intentNodeSchemaPatchRunner(JdbcTemplate jdbcTemplate) {
        return args -> jdbcTemplate.execute("""
                ALTER TABLE public.t_intent_node
                ADD COLUMN IF NOT EXISTS kb_id int8
                """);
    }
}
