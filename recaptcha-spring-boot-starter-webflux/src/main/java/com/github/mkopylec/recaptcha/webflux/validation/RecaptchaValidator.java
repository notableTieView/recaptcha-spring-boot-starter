package com.github.mkopylec.recaptcha.webflux.validation;

import com.github.mkopylec.recaptcha.commons.RecaptchaProperties;
import com.github.mkopylec.recaptcha.commons.RecaptchaProperties.Validation;
import com.github.mkopylec.recaptcha.commons.validation.RecaptchaValidationException;
import com.github.mkopylec.recaptcha.commons.validation.ValidationResult;
import org.slf4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.slf4j.LoggerFactory.getLogger;

public class RecaptchaValidator {

    private static final Logger log = getLogger(RecaptchaValidator.class);

    protected final WebClient webClient;
    protected final Validation validation;

    public RecaptchaValidator(WebClient webClient, RecaptchaProperties recaptcha) {
        this.webClient = webClient;
        validation = recaptcha.getValidation();
    }

    public Mono<ValidationResult> validate(ServerWebExchange exchange) {
        return validate(exchange, getIpAddress(exchange));
    }

    public Mono<ValidationResult> validate(ServerWebExchange exchange, String ipAddress) {
        return validate(getUserResponse(exchange), ipAddress);
    }

    public Mono<ValidationResult> validate(ServerWebExchange exchange, String ipAddress, String secretKey) {
        return validate(getUserResponse(exchange), ipAddress, secretKey);
    }

    public Mono<ValidationResult> validate(Mono<String> userResponse) {
        return validate(userResponse, null);
    }

    public Mono<ValidationResult> validate(Mono<String> userResponse, String ipAddress) {
        return validate(userResponse, ipAddress, validation.getSecretKey());
    }

    public Mono<ValidationResult> validate(Mono<String> userResponse, String ipAddress, String secretKey) {
        Mono<MultiValueMap<String, Object>> body = userResponse.map(response -> {
            MultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
            parameters.add("secret", secretKey);
            parameters.add("response", response);
            parameters.add("remoteip", ipAddress);
            log.debug("Validating reCAPTCHA:\n    verification url: {}\n    verification parameters: {}", validation.getVerificationUrl(), parameters);
            return parameters;
        });
        return webClient.post()
                .body(body, new ParameterizedTypeReference<MultiValueMap<String, Object>>() {

                })
                .retrieve()
                .bodyToMono(ValidationResult.class)
                .doOnSuccess(result -> log.debug("reCAPTCHA validation finished: {}", result))
                .doOnError(WebClientException.class, ex -> {
                    throw new RecaptchaValidationException(validation.getVerificationUrl(), ex);
                });
    }

    protected String getIpAddress(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().toString() : null;
    }

    protected Mono<String> getUserResponse(ServerWebExchange exchange) {
        return exchange.getFormData()
                .map(parameters -> parameters.getFirst(validation.getResponseParameter()));
    }
}
