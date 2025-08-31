package com.Virtual_Bank_System.TransactionService.Service;

import com.Virtual_Bank_System.TransactionService.DTO.AccountDTO;
import com.Virtual_Bank_System.TransactionService.DTO.AccountTransfer;
import com.Virtual_Bank_System.TransactionService.Exception.AccountNotFoundById;
import com.Virtual_Bank_System.TransactionService.Exception.AccountServiceNotWorkingException;
import com.Virtual_Bank_System.TransactionService.Exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class WebClientService {
  private final WebClient accountServiceWebClient;

  public WebClientService(WebClient accountServiceWebClient) {
    this.accountServiceWebClient = accountServiceWebClient;
  }

  public void validateAccount(UUID accountId) {
    ResponseEntity<Map<String, String>> responseEntity =
        accountServiceWebClient
            .get()
            .uri("/accounts/{accountId}", accountId)
            .retrieve()
            .onStatus(
                HttpStatusCode::is4xxClientError,
                clientResponse -> {
                  System.out.println(clientResponse.statusCode());
                  return Mono.error(new AccountNotFoundById());
                })
            .onStatus(
                HttpStatusCode::is5xxServerError,
                clientResponse -> {
                  return Mono.error(new AccountServiceNotWorkingException());
                })
            .toEntity(new ParameterizedTypeReference<Map<String, String>>() {})
            .block();
  }

  public void validateAmount(UUID accountId, BigDecimal amount) {

    ResponseEntity<Map<String, String>> responseEntity =
        accountServiceWebClient
            .get()
            .uri("/accounts/{accountId}", accountId)
            .retrieve()
            .toEntity(new ParameterizedTypeReference<Map<String, String>>() {})
            .block();
    Map<String, String> accountDetails = responseEntity.getBody();
    BigDecimal ownedMoney = new BigDecimal(accountDetails.get("balance"));
    if (ownedMoney.compareTo(amount) < 0) {
      throw new InsufficientFundsException();
    }
  }

  public List<AccountDTO> getActiveSavingsAccounts() {
    try {
      return accountServiceWebClient
          .get()
          .uri("/accounts/active") // directly call the dedicated endpoint
          .retrieve()
          .bodyToFlux(AccountDTO.class)
          .collectList()
          .block();
    } catch (Exception e) {
      throw new RuntimeException("Failed to fetch accounts from Account Service", e);
    }
  }

  public void creditInterest(AccountTransfer transferDTO) {}
}
