package com.jachwibangjeongsig.jb.property.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "property")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Property {
    @Id @Column(columnDefinition = "BINARY(16)") private UUID id;
    private String name;
    private String address;
    @Column(name = "road_address") private String roadAddress;
    @Column(name = "sgg_code") private String sggCode;
    @Column(name = "umd_name") private String umdName;
    private double lat;
    private double lng;
    @Column(name = "property_type") private String propertyType;
    private int deposit;
    @Column(name = "monthly_rent") private int monthlyRent;
    @Column(name = "exclusive_area", precision = 8, scale = 2) private BigDecimal exclusiveArea;
    private Integer floor;
    @Column(name = "total_floors") private Integer totalFloors;
    @Column(name = "build_year") private Integer buildYear;
    private String direction;
    @Column(columnDefinition = "TEXT") private String description;
}
