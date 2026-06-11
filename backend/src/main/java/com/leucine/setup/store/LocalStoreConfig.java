package com.leucine.setup.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@EnableConfigurationProperties(LocalStoreProperties.class)
public class LocalStoreConfig {

  private static final Logger log = LoggerFactory.getLogger(LocalStoreConfig.class);

  /**
   * Local SQLite store for the tool's own state (connection configs, audit log).
   * NOT a target Postgres — never use this DataSource for streem-backend writes.
   */
  @Bean(name = "localStoreDataSource")
  public DataSource localStoreDataSource(LocalStoreProperties props) throws Exception {
    Path dbPath = Paths.get(props.path());
    Files.createDirectories(dbPath.getParent());
    log.info("Local store path: {}", dbPath.toAbsolutePath());

    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.sqlite.JDBC");
    ds.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
    return ds;
  }

  /** Runs schema.sql on boot. Idempotent (CREATE TABLE IF NOT EXISTS). */
  @Bean
  public DataSourceInitializer localStoreInitializer(DataSource localStoreDataSource) {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    populator.addScript(new ClassPathResource("store/schema.sql"));
    populator.setContinueOnError(false);

    DataSourceInitializer init = new DataSourceInitializer();
    init.setDataSource(localStoreDataSource);
    init.setDatabasePopulator(populator);
    return init;
  }

  @Bean(name = "localStoreJdbc")
  public JdbcTemplate localStoreJdbc(DataSource localStoreDataSource) {
    return new JdbcTemplate(localStoreDataSource);
  }
}
