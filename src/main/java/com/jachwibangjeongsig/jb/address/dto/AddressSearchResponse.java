package com.jachwibangjeongsig.jb.address.dto;

import com.jachwibangjeongsig.jb.global.geocoding.AddressCandidate;
import com.jachwibangjeongsig.jb.global.geocoding.AddressSearchResult;

import java.util.List;

public record AddressSearchResponse(List<AddressCandidate> addresses, int page, boolean hasMore) {

	public static AddressSearchResponse of(AddressSearchResult result, int page) {
		return new AddressSearchResponse(result.candidates(), page, result.hasMore());
	}
}
