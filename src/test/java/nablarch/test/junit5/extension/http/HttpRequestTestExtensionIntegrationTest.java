package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.HttpRequestTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link HttpRequestTestExtension} を実際に JUnit 5 で動かすテスト。
 *
 * @author Tanaka Tomoyuki
 */
@HttpRequestTest
class HttpRequestTestExtensionIntegrationTest {
    HttpRequestTestSupport support;

    @Test
    void HttpRequestTestExtensionが動作することをテスト() {
        assertThat(support, is(instanceOf(HttpRequestTestSupport.class)));
    }
}