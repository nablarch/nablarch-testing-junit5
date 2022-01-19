package nablarch.test.junit5.extension.messaging;

import nablarch.test.core.messaging.MessagingReceiveTestSupport;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link MessagingReceiveTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
public class MessagingReceiveTestExtension extends TestEventDispatcherExtension<MessagingReceiveTestSupport> {
    @Override
    protected MessagingReceiveTestSupport createSupport(Object testInstance, ExtensionContext context) {
        return new MessagingReceiveTestSupport(testInstance.getClass());
    }
}
