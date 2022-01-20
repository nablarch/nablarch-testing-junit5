package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * テストのための {@link TestEventDispatcherExtension} の仮実装クラス。
 * @author Tanaka Tomoyuki
 */
public class MockTestEventDispatcherExtension extends TestEventDispatcherExtension {

    @Override
    protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
        return new MockTestEventDispatcher();
    }
}