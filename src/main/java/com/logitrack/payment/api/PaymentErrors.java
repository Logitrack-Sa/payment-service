package com.logitrack.payment.api;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentErrors {
  private static final Logger log = LoggerFactory.getLogger(PaymentErrors.class);

  @ExceptionHandler(PaymentException.class)
  ResponseEntity<?> payment(PaymentException e) {
    return ResponseEntity.status(e.status())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("code", e.code(), "message", e.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "code",
                "CONCURRENT_REQUEST",
                "message",
                "Otra solicitud está procesando este pago. Volvé a consultar."));
  }

  @ExceptionHandler({
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<?> badRequest() {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "INVALID_REQUEST", "message", "Revisá los datos de la solicitud."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    log.error("Fallo de pagos: {}", e.getClass().getSimpleName());
    return ResponseEntity.status(500)
        .body(
            Map.of(
                "code", "PAYMENT_ERROR", "message", "No pudimos procesar la solicitud de pago."));
  }
}
