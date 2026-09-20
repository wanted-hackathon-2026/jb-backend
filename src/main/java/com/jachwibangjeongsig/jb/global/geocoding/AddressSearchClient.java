package com.jachwibangjeongsig.jb.global.geocoding;

/**
 * 부분 문자열로 도로명주소 후보를 찾는다.
 * 완성된 주소 하나를 좌표로 바꾸는 {@link GeocodingClient}와는 호출 대상도 응답도 다르다.
 */
public interface AddressSearchClient {

	int PAGE_SIZE = 10;

	AddressSearchResult search(String query, int page);
}
