package com.zim.quine.rule;

import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.rule.StoreCodeMustBeUniqueRule;
import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.StoreName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StoreCodeMustBeUniqueRuleTest {

    @Test
    void shouldBeBrokenWhenStoreCodeAlreadyExists() {
        Store existingStore = Store.create(
                new StoreId(UUID.randomUUID()),
                new StoreCode("THIES-01"),
                new StoreName("Magasin Thiès")
        );

        StoreCodeMustBeUniqueRule rule =
                new StoreCodeMustBeUniqueRule(
                        List.of(existingStore),
                        new StoreCode("THIES-01")
                );

        assertThat(rule.isBroken()).isTrue();
    }

    @Test
    void shouldNotBeBrokenWhenStoreCodeIsUnique() {
        Store existingStore = Store.create(
                new StoreId(UUID.randomUUID()),
                new StoreCode("THIES-01"),
                new StoreName("Magasin Thiès")
        );

        StoreCodeMustBeUniqueRule rule =
                new StoreCodeMustBeUniqueRule(
                        List.of(existingStore),
                        new StoreCode("DAKAR-01")
                );

        assertThat(rule.isBroken()).isFalse();
    }
}