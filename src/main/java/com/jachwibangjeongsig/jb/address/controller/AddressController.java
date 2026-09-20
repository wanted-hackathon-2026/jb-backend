package com.jachwibangjeongsig.jb.address.controller;

import com.jachwibangjeongsig.jb.address.dto.AddressSearchResponse;
import com.jachwibangjeongsig.jb.global.geocoding.AddressSearchClient;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/address")
public class AddressController {

	private final AddressSearchClient addressSearchClient;

	public AddressController(AddressSearchClient addressSearchClient) {
		this.addressSearchClient = addressSearchClient;
	}

	/** 공백만 남은 검색어가 null로 바뀌어 @Size를 건너뛰지 않도록 emptyAsNull은 끈다. */
	@InitBinder
	void trimQueryParameters(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
	}

	@GetMapping("/search")
	public AddressSearchResponse search(
		@RequestParam @Size(min = 2, max = 100) String query,
		@RequestParam(defaultValue = "1") @Min(1) @Max(100) int page
	) {
		return AddressSearchResponse.of(addressSearchClient.search(query, page), page);
	}
}
