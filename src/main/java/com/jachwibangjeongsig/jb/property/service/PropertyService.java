package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;
import com.jachwibangjeongsig.jb.property.dto.PropertyCreateRequest;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.exception.PropertyAddressNotGeocodableException;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PropertyService {

	private final PropertyRepository propertyRepository;
	private final GeocodingClient geocodingClient;
	private final TransactionTemplate transactionTemplate;

	public PropertyService(PropertyRepository propertyRepository, GeocodingClient geocodingClient,
		PlatformTransactionManager transactionManager) {
		this.propertyRepository = propertyRepository;
		this.geocodingClient = geocodingClient;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public Property create(PropertyCreateRequest request) {
		// Do not hold a database transaction open while waiting for VWorld.
		Coordinates coordinates = geocodingClient.locate(request.roadAddress())
			.orElseThrow(PropertyAddressNotGeocodableException::new);
		return transactionTemplate.execute(status -> propertyRepository.saveAndFlush(Property.builder()
			.name(request.name()).address(request.address()).roadAddress(request.roadAddress())
			.sggCode(request.sggCode()).umdName(request.umdName())
			.lat(coordinates.lat()).lng(coordinates.lng()).propertyType(request.propertyType())
			.leaseType(request.leaseType())
			.deposit(request.deposit()).monthlyRent(request.monthlyRent())
			.exclusiveArea(request.exclusiveArea()).floor(request.floor()).totalFloors(request.totalFloors())
			.buildYear(request.buildYear()).direction(request.direction()).description(request.description())
			.build()));
	}
}
