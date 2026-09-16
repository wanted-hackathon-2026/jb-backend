package com.jachwibangjeongsig.jb.workplace;

import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;
import com.jachwibangjeongsig.jb.global.geocoding.VWorldGeocodingClient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real VWorld API. Skipped unless VWORLD_API_KEY is exported:
 *   VWORLD_API_KEY=... ./gradlew test --tests '*VWorldGeocodingClientLiveTest'
 */
@EnabledIfEnvironmentVariable(named = "VWORLD_API_KEY", matches = ".+")
class VWorldGeocodingClientLiveTest {

	private static final String ROAD_ADDRESS = "경기 수원시 팔달구 월드컵로 205";

	private final GeocodingClient client = new VWorldGeocodingClient(System.getenv("VWORLD_API_KEY"));

	@Test
	void returnsWgs84DegreesNotUtmK() {
		Optional<Coordinates> found = client.locate(ROAD_ADDRESS);

		assertThat(found)
			.withFailMessage("VWorld found no coordinates for %s - check the address is the full form "
				+ "Daum Postcode returns, including the si/do prefix", ROAD_ADDRESS)
			.isPresent();

		Coordinates coordinates = found.orElseThrow();
		// UTM-K (EPSG:5179) would come back as metres, e.g. 953000 / 1953000 - far outside these bounds.
		assertThat(coordinates.lat()).isBetween(33.0, 39.0);
		assertThat(coordinates.lng()).isBetween(124.0, 132.0);
	}

	@Test
	void returnsEmptyForAnAddressThatDoesNotExist() {
		assertThat(client.locate("서울특별시 없는구 없는로 9999")).isEmpty();
	}
}
