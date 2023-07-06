package nablarch.test.junit5.extension.db;

import nablarch.core.util.annotation.Published;
import nablarch.test.core.db.DbAccessTestSupport;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link DbAccessTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
@Published
public class DbAccessTestExtension extends TestEventDispatcherExtension {
    @Override
    protected DbAccessTestSupport createSupport(Object testInstance, ExtensionContext context) {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!! createSupport start...");
        try {
            return new DbAccessTestSupport(testInstance.getClass());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            System.out.println("!!!!!!!!!!!!!!!!!!!!!! createSupport end!!");
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!! beforeEach start...");
        try {
            super.beforeEach(context);
            ((DbAccessTestSupport) support).beginTransactions();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            System.out.println("!!!!!!!!!!!!!!!!!!!!!! beforeEach end!!");
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!! afterEach start...");
        try {
            super.afterEach(context);
            ((DbAccessTestSupport) support).endTransactions();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            System.out.println("!!!!!!!!!!!!!!!!!!!!!! afterEach end!!");
        }
    }
}
