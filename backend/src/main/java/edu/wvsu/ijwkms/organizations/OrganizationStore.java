package edu.wvsu.ijwkms.organizations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OrganizationStore {

    private final JdbcClient jdbc;

    OrganizationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean codeExists(String code) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM organization_unit WHERE code = :code)")
                .param("code", code)
                .query(Boolean.class)
                .single();
    }

    Optional<OrganizationUnitView> findById(UUID id) {
        return jdbc.sql("""
                        SELECT id, parent_id, unit_type, code, name, status, version
                        FROM organization_unit
                        WHERE id = :id
                        """).param("id", id).query(this::map).optional();
    }

    void create(OrganizationUnitView unit) {
        jdbc.sql("""
                        INSERT INTO organization_unit (
                            id, parent_id, unit_type, code, name, status, version
                        ) VALUES (
                            :id, :parentId, :type, :code, :name, :status, :version
                        )
                        """)
                .param("id", unit.id())
                .param("parentId", unit.parentId())
                .param("type", unit.type().name())
                .param("code", unit.code())
                .param("name", unit.name())
                .param("status", unit.status().name())
                .param("version", unit.version())
                .update();
    }

    int update(UUID id, String name, OrganizationUnitStatus status, long expectedVersion) {
        return jdbc.sql("""
                        UPDATE organization_unit
                        SET name = :name,
                            status = :status,
                            updated_at = CURRENT_TIMESTAMP,
                            version = version + 1
                        WHERE id = :id AND version = :expectedVersion
                        """)
                .param("name", name)
                .param("status", status.name())
                .param("id", id)
                .param("expectedVersion", expectedVersion)
                .update();
    }

    List<OrganizationUnitView> list(int limit, int offset) {
        return jdbc.sql("""
                        SELECT id, parent_id, unit_type, code, name, status, version
                        FROM organization_unit
                        ORDER BY unit_type, code
                        LIMIT :limit OFFSET :offset
                        """)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::map)
                .list();
    }

    long count() {
        return jdbc.sql("SELECT COUNT(*) FROM organization_unit")
                .query(Long.class)
                .single();
    }

    private OrganizationUnitView map(ResultSet result, int rowNumber) throws SQLException {
        return new OrganizationUnitView(
                result.getObject("id", UUID.class),
                result.getObject("parent_id", UUID.class),
                OrganizationUnitType.valueOf(result.getString("unit_type")),
                result.getString("code"),
                result.getString("name"),
                OrganizationUnitStatus.valueOf(result.getString("status")),
                result.getLong("version"));
    }
}
