package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.RestTestSupport;
import nablarch.test.junit5.extension.batch.BatchRequestTestExtension;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link RestTestExtension} を実際に JUnit 5 で動かすテスト。
 * @author Tanaka Tomoyuki
 */
@RestTest
class RestTestExtensionIntegrationTest {
    RestTestSupport support;

    @Test
    void RestTestExtensionが動作していることをテスト() {
        assertThat(support, is(instanceOf(RestTestSupport.class)));
    }
}