package com.jachwibangjeongsig.jb.auth.service;

import com.jachwibangjeongsig.jb.auth.dto.IssuedTokens;
import com.jachwibangjeongsig.jb.user.User;

public interface AuthService {

	LoginResult loginWithGoogle(String idToken);

	ReissueResult reissue(String refreshToken);

	void logout(String refreshToken);

	record LoginResult(User user, boolean newUser, IssuedTokens tokens) {
	}

	record ReissueResult(IssuedTokens tokens) {
	}
}
