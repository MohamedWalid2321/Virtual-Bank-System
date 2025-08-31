package MainAccount.AccountService.Entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Data
@AllArgsConstructor
@Entity
@NoArgsConstructor
@Table(name = "accounts")
@Builder
public class Account {
  public enum AccountType {
    SAVINGS,
    CHECKING,
    SYSTEM
  }

  public enum AccountStatus {
    ACTIVE,
    INACTIVE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "account_id", updatable = false, nullable = false)
  private UUID accountID;

  @Column(name = "account_number", unique = true, nullable = false, length = 20)
  @NotBlank
  private String accountNumber;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false)
  private AccountType accountType;

  @Column(name = "balance", nullable = false)
  private BigDecimal balance = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private AccountStatus status;

  @Column(nullable = false, name = "user_id")
  private UUID userId;

  @NotNull
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @NotNull
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "last_transaction_date")
  private LocalDateTime lastTransactionDate;

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
    // If lastTransactionDate is null, initialize it with creation time
    if (this.lastTransactionDate == null) {
      this.lastTransactionDate = LocalDateTime.now();
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
