package nablarch.test.junit5.extension.db;

import nablarch.test.core.db.DbAccessTestSupport;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link DbAccessTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
public class DbAccessTestExtension extends TestEventDispatcherExtension {
    @Override
    protected DbAccessTestSupport createSupport(Object testInstance, ExtensionContext context) {
        return new DbAccessTestSupport(testInstance.getClass());
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);
        ((DbAccessTestSupport) support).beginTransactions();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        super.afterEach(context);
        ((DbAccessTestSupport) support).endTransactions();
    }
}
