package nablarch.test.junit5.extension.messaging;

import nablarch.core.util.annotation.Published;
import nablarch.test.core.messaging.MessagingReceiveTestSupport;
import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link MessagingReceiveTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
@Published
public class MessagingReceiveTestExtension extends MessagingRequestTestExtension {
    @Override
    protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
        return new MessagingReceiveTestSupport(testInstance.getClass());
    }
}
