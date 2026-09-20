package com.jachwibangjeongsig.jb.workplace.repository;

import com.jachwibangjeongsig.jb.workplace.entity.Workplace;

import com.jachwibangjeongsig.jb.user.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkplaceRepository extends JpaRepository<Workplace, UUID> {

	List<Workplace> findAllByUser(User user);

	Optional<Workplace> findByIdAndUser(UUID id, User user);
}
