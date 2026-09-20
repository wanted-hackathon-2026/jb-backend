package com.jachwibangjeongsig.jb.workplace.entity;

import com.jachwibangjeongsig.jb.global.entity.BaseTimeEntity;
import com.jachwibangjeongsig.jb.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(name = "workplace")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workplace extends BaseTimeEntity {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.RANDOM)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(name = "road_address", nullable = false, length = 255)
	private String roadAddress;

	@Column(nullable = false)
	private double lat;

	@Column(nullable = false)
	private double lng;

	public void rename(String name) {
		this.name = name;
	}

	public void relocate(String roadAddress, double lat, double lng) {
		this.roadAddress = roadAddress;
		this.lat = lat;
		this.lng = lng;
	}

	@Builder
	private Workplace(
		User user,
		String name,
		String roadAddress,
		double lat,
		double lng
	) {
		this.user = user;
		this.name = name;
		this.roadAddress = roadAddress;
		this.lat = lat;
		this.lng = lng;
	}
}
