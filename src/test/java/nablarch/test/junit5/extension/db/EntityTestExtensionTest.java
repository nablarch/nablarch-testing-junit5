package nablarch.test.junit5.extension.db;

import nablarch.test.core.db.EntityTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link EntityTestExtension}の単体テスト。
 * @author Tanaka Tomoyuki
 */
@EntityTest
class EntityTestExtensionTest {

    EntityTestSupport support;

    @Test
    void test() {
        assertThat(support, is(instanceOf(EntityTestSupport.class)));
    }
}