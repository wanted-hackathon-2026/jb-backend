package com.jachwibangjeongsig.jb.property.entity;

import com.jachwibangjeongsig.jb.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "property")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Property extends BaseTimeEntity {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.RANDOM)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 255)
	private String address;

	@Column(name = "road_address", length = 255)
	private String roadAddress;

	@Column(name = "sgg_code", nullable = false, length = 20)
	private String sggCode;

	@Column(name = "umd_name", nullable = false, length = 50)
	private String umdName;

	@Column(nullable = false)
	private double lat;

	@Column(nullable = false)
	private double lng;

	@Column(name = "property_type", nullable = false, length = 20)
	private String propertyType;

	@Enumerated(EnumType.STRING)
	@Column(name = "lease_type", nullable = false, length = 20)
	private LeaseType leaseType;

	@Column(nullable = false)
	private int deposit;

	@Column(name = "monthly_rent", nullable = false)
	private int monthlyRent;

	@Column(name = "exclusive_area", precision = 8, scale = 2)
	private BigDecimal exclusiveArea;

	private Integer floor;

	@Column(name = "total_floors")
	private Integer totalFloors;

	@Column(name = "build_year")
	private Integer buildYear;

	@Column(length = 10)
	private String direction;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Builder
	private Property(String name, String address, String roadAddress, String sggCode, String umdName,
		double lat, double lng, String propertyType, LeaseType leaseType, int deposit, int monthlyRent,
		BigDecimal exclusiveArea, Integer floor, Integer totalFloors, Integer buildYear,
		String direction, String description) {
		this.name = name;
		this.address = address;
		this.roadAddress = roadAddress;
		this.sggCode = sggCode;
		this.umdName = umdName;
		this.lat = lat;
		this.lng = lng;
		this.propertyType = propertyType;
		this.leaseType = leaseType;
		this.deposit = deposit;
		this.monthlyRent = monthlyRent;
		this.exclusiveArea = exclusiveArea;
		this.floor = floor;
		this.totalFloors = totalFloors;
		this.buildYear = buildYear;
		this.direction = direction;
		this.description = description;
	}
}
