package nablarch.test.junit5.extension.messaging;

import nablarch.test.core.messaging.MessagingRequestTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link MessagingRequestTestExtension} を実際に JUnit 5 で動かすテスト。
 * @author Tanaka Tomoyuki
 */
@MessagingRequestTest
class MessagingRequestTestExtensionIntegrationTest {
    MessagingRequestTestSupport support;

    @Test
    void MessagingRequestTestExtensionが動作していることをテスト() {
        assertThat(support, is(instanceOf(MessagingRequestTestSupport.class)));
    }
}