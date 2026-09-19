package com.jachwibangjeongsig.jb.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.jachwibangjeongsig.jb.property.service.CsvBusStopInfrastructureMetricSource;
import com.jachwibangjeongsig.jb.property.service.InfrastructureMetricSource.InfrastructureKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvBusStopInfrastructureMetricSourceTest {
    @TempDir
    Path directory;

    @Test
    void countsUniqueValidStopsWithinFiveHundredMeters() throws Exception {
        Files.writeString(directory.resolve("bus-stops.csv"), """
            stop_id,latitude,longitude
            here,37.5,127.0
            here,37.5,127.0
            near,37.501,127.0
            far,37.51,127.0
            missing,,127.0
            invalid,999,127.0
            """
        );
        var source = new CsvBusStopInfrastructureMetricSource(directory);

        assertThat(source.measure(InfrastructureKind.BUS_STOP, 37.5, 127.0)).isEqualByComparingTo("2");
    }
}
