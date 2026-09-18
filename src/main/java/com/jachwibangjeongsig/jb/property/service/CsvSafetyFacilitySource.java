package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;
import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator.Facility;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.Charset;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "safety.csv.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnResource(resources = "file:${safety.csv.directory:./data/safety}/cctv.csv")
public class CsvSafetyFacilitySource implements SafetyFacilitySource {
    private static final Logger log = LoggerFactory.getLogger(CsvSafetyFacilitySource.class);
    private static final GeometryFactory GEOMETRIES = new GeometryFactory();
    private static final Charset CP949 = Charset.forName("MS949");
    private final Path directory;
    private final GeocodingClient geocoding;
    private final Map<Kind, List<Facility>> cache = new EnumMap<>(Kind.class);
    private PreparedGeometry boundary;

    public CsvSafetyFacilitySource(@Value("${safety.csv.directory:./data/safety}") Path directory,
        GeocodingClient geocoding) {
        this.directory = directory;
        this.geocoding = geocoding;
    }

    @Override
    public synchronized List<Facility> load(Kind kind) {
        // ponytail: process-lifetime snapshot and serial first load; add refresh/bulk preprocessing when required.
        return cache.computeIfAbsent(kind, this::read);
    }

    private List<Facility> read(Kind kind) {
        String filename = switch (kind) {
            case CCTV -> "cctv.csv";
            case EMERGENCY_BELL -> "emergency-bell.csv";
            case SECURITY_LIGHT -> "security-light.csv";
            case POLICE_STATION -> "police.csv";
        };
        try (var reader = Files.newBufferedReader(directory.resolve(filename), CP949);
             var csv = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                 .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get().parse(reader)) {
            var headers = csv.getHeaderMap();
            var required = kind == Kind.POLICE_STATION ? List.of("시도청", "주소")
                : List.of("관리번호", "WGS84위도", "WGS84경도");
            if (!headers.keySet().containsAll(required)) throw new IllegalArgumentException("Missing CSV columns");
            String quantityColumn = switch (kind) {
                case CCTV -> "카메라대수";
                case SECURITY_LIGHT -> "설치개수";
                default -> null;
            };
            if (quantityColumn != null && !headers.containsKey(quantityColumn)) {
                throw new IllegalArgumentException("Missing quantity column");
            }
            var facilities = new ArrayList<Facility>();
            var ids = new HashSet<String>();
            int excluded = 0;
            int records = 0;
            for (CSVRecord row : csv) {
                records++;
                if (!row.isConsistent()) throw new IllegalArgumentException("Incomplete CSV row");
                double lat;
                double lng;
                int quantity = 1;
                if (kind == Kind.POLICE_STATION) {
                    if (!"서울청".equals(row.get("시도청").trim())) continue;
                    String address = row.get("주소").trim();
                    if (address.isEmpty()) throw new IllegalArgumentException("Missing police address");
                    var point = geocoding.locate(address).orElseThrow(() ->
                        new IllegalStateException("Police address could not be geocoded"));
                    lat = point.lat();
                    lng = point.lng();
                } else {
                    String id = row.get("관리번호").trim();
                    if (id.isEmpty() || !ids.add(id)) throw new IllegalArgumentException("Missing or duplicate ID");
                    if (quantityColumn != null) {
                        quantity = Integer.parseInt(row.get(quantityColumn).trim());
                        if (quantity < 0) throw new IllegalArgumentException("Negative quantity");
                    }
                    try {
                        lat = Double.parseDouble(row.get("WGS84위도").trim());
                        lng = Double.parseDouble(row.get("WGS84경도").trim());
                    } catch (NumberFormatException invalidCoordinate) {
                        excluded++;
                        continue;
                    }
                }
                if (!insideSeoul(lat, lng)) {
                    excluded++;
                    continue;
                }
                facilities.add(new Facility(lat, lng, quantity));
            }
            if (records == 0) throw new IllegalArgumentException("Empty source file");
            log.info("Safety CSV loaded: kind={}, accepted={}, excludedCoordinates={}",
                kind, facilities.size(), excluded);
            return List.copyOf(facilities);
        } catch (IOException exception) {
            throw new UncheckedIOException("Safety CSV read failed", exception);
        }
    }

    private boolean insideSeoul(double lat, double lng) throws IOException {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return false;
        }
        if (boundary == null) {
            var root = JsonMapper.builder().build().readTree(Files.readString(directory.resolve("seoul.geojson")));
            var polygons = new ArrayList<Polygon>();
            for (JsonNode feature : root.path("features")) {
                var geometry = feature.path("geometry");
                String type = geometry.path("type").asString();
                if ("Polygon".equals(type)) polygons.add(polygon(geometry.path("coordinates")));
                else if ("MultiPolygon".equals(type)) {
                    for (JsonNode coordinates : geometry.path("coordinates")) polygons.add(polygon(coordinates));
                } else throw new IllegalArgumentException("Unsupported boundary geometry");
            }
            if (polygons.isEmpty()) throw new IllegalArgumentException("Empty Seoul boundary");
            var combined = GEOMETRIES.createMultiPolygon(polygons.toArray(Polygon[]::new)).union();
            if (!combined.isValid()) throw new IllegalArgumentException("Invalid Seoul boundary");
            boundary = PreparedGeometryFactory.prepare(combined);
        }
        return boundary.covers(GEOMETRIES.createPoint(new Coordinate(lng, lat)));
    }

    private static Polygon polygon(JsonNode rings) {
        if (!rings.isArray() || rings.isEmpty()) throw new IllegalArgumentException("Missing boundary rings");
        var holes = new LinearRing[rings.size() - 1];
        for (int i = 1; i < rings.size(); i++) holes[i - 1] = ring(rings.get(i));
        return GEOMETRIES.createPolygon(ring(rings.get(0)), holes);
    }

    private static LinearRing ring(JsonNode points) {
        var coordinates = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i);
            if (point.size() < 2 || !point.get(0).isNumber() || !point.get(1).isNumber()) {
                throw new IllegalArgumentException("Invalid boundary coordinate");
            }
            double lng = point.get(0).asDouble();
            double lat = point.get(1).asDouble();
            if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
                throw new IllegalArgumentException("Boundary must use WGS84");
            }
            coordinates[i] = new Coordinate(lng, lat);
        }
        return GEOMETRIES.createLinearRing(coordinates);
    }
}
