package com.csit314.platform.config;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

// IDR-04 - a database created before Project.shortlistCount became a @Formula still has
// the old NOT NULL shortlist_count column, so every new project's INSERT failed while
// edits kept working. Each test gets its own throwaway H2 database instead of the shared
// Spring test one, since it has to put that old column back
class ShortlistCountColumnCleanupRunnerIntegrationTests {

    // the columns that matter, as ddl-auto=update left them on an old database
    private static final String LEGACY_PROJECTS_TABLE = "create table projects (id bigint auto_increment primary key, "
            + "title varchar(150) not null, view_count integer not null, shortlist_count integer not null)";
    private static final String CURRENT_PROJECTS_TABLE = "create table projects (id bigint auto_increment primary key, "
            + "title varchar(150) not null, view_count integer not null)";
    // what Hibernate sends now - shortlist_count isn't a column in the mapping any more
    private static final String NEW_PROJECT_INSERT = "insert into projects (title, view_count) values ('New project', 0)";

    private JdbcDataSource dataSource;

    @BeforeEach
    void freshDatabase() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:shortlist-cleanup-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
    }

    @Test
    void anOldDatabaseRejectsNewProjectsUntilTheLeftoverColumnIsDropped() throws SQLException {
        execute(LEGACY_PROJECTS_TABLE);
        execute("insert into projects (title, view_count, shortlist_count) values ('Existing project', 0, 0)");
        // the bug report: editing an existing project works, creating a new one doesn't
        assertThatCode(() -> execute("update projects set title = 'Edited' where id = 1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> execute(NEW_PROJECT_INSERT))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("SHORTLIST_COUNT");

        new ShortlistCountColumnCleanupRunner(dataSource).run(null);

        assertThat(projectColumns()).containsExactly("ID", "TITLE", "VIEW_COUNT");
        assertThatCode(() -> execute(NEW_PROJECT_INSERT)).doesNotThrowAnyException();
    }

    @Test
    void anUpToDateDatabaseIsLeftAlone() throws SQLException {
        execute(CURRENT_PROJECTS_TABLE);

        new ShortlistCountColumnCleanupRunner(dataSource).run(null);

        assertThat(projectColumns()).containsExactly("ID", "TITLE", "VIEW_COUNT");
    }

    @Test
    void runningOnEveryStartupIsHarmless() throws SQLException {
        execute(LEGACY_PROJECTS_TABLE);
        ShortlistCountColumnCleanupRunner runner = new ShortlistCountColumnCleanupRunner(dataSource);

        runner.run(null);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        assertThat(projectColumns()).doesNotContain("SHORTLIST_COUNT");
    }

    @Test
    void aDatabaseItCantReachDoesNotStopTheApplicationStarting() {
        JdbcDataSource missing = new JdbcDataSource();
        missing.setURL("jdbc:h2:mem:never-created-" + System.nanoTime() + ";IFEXISTS=TRUE");

        assertThatCode(() -> new ShortlistCountColumnCleanupRunner(missing).run(null)).doesNotThrowAnyException();
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<String> projectColumns() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, "PROJECTS", null)) {
            while (columns.next()) {
                names.add(columns.getString("COLUMN_NAME"));
            }
        }
        return names;
    }
}
