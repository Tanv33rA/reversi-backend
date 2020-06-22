package org.abubusoft.reversi.server.model;

import javax.persistence.*;
import java.util.Objects;

@Entity
public class User extends AbstractBaseEntity {
  private String name;

  @Enumerated(EnumType.STRING)
  private UserStatus status;
  @ManyToOne(fetch = FetchType.LAZY)
  private MatchStatus matchStatus;

  public User() {
    super(null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    User user = (User) o;
    return Objects.equals(name, user.name) &&
            status == user.status &&
            Objects.equals(matchStatus, user.matchStatus);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, status, matchStatus);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public UserStatus getStatus() {
    return status;
  }

  public void setStatus(UserStatus status) {
    this.status = status;
  }

  public MatchStatus getMatchStatus() {
    return matchStatus;
  }

  public void setMatchStatus(MatchStatus matchStatus) {
    this.matchStatus = matchStatus;
  }

}