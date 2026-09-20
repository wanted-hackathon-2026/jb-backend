package com.jachwibangjeongsig.jb.recommendation.exception;

public class ClientSessionRequiredException extends RuntimeException {

	public ClientSessionRequiredException() {
		super("로그인하지 않은 요청에는 X-Client-Session 헤더가 필요합니다.");
	}
}
