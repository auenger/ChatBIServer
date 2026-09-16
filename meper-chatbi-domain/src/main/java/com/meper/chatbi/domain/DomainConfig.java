package com.meper.chatbi.domain;

import com.meper.chatbi.jdbc.ConnectionPoolRegistry;
import com.meper.chatbi.jdbc.JdbcSqlExecutor;
import com.meper.chatbi.query.SqlClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 领域装配：连接器与执法组件的 bean 定义。 */
@Configuration
public class DomainConfig {

    @Bean
    public ConnectionPoolRegistry connectionPoolRegistry(
            @Value("${meper.workbench.pool-max-size:5}") int poolMaxSize) {
        return new ConnectionPoolRegistry(poolMaxSize);
    }

    @Bean
    public JdbcSqlExecutor jdbcSqlExecutor() {
        return new JdbcSqlExecutor();
    }

    @Bean
    public SqlClassifier sqlClassifier() {
        return new SqlClassifier();
    }
}
