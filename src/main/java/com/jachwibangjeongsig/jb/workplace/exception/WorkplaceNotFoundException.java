package com.jachwibangjeongsig.jb.workplace.exception;

public class WorkplaceNotFoundException extends RuntimeException {

	public WorkplaceNotFoundException() {
		super("거점을 찾을 수 없습니다.");
	}
}
