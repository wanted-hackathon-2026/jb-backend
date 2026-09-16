package com.jachwibangjeongsig.jb.property;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class PropertyLeaseTypeMigrationTest {

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Test
	void backfillsLegacyRowsPreservesIdsAndEnforcesLeasePriceConstraint() throws Exception {
		Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
			.locations("classpath:db/migration").target("5").load().migrate();
		UUID jeonseId = UUID.randomUUID();
		UUID monthlyId = UUID.randomUUID();
		try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
			insertLegacy(connection, jeonseId, 20000, 0);
			insertLegacy(connection, monthlyId, 0, 58);
			Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
				.locations("classpath:db/migration").load().migrate();
			assertProperty(connection, jeonseId, "JEONSE", 20000, 0);
			assertProperty(connection, monthlyId, "MONTHLY", 0, 58);

			// Invalid direct database writes must not bypass the API validation.
			assertThrows(SQLException.class, () -> updateLease(connection, monthlyId, "MONTHLY", 0));
			assertThrows(SQLException.class, () -> updateLease(connection, jeonseId, "JEONSE", 58));
			assertThrows(SQLException.class, () -> updateLease(connection, monthlyId, "SALE", 58));
			assertThrows(SQLException.class, () -> updateLease(connection, monthlyId, null, 58));
			assertProperty(connection, jeonseId, "JEONSE", 20000, 0);
			assertProperty(connection, monthlyId, "MONTHLY", 0, 58);
		}
	}

	private void insertLegacy(Connection connection, UUID id, int deposit, int monthlyRent) throws SQLException {
		try (var statement = connection.prepareStatement("""
			INSERT INTO property (id, name, address, sgg_code, umd_name, lat, lng,
			    property_type, deposit, monthly_rent, created_at, updated_at)
			VALUES (UUID_TO_BIN(?), '기존 매물', '원천동', '41117', '원천동', 37.28, 127.04,
			    'oneroom', ?, ?, '2026-09-16 12:00:00', '2026-09-16 12:00:00')
			""")) {
			statement.setString(1, id.toString());
			statement.setInt(2, deposit);
			statement.setInt(3, monthlyRent);
			statement.executeUpdate();
		}
	}

	private void assertProperty(Connection connection, UUID id, String leaseType, int deposit, int monthlyRent)
		throws SQLException {
		try (var statement = connection.prepareStatement("""
			SELECT BIN_TO_UUID(id) AS id, name, lease_type, deposit, monthly_rent, created_at, updated_at
			FROM property WHERE id = UUID_TO_BIN(?)
			""")) {
			statement.setString(1, id.toString());
			try (var result = statement.executeQuery()) {
				assertThat(result.next()).isTrue();
				assertThat(result.getString("id")).isEqualToIgnoringCase(id.toString());
				assertThat(result.getString("name")).isEqualTo("기존 매물");
				assertThat(result.getString("lease_type")).isEqualTo(leaseType);
				assertThat(result.getInt("deposit")).isEqualTo(deposit);
				assertThat(result.getInt("monthly_rent")).isEqualTo(monthlyRent);
				assertThat(result.getTimestamp("created_at").toLocalDateTime().toString()).isEqualTo("2026-09-16T12:00");
				assertThat(result.getTimestamp("updated_at").toLocalDateTime().toString()).isEqualTo("2026-09-16T12:00");
				assertThat(result.next()).isFalse();
			}
		}
	}

	private void updateLease(Connection connection, UUID id, String leaseType, int rent) throws SQLException {
		try (var statement = connection.prepareStatement("UPDATE property SET lease_type = ?, monthly_rent = ? WHERE id = UUID_TO_BIN(?)")) {
			statement.setString(1, leaseType);
			statement.setInt(2, rent);
			statement.setString(3, id.toString());
			statement.executeUpdate();
		}
	}
}
