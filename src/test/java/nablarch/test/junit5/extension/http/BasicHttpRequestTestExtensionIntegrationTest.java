package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.BasicHttpRequestTestTemplate;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link BasicHttpRequestTestExtension} を実際に JUnit 5 で動かすテスト。
 * @author Tanaka Tomoyuki
 */
@BasicHttpRequestTest(baseUri = "/test")
class BasicHttpRequestTestExtensionIntegrationTest {
    BasicHttpRequestTestTemplate support;

    @Test
    void BasicHttpRequestTestExtensionが動作することをテスト() {
        assertThat(support, is(instanceOf(BasicHttpRequestTestTemplate.class)));
    }
}