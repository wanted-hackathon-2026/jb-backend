package com.jachwibangjeongsig.jb.global.geocoding;


import java.util.Optional;

public interface GeocodingClient {

	/** Empty when the provider has no coordinates for the address. */
	Optional<Coordinates> locate(String roadAddress);
}
