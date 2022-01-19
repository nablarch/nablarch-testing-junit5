package nablarch.test.junit5.extension;

import nablarch.test.TestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link TestSupportExtension} を実際に JUnit 5 で動かすテスト。
 *
 * @author Tanaka Tomoyuki
 */
@NablarchTest
class TestSupportExtensionIntegrationTest {
    TestSupport support;

    @Test
    void TestSupportExtensionが動作することをテスト() {
        assertThat(support, is(instanceOf(TestSupport.class)));
    }
}