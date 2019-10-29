package com.example.retry;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RunWith(SpringRunner.class)
public class RetryApplicationTests {


	private static final MockWebServer server  = new MockWebServer();

	private final RquestCountingFilterFunction requestCounter = new RquestCountingFilterFunction();

	@AfterClass
	public static void shutdown() throws IOException {
		server.shutdown();
	}

	@Test
	public void test() {

		server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
		server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
		server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
		server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

		WebClient webClient = WebClient.builder()
									   .baseUrl(server.url("/api").toString())
									   .filter(requestCounter)
									   .build();

		Mono<String> responseMono1 = webClient.get()
											  .uri("/api")
											  .retrieve()
											  .bodyToMono(String.class)
											  .retryBackoff(3, Duration.ofMillis(1000)) ;

		StepVerifier.create(responseMono1).expectNextCount(1).verifyComplete();

		assertThat(requestCounter.count()).isEqualTo(4);
	}



	static class RquestCountingFilterFunction implements ExchangeFilterFunction {

		final Logger log = LoggerFactory.getLogger(getClass());
		final AtomicInteger counter = new AtomicInteger();

		@Override
		public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
			log.info("Sending {} request to {} {}", counter.incrementAndGet(), request.method(), request.url());
			return next.exchange(request);
		}

		int count() {
			return counter.get();
		}
	}

}
