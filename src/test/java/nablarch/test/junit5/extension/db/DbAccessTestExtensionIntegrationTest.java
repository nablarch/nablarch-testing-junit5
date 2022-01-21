package nablarch.test.junit5.extension.db;

import nablarch.test.core.db.DbAccessTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link DbAccessTestExtension} を実際に JUnit5 で動かすテスト。
 * @author Tanaka Tomoyuki
 */
@DbAccessTest
public class DbAccessTestExtensionIntegrationTest {

    DbAccessTestSupport support;

    @Test
    void DbAccessTestExtensionが適用できていることをテスト() {
        assertThat(support, is(instanceOf(DbAccessTestSupport.class)));
    }
}
