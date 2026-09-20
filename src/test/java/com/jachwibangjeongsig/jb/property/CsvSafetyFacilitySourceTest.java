package com.jachwibangjeongsig.jb.property;

import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.property.service.CsvSafetyFacilitySource;
import com.jachwibangjeongsig.jb.property.service.SafetyFacilitySource.Kind;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class CsvSafetyFacilitySourceTest {
    @TempDir Path directory;
    private static final Charset CP949 = Charset.forName("MS949");

    @BeforeEach
    void boundary() throws Exception {
        // Synthetic non-rectangular boundary: an outside point can still be inside its bounding box.
        Files.writeString(directory.resolve("seoul.geojson"), """
            {"type":"FeatureCollection","features":[{"type":"Feature","geometry":
            {"type":"Polygon","coordinates":[[[126,37],[128,37],[126,39],[126,37]]]}}]}
            """);
    }

    @Test
    void filtersCoordinatesNotAddressesAndKeepsDistinctIdsAtSameLocation() throws Exception {
        Files.writeString(directory.resolve("cctv.csv"), """
            관리번호,소재지도로명주소,WGS84위도,WGS84경도,카메라대수
            1,"서울특별시, 테스트",37.5,126.5,2
            2,서울특별시,37.5,126.5,3
            3,서울특별시,38.5,127.5,9
            4,서울특별시,37.5,216.994723,7
            5,서울특별시,,126.5,6
            6,서울특별시,NaN,126.5,6
            7,서울특별시,37,126,4
            """, CP949);
        var source = new CsvSafetyFacilitySource(directory, address -> Optional.empty());
        var facilities = source.load(Kind.CCTV);
        assertEquals(3, facilities.size());
        assertEquals(9, facilities.stream().mapToInt(f -> f.quantity()).sum());
        assertSame(facilities, source.load(Kind.CCTV));
    }

    @Test
    void bellUsesWgs84ColumnsAndOnePerRow() throws Exception {
        Files.writeString(directory.resolve("emergency-bell.csv"), """
            관리번호,WGS84위도,WGS84경도,부가기능
            1,37.5,126.5,"통화, 경보"
            2,37.5,126.5,통화
            """, CP949);
        assertEquals(2, new CsvSafetyFacilitySource(directory, a -> Optional.empty())
            .load(Kind.EMERGENCY_BELL).stream().mapToInt(f -> f.quantity()).sum());
    }

    @Test
    void missingFileBadQuantityAndDuplicateIdsFailRatherThanProduceZero() throws Exception {
        var source = new CsvSafetyFacilitySource(directory, a -> Optional.empty());
        assertThrows(RuntimeException.class, () -> source.load(Kind.SECURITY_LIGHT));
        Files.writeString(directory.resolve("cctv.csv"), "관리번호,WGS84위도,WGS84경도,카메라대수\n1,37.5,126.5,wrong\n", CP949);
        assertThrows(RuntimeException.class, () -> source.load(Kind.CCTV));
        Files.writeString(directory.resolve("cctv.csv"), "관리번호,WGS84위도,WGS84경도,카메라대수\n1,37.5,126.5,2\n1,37.5,126.5,3\n", CP949);
        assertThrows(RuntimeException.class, () -> source.load(Kind.CCTV));
    }

    @Test
    void policeOnlyGeocodesSeoulAndCachesSuccess() throws Exception {
        Files.writeString(directory.resolve("police.csv"), "시도청,주소\n서울청,서울 주소\n부산청,부산 주소\n", CP949);
        var calls = new AtomicInteger();
        var source = new CsvSafetyFacilitySource(directory, a -> {
            assertEquals("서울 주소", a);
            calls.incrementAndGet();
            return Optional.of(new Coordinates(37.5,126.5));
        });
        assertEquals(1, source.load(Kind.POLICE_STATION).size());
        source.load(Kind.POLICE_STATION);
        assertEquals(1, calls.get());
        assertThrows(RuntimeException.class, () -> new CsvSafetyFacilitySource(directory, a -> Optional.empty())
            .load(Kind.POLICE_STATION));
    }

    @Test
    void handlesMultipolygonAndHolesAndRejectsMalformedBoundary() throws Exception {
        Files.writeString(directory.resolve("seoul.geojson"), """
            {"type":"FeatureCollection","features":[{"type":"Feature","geometry":
            {"type":"MultiPolygon","coordinates":[
              [[[126,37],[128,37],[128,39],[126,39],[126,37]],
               [[126.8,37.8],[127.2,37.8],[127.2,38.2],[126.8,38.2],[126.8,37.8]]],
              [[[129,37],[130,37],[130,38],[129,38],[129,37]]]
            ]}}]}
            """);
        Files.writeString(directory.resolve("emergency-bell.csv"), """
            관리번호,WGS84위도,WGS84경도
            1,37.5,126.5
            2,38,127
            3,37.5,129.5
            """, CP949);
        assertEquals(2, new CsvSafetyFacilitySource(directory, a -> Optional.empty())
            .load(Kind.EMERGENCY_BELL).size());
        Files.writeString(directory.resolve("seoul.geojson"), "{}");
        assertThrows(RuntimeException.class, () -> new CsvSafetyFacilitySource(directory, a -> Optional.empty())
            .load(Kind.EMERGENCY_BELL));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAFETY_CSV_SMOKE", matches = "true")
    void readsUserProvidedPublicFilesWithoutGeocodingOrChangingThem() {
        var source = new CsvSafetyFacilitySource(Path.of("data/safety"), a -> {
            fail("CCTV and bells must not call geocoding");
            return Optional.empty();
        });
        assertTrue(source.load(Kind.CCTV).size() > 50_000);
        assertTrue(source.load(Kind.EMERGENCY_BELL).size() > 20_000);
        assertThrows(RuntimeException.class, () -> source.load(Kind.SECURITY_LIGHT));
    }
}
