package nablarch.test.junit5.extension.db;

import nablarch.test.core.db.EntityTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link EntityTestExtension} を実際に JUnit5 で動かすテスト。
 * @author Tanaka Tomoyuki
 */
@EntityTest
class EntityTestExtensionIntegrationTest {

    EntityTestSupport support;

    @Test
    void EntityTestExtensionが動作することをテスト() {
        assertThat(support, is(instanceOf(EntityTestSupport.class)));
    }
}