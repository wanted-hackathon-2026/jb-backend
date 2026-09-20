package com.jachwibangjeongsig.jb.global.geocoding;

import java.util.List;

/** 한 페이지 분량의 검색 결과. 결과가 없으면 빈 목록에 {@code hasMore=false}다. */
public record AddressSearchResult(List<AddressCandidate> candidates, boolean hasMore) {

	public static AddressSearchResult empty() {
		return new AddressSearchResult(List.of(), false);
	}
}
