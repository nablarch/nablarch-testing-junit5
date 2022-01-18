package nablarch.test.junit5.extension.db;


import nablarch.test.core.db.DbAccessTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link DbAccessTestExtension} の単体テスト。
 * @author Tanaka Tomoyuki
 */
@DbAccessTest
class DbAccessTestExtensionTest {

    DbAccessTestSupport support;

    @Test
    void test() {
        assertThat(support, is(instanceOf(DbAccessTestSupport.class)));
    }
}