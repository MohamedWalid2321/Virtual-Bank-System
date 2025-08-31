package BFF.bffService.Aspect;

import BFF.bffService.Service.KafkaLoggingService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
// It's used to intercept method calls and perform logging of HTTP requests and responses via Kafka.
public class LoggingAspect {

  private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

  @Autowired
  // Instead of local logs, requests and responses are streamed to Kafka
  private KafkaLoggingService kafkaLoggingService;

  // This advice wraps around controller methods, running before and after them.
  // Intercept any method in any class inside the Controller
  @Around("execution(*  BFF.bffService.Controller.*.*(..))")
  public Object logRequestResponse(ProceedingJoinPoint joinPoint) throws Throwable {

    // RequestContextHolder Get HTTP request details
    // gives you access to the current HTTP request (so you can read URL, method, headers, etc.)
    // ProceedingJoinPoint joinPoint → represents the actual controller method being called
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

    if (attributes == null) {
      return joinPoint.proceed();
    }
    // Set Request Attributes
    HttpServletRequest request = attributes.getRequest(); // get or post?
    String endpoint = request.getRequestURI();
    String method = request.getMethod();

    // log request
    Object[] args = joinPoint.getArgs();
    Object requestBody = args.length > 0 ? args[0] : null;

    // Sends request details (endpoint, method, body) to Kafka via KafkaLoggingService
    kafkaLoggingService.logRequest(endpoint, method, requestBody);

    try {
      // joinPoint.proceed() → actually runs the controller method you intercepted and Captures its
      // result
      Object result = joinPoint.proceed();

      int statusCode = 200;
      Object responseBody = result;

      // If the controller returned a ResponseEntity, it extracts:
      //
      // statusCode
      //
      // responseBody
      //
      // Otherwise assumes a 200 response with the returned object.
      //
      // Logs this information to Kafka.
      if (result instanceof ResponseEntity<?>) {
        ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
        statusCode = responseEntity.getStatusCodeValue();
        responseBody = responseEntity.getBody();
      }
      kafkaLoggingService.logResponse(endpoint, method, responseBody, statusCode);
      return result;

    } catch (Exception e) {
      kafkaLoggingService.logResponse(endpoint, method, "Error" + e.getMessage(), 500);
      throw e; // tells app to stop as there is exception
    }
  }
}
