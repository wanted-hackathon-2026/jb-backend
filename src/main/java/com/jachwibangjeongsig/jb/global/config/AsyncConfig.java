package com.jachwibangjeongsig.jb.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 추천 처리는 LLM 응답을 수십 초 기다리므로 요청 스레드를 붙잡지 않는다.
 *
 * ponytail: 인메모리 실행이라 처리 도중 앱이 죽으면 그 추천은 PENDING/PROCESSING 에
 * 머문 채 남는다. 재시작 시 오래된 진행 중 추천을 FAILED 로 쓸어 담는 작업이나
 * 외부 큐가 필요해지면 그때 붙인다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
