package com.zim.identity.infrastructure.configuration;

import com.zim.identity.application.handler.CreateOwnerHandler;
import com.zim.identity.application.port.ClockProvider;
import com.zim.identity.application.port.PasswordHasher;
import com.zim.identity.application.port.UserIdGenerator;
import com.zim.identity.domain.repository.UserRepository;
import com.zim.identity.domain.valueobject.UserId;
import com.zim.identity.infrastructure.persistence.adapteur.UserRepositoryAdapter;
import com.zim.identity.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.zim.identity.infrastructure.persistence.repository.SpringDataUserRepository;
import com.zim.identity.infrastructure.security.BcryptPasswordHasher;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Instant;
import java.util.UUID;

@Configuration
public class IdentityConfiguration {

  @Bean
  UserPersistenceMapper userPersistenceMapper() {
    return new UserPersistenceMapper();
  }

  @Bean
  UserRepository userRepository(
      SpringDataUserRepository springDataRepository,
      UserPersistenceMapper mapper
  ) {
    return new UserRepositoryAdapter(springDataRepository, mapper);
  }

  @Bean
  PasswordHasher passwordHasher() {
    return new BcryptPasswordHasher();
  }

  @Bean
  UserIdGenerator userIdGenerator() {
    return () -> new UserId(UUID.randomUUID());
  }

  @Bean
  ClockProvider identityClockProvider() {
    return Instant::now;
  }

  /**
   * Joins the caller's transaction when there is one (registration), and
   * starts its own otherwise, so a failure here rolls back the caller too.
   */
  @Bean
  CreateOwnerHandler createOwnerHandler(
      UserRepository userRepository,
      PasswordHasher passwordHasher,
      UserIdGenerator userIdGenerator,
      ClockProvider identityClockProvider,
      PlatformTransactionManager transactionManager
  ) {
    return transactional(
        new CreateOwnerHandler(
            userRepository,
            passwordHasher,
            userIdGenerator,
            identityClockProvider
        ),
        transactionManager
    );
  }

  private static <T> T transactional(
      T handler,
      PlatformTransactionManager transactionManager
  ) {
    NameMatchTransactionAttributeSource attributes =
        new NameMatchTransactionAttributeSource();
    attributes.addTransactionalMethod("handle", new DefaultTransactionAttribute());

    ProxyFactory proxyFactory = new ProxyFactory(handler);
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAdvice(
        new TransactionInterceptor(transactionManager, attributes)
    );

    @SuppressWarnings("unchecked")
    T proxy = (T) proxyFactory.getProxy(handler.getClass().getClassLoader());
    return proxy;
  }
}
