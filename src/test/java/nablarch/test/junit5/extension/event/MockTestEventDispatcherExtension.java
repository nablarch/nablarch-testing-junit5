package nablarch.test.junit5.extension.event;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * テストのための {@link TestEventDispatcherExtension} の仮実装クラス。
 * @author Tanaka Tomoyuki
 */
public class MockTestEventDispatcherExtension extends TestEventDispatcherExtension<MockTestEventDispatcher> {

    @Override
    protected MockTestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
        return new MockTestEventDispatcher();
    }
}