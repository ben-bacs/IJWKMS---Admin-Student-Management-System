package edu.wvsu.ijwkms;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FoundationMigrationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayCreatesFoundationMetadata() throws Exception {
        try (Connection connection = dataSource.getConnection();
                ResultSet result = connection
                        .createStatement()
                        .executeQuery(
                                "SELECT metadata_value FROM app_metadata WHERE metadata_key = 'schema_version'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("academics");
        }

        try (Connection connection = dataSource.getConnection();
                ResultSet result = connection.createStatement().executeQuery("SELECT COUNT(*) FROM app_role")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(7);
        }

        try (Connection connection = dataSource.getConnection();
                ResultSet result = connection.createStatement().executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN (
                              'organization_unit', 'academic_program', 'specialization',
                              'course', 'course_prerequisite', 'curriculum',
                              'curriculum_version', 'curriculum_requirement',
                              'academic_year', 'academic_term'
                          )
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(10);
        }

        try (Connection connection = dataSource.getConnection();
                ResultSet result = connection.createStatement().executeQuery("""
                        SELECT COUNT(*) FROM app_permission
                        WHERE code IN (
                            'academics.read', 'academics.manage',
                            'curriculum.read', 'curriculum.manage'
                        )
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(4);
        }
    }
}
