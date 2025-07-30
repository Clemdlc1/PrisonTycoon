package fr.prisontycoon.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.prisontycoon.PrisonTycoon;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private final PrisonTycoon plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        setupDataSource();
    }

    private void setupDataSource() {
        ConfigManager configManager = plugin.getConfigManager();
        String databaseType = configManager.get("database.type", "YAML");

        if (!"POSTGRESQL".equalsIgnoreCase(databaseType)) {
            plugin.getLogger().info("Database type is not PostgreSQL. Skipping connection pool setup.");
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + configManager.get("database.postgresql.host", "localhost") + ":" + configManager.get("database.postgresql.port", 5432) + "/" + configManager.get("database.postgresql.name", "prisontycoon"));
        config.setUsername(configManager.get("database.postgresql.user", "user"));
        config.setPassword(configManager.get("database.postgresql.password", "password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        config.setDriverClassName("org.postgresql.Driver");

        dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
