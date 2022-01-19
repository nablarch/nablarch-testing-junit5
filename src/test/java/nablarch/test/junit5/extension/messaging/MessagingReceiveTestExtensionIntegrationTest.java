package nablarch.test.junit5.extension.messaging;

import nablarch.test.core.messaging.MessagingReceiveTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link MessagingReceiveTestExtension} を実際に JUnit 5 で動かすテスト。
 * @author Tanaka Tomoyuki
 */
@MessagingReceiveTest
class MessagingReceiveTestExtensionIntegrationTest {
    MessagingReceiveTestSupport support;

    @Test
    void MessagingReceiveTestExtensionが動作していることをテスト() {
        assertThat(support, is(instanceOf(MessagingReceiveTestSupport.class)));
    }
}