package com.jachwibangjeongsig.jb.auth.service;

import com.jachwibangjeongsig.jb.auth.dto.GoogleIdentity;

public interface GoogleIdentityVerifier {

	GoogleIdentity verify(String idToken);
}
