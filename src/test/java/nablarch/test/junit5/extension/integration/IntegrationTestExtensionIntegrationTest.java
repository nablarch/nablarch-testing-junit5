package nablarch.test.junit5.extension.integration;

import mockit.Mocked;
import mockit.Verifications;
import nablarch.test.core.db.DbAccessTestSupport;
import nablarch.test.core.integration.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link IntegrationTestExtension} を実際に JUnit 5 で動かすテスト。
 *
 * @author Tanaka Tomoyuki
 */
@IntegrationTest
class IntegrationTestExtensionIntegrationTest {
    @Mocked
    DbAccessTestSupport mockDbAccessTestSupport;

    IntegrationTestSupport support;

    @Test
    void IntegrationTestExtensionが動作することをテスト() {
        assertThat(support, is(instanceOf(IntegrationTestSupport.class)));

        new Verifications() {{
            mockDbAccessTestSupport.setUpDb("setUpDb"); times = 1;
        }};
    }
}