package com.zim.identity.infrastructure.persistence.mapper;

import com.zim.identity.domain.model.User;
import com.zim.identity.domain.valueobject.Email;
import com.zim.identity.domain.valueobject.PasswordHash;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.identity.infrastructure.persistence.entity.UserEntity;
import com.zim.shared.domain.TenantId;

public class UserPersistenceMapper {

  public UserEntity toEntity(User user) {
    return new UserEntity(
        user.id().value(),
        user.tenantId().value(),
        user.email().value(),
        user.passwordHash().value(),
        user.isOwner(),
        user.createdAt()
    );
  }

  public User toDomain(UserEntity entity) {
    return User.restore(
        new UserId(entity.getId()),
        new TenantId(entity.getTenantId()),
        new Email(entity.getEmail()),
        new PasswordHash(entity.getPasswordHash()),
        entity.isOwner(),
        entity.getCreatedAt()
    );
  }
}
