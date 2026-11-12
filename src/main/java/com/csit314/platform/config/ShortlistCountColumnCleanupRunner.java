package com.csit314.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// IDR-04 - Project.shortlistCount used to be a real column (shortlist_count, NOT NULL,
// no default) until it became a @Formula in ded8489. ddl-auto=update adds new columns but
// never drops old ones, so a MySQL database created before that change still has it -
// and since Hibernate no longer lists it in the INSERT, MySQL rejects every new project
// with "Field 'shortlist_count' doesn't have a default value" (shown to the designer as
// the generic system error). Editing kept working because an UPDATE never touches it.
// Nothing reads the column any more - the count is live from the shortlists table - so
// it's dropped here. A database created after that change never had it and is left alone
@Component
public class ShortlistCountColumnCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ShortlistCountColumnCleanupRunner.class);
    private static final String COLUMN = "shortlist_count";

    private final DataSource dataSource;

    public ShortlistCountColumnCleanupRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        // same as DemoDataImportRunner - an exception here would stop the whole app from
        // starting, which is worse than the one broken form this is fixing
        try (Connection connection = dataSource.getConnection()) {
            if (!hasLeftoverColumn(connection)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("alter table projects drop column " + COLUMN);
            }
            log.info("Dropped the leftover projects.{} column - new projects can be saved again.", COLUMN);
        } catch (SQLException e) {
            log.error("Couldn't drop the leftover projects.{} column, so creating a project will keep failing. "
                    + "Run \"ALTER TABLE projects DROP COLUMN {};\" on the database by hand.", COLUMN, COLUMN, e);
        }
    }

    private boolean hasLeftoverColumn(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        // MySQL keeps the lowercase name Hibernate created; H2 (tests) upper-cases it
        String table = metaData.storesUpperCaseIdentifiers() ? "PROJECTS" : "projects";
        try (ResultSet columns = metaData.getColumns(connection.getCatalog(), connection.getSchema(), table, null)) {
            while (columns.next()) {
                if (COLUMN.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
