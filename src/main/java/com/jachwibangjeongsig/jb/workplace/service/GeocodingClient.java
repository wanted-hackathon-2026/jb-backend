package com.jachwibangjeongsig.jb.workplace.service;

import com.jachwibangjeongsig.jb.workplace.dto.Coordinates;

import java.util.Optional;

public interface GeocodingClient {

	/** Empty when the provider has no coordinates for the address. */
	Optional<Coordinates> locate(String roadAddress);
}
