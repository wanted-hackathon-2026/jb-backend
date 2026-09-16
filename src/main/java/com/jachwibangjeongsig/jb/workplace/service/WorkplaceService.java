package com.jachwibangjeongsig.jb.workplace.service;

import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;
import com.jachwibangjeongsig.jb.workplace.exception.AddressNotGeocodableException;
import com.jachwibangjeongsig.jb.workplace.dto.WorkplaceCreateRequest;
import com.jachwibangjeongsig.jb.workplace.entity.Workplace;
import com.jachwibangjeongsig.jb.workplace.repository.WorkplaceRepository;

import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkplaceService {

	private final WorkplaceRepository workplaceRepository;
	private final UserRepository userRepository;
	private final GeocodingClient geocodingClient;

	public WorkplaceService(
		WorkplaceRepository workplaceRepository,
		UserRepository userRepository,
		GeocodingClient geocodingClient
	) {
		this.workplaceRepository = workplaceRepository;
		this.userRepository = userRepository;
		this.geocodingClient = geocodingClient;
	}

	@Transactional
	public Workplace create(UUID userId, WorkplaceCreateRequest request) {
		User user = userRepository.getReferenceById(userId);
		Coordinates coordinates = geocodingClient.locate(request.roadAddress())
			.orElseThrow(AddressNotGeocodableException::new);
		return workplaceRepository.save(Workplace.builder()
			.user(user)
			.name(request.name())
			.roadAddress(request.roadAddress())
			.lat(coordinates.lat())
			.lng(coordinates.lng())
			.build());
	}

	@Transactional(readOnly = true)
	public List<Workplace> findAll(UUID userId) {
		return workplaceRepository.findAllByUser(userRepository.getReferenceById(userId));
	}
}
