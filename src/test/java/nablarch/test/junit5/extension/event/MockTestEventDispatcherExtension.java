package nablarch.test.junit5.extension.event;

import nablarch.test.event.TestEventDispatcher;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * テストのための {@link TestEventDispatcherExtension} の仮実装クラス。
 * @author Tanaka Tomoyuki
 */
public class MockTestEventDispatcherExtension extends TestEventDispatcherExtension {
    private boolean beforeEachInvoked;
    private boolean afterEachInvoked;

    @Override
    protected TestEventDispatcher createSupport(Object testInstance, ExtensionContext context) {
        return new MockTestEventDispatcher();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);
        beforeEachInvoked = true;
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        super.afterEach(context);
        afterEachInvoked = true;
    }

    /**
     * {@link #beforeEach(ExtensionContext)}が実行されている場合は true を返す。
     * @return {@link #beforeEach(ExtensionContext)}が実行されている場合は true
     */
    public boolean isBeforeEachInvoked() {
        return beforeEachInvoked;
    }

    /**
     * {@link #afterEach(ExtensionContext)}が実行されている場合は true を返す。
     * @return {@link #afterEach(ExtensionContext)}が実行されている場合は true
     */
    public boolean isAfterEachInvoked() {
        return afterEachInvoked;
    }
}