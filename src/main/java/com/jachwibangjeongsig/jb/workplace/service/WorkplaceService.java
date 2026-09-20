package com.jachwibangjeongsig.jb.workplace.service;

import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;
import com.jachwibangjeongsig.jb.workplace.exception.AddressNotGeocodableException;
import com.jachwibangjeongsig.jb.workplace.exception.WorkplaceNotFoundException;
import com.jachwibangjeongsig.jb.workplace.dto.WorkplaceCreateRequest;
import com.jachwibangjeongsig.jb.workplace.dto.WorkplaceUpdateRequest;
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

	/** 주소가 실제로 바뀔 때만 지오코딩을 다시 호출한다. */
	@Transactional
	public Workplace update(UUID userId, UUID workplaceId, WorkplaceUpdateRequest request) {
		Workplace workplace = findOwned(userId, workplaceId);
		String roadAddress = request.roadAddress();
		if (roadAddress != null && !roadAddress.equals(workplace.getRoadAddress())) {
			Coordinates coordinates = geocodingClient.locate(roadAddress)
				.orElseThrow(AddressNotGeocodableException::new);
			workplace.relocate(roadAddress, coordinates.lat(), coordinates.lng());
		}
		if (request.name() != null) {
			workplace.rename(request.name());
		}
		return workplace;
	}

	@Transactional
	public void delete(UUID userId, UUID workplaceId) {
		workplaceRepository.delete(findOwned(userId, workplaceId));
	}

	@Transactional(readOnly = true)
	public List<Workplace> findAll(UUID userId) {
		return workplaceRepository.findAllByUser(userRepository.getReferenceById(userId));
	}

	/** 소유자까지 한 번의 조회로 좁힌다. 남의 거점은 존재 자체를 숨기려고 404로 나간다. */
	private Workplace findOwned(UUID userId, UUID workplaceId) {
		return workplaceRepository
			.findByIdAndUser(workplaceId, userRepository.getReferenceById(userId))
			.orElseThrow(WorkplaceNotFoundException::new);
	}
}
