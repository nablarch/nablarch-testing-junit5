package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.SimpleRestTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link SimpleRestTestExtension} を実際に JUnit 5 で動かすテスト。
 *
 * @author Tanaka Tomoyuki
 */
@SimpleRestTest
class SimpleRestTestExtensionIntegrationTest {
    SimpleRestTestSupport support;

    @Test
    void SimpleRestTestExtensionが動作することをテスト() {
        assertThat(support, is(instanceOf(SimpleRestTestSupport.class)));
    }

    @Test
    void testDescriptionが正しくエミュレートされていることをテスト() {
        assertThat(support.testDescription.getTestClass(), is(equalTo(SimpleRestTestExtensionIntegrationTest.class)));
        assertThat(support.testDescription.getMethodName(), is("testDescriptionが正しくエミュレートされていることをテスト"));
    }
}