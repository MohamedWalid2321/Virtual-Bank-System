package MainAccount.AccountService.Service;

import MainAccount.AccountService.DTOS.AccountCreateDTO;
import MainAccount.AccountService.DTOS.AccountRequestDTO;
import MainAccount.AccountService.DTOS.AccountResponseDTO;
import MainAccount.AccountService.Entities.Account;
import MainAccount.AccountService.Exceptions.*;
import MainAccount.AccountService.Repositry.AccountRepo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class AccountService {
  @Autowired private AccountRepo accountRepo;

  private static final Logger log = LoggerFactory.getLogger(AccountService.class);

  public AccountResponseDTO createAccount(AccountCreateDTO request) {
    // Validate input
    if (request.getInitialBalance() == null) {
      throw new InvalidAccountDataException("Initial balance cannot be null.");
    }

    if (request.getAccountType() == null) {
      throw new AccountNotFoundByType("Account type cannot be null. Allowed: SAVINGS or CHECKING.");
    }

    if (request.getInitialBalance().compareTo(BigDecimal.ZERO) < 0) {
      throw new InvalidAccountDataException("Initial balance cannot be negative.");
    }

    // Generate unique account number
    String accountNumber;
    do {
      accountNumber = generateAccountNumber();
    } while (accountRepo.findByAccountNumber(accountNumber).isPresent());

    System.out.println(request.getAccountType());
    // Build Account entity
    Account account =
        Account.builder()
            .userId(request.getUserId())
            .accountType(request.getAccountType())
            .balance(request.getInitialBalance())
            .accountNumber(accountNumber)
            .status(Account.AccountStatus.ACTIVE)
            // .lastTransactionTime(LocalDateTime.now())
            .build();

    // Save to DB
    accountRepo.save(account);

    // Build response
    return AccountResponseDTO.builder()
        .accountId(account.getAccountID())
        .accountNumber(account.getAccountNumber())
        .message("Account created successfully.")
        .accountType(request.getAccountType())
        .balance(account.getBalance())
        .status(account.getStatus())
        .build();
  }

  public AccountResponseDTO getAccountById(UUID accountId) {
    Account account =
        accountRepo
            .findById(accountId)
            .orElseThrow(
                () -> new AccountNotFoundException("Account with ID " + accountId + " not found."));

    return AccountResponseDTO.builder()
        .accountId(account.getAccountID())
        .accountNumber(account.getAccountNumber())
        .accountType(account.getAccountType())
        .balance(account.getBalance())
        .status(account.getStatus())
        .message("Account retrieved successfully.")
        .build();
  }

  @Transactional(readOnly = true)
  public List<AccountResponseDTO> getAccountsByUserId(UUID userId) {
    List<Account> accounts = accountRepo.findByUserId(userId);
    if (!accountRepo.existsByUserId(userId)) {
      throw new UserNotFound("User not found with id: " + userId);
    }

    return accounts.stream()
        .map(
            account ->
                AccountResponseDTO.builder()
                    .accountId(account.getAccountID())
                    .accountNumber(account.getAccountNumber())
                    .accountType(account.getAccountType())
                    .balance(account.getBalance())
                    .status(account.getStatus())
                    .build())
        .toList();
  }

  @Transactional
  public void transferFunds(AccountRequestDTO request) {
    Account fromAccount =
        accountRepo
            .findById(request.getFromAccountId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Account with ID " + request.getFromAccountId() + " not found."));

    Account toAccount =
        accountRepo
            .findById(request.getToAccountId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Account with ID " + request.getToAccountId() + " not found."));

    if (fromAccount.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidAccountDataException("Transfer amount must be greater than zero");
    }

    if (fromAccount.getAccountID().equals(toAccount.getAccountID())) {
      throw new InvalidAccountDataException("Cannot transfer to the same account");
    }
    if (fromAccount.getStatus() != Account.AccountStatus.ACTIVE
        || toAccount.getStatus() != Account.AccountStatus.ACTIVE) {
      throw new InvalidAccountDataException("One or both accounts are not active");
    }

    // Check sufficient balance
    if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
      throw new InsufficientFundsException("Insufficient balance in the source account.");
    }

    // Perform transfer
    fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
    toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

    LocalDateTime now = LocalDateTime.now();
    fromAccount.setLastTransactionDate(now);
    toAccount.setLastTransactionDate(now);

    // Save changes
    accountRepo.save(fromAccount);
    accountRepo.save(toAccount);
  }

  // start - modified service method for active accounts with exception
  @Transactional(readOnly = true)
  public List<AccountResponseDTO> getActiveAccounts() {
    List<Account> accounts = accountRepo.findByStatus(Account.AccountStatus.ACTIVE);

    if (accounts.isEmpty()) {
      throw new NoActiveAccountsException("No active accounts found.");
    }

    return accounts.stream()
        .map(
            account ->
                AccountResponseDTO.builder()
                    .accountId(account.getAccountID())
                    .accountNumber(account.getAccountNumber())
                    .accountType(account.getAccountType())
                    .balance(account.getBalance())
                    .status(account.getStatus())
                    .message("Active account retrieved successfully.")
                    .build())
        .toList();
  }

  // end - modified service method for active accounts with exception

  private String generateAccountNumber() {
    // Example: 10-digit random number
    return String.valueOf((long) (Math.random() * 1_000_000_0000L));
  }

  @Scheduled(fixedRate = 60000000) // 3 minutes (in milliseconds)
  public void inactivateStaleAccounts() {
    System.out.println("Running inactivateStaleAccounts job at " + LocalDateTime.now());

    // I know Requirenment asked for 24 hours, but for testing purpose I am using 1 minute
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
    System.out.println("Looking for accounts with no transactions since: " + cutoff);

    // Get all accounts first to see what we're working with
    List<Account> allAccounts = accountRepo.findAll();
    System.out.println("Total accounts in database: " + allAccounts.size());

    // Get active accounts
    List<Account> activeAccounts =
        allAccounts.stream()
            .filter(acc -> acc.getStatus() == Account.AccountStatus.ACTIVE)
            .collect(Collectors.toList());

    System.out.println("Total ACTIVE accounts: " + activeAccounts.size());

    // Log details for each active account
    for (Account acc : activeAccounts) {
      LocalDateTime lastTxDate = acc.getLastTransactionDate();
      if (lastTxDate == null) {
        lastTxDate = acc.getCreatedAt();
        System.out.println(
            "Account "
                + acc.getAccountID()
                + " has NULL lastTransactionDate, using createdAt: "
                + lastTxDate);
      } else {
        System.out.println("Account " + acc.getAccountID() + " lastTransactionDate: " + lastTxDate);
      }

      if (lastTxDate.isBefore(cutoff)) {
        System.out.println(
            "Account "
                + acc.getAccountID()
                + " qualifies for inactivation (last tx: "
                + lastTxDate
                + ")");
      } else {
        System.out.println(
            "Account " + acc.getAccountID() + " is still active (last tx: " + lastTxDate + ")");
      }
    }

    // Filter for stale accounts
    List<Account> staleAccounts =
        activeAccounts.stream()
            .filter(
                acc -> {
                  LocalDateTime lastTxDate = acc.getLastTransactionDate();
                  // If lastTransactionDate is null, fall back to createdAt date
                  if (lastTxDate == null) {
                    lastTxDate = acc.getCreatedAt();
                  }
                  return lastTxDate.isBefore(cutoff);
                })
            .collect(Collectors.toList());

    System.out.println("Stale accounts found: " + staleAccounts.size());

    int count = 0;
    if (staleAccounts.isEmpty()) {
      System.out.println("No stale accounts found to inactivate.");
      return;
    }
    for (Account acc : staleAccounts) {
      System.out.println(
          "Inactivating account: "
              + acc.getAccountID()
              + ", last transaction: "
              + (acc.getLastTransactionDate() != null
                  ? acc.getLastTransactionDate()
                  : acc.getCreatedAt()));
      acc.setStatus(Account.AccountStatus.INACTIVE);
      accountRepo.save(acc);
      count++;
    }

    // Log how many accounts were inactivated
    System.out.println(
        "Inactivated " + count + " stale accounts that had no transactions for 1+ minute");
  }
}
