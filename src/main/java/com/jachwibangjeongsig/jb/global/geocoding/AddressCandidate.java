package com.jachwibangjeongsig.jb.global.geocoding;

/** 주소 검색 후보 한 건. {@code jibunAddress}는 제공자가 지번 표기를 주지 않으면 null이다. */
public record AddressCandidate(
	String roadAddress,
	String jibunAddress,
	double latitude,
	double longitude
) {
}
