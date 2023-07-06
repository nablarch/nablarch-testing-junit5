package nablarch.test.junit5.extension.db;

import nablarch.core.util.annotation.Published;
import nablarch.test.core.db.DbAccessTestSupport;
import nablarch.test.junit5.extension.event.TestEventDispatcherExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * {@link DbAccessTestSupport} を JUnit 5 で使用するための Extension 実装。
 * @author Tanaka Tomoyuki
 */
@Published
public class DbAccessTestExtension extends TestEventDispatcherExtension {
    @Override
    protected DbAccessTestSupport createSupport(Object testInstance, ExtensionContext context) {
        System.out.println(now() + " !!!!!!!!!!!!!!!!!!!!!! " + Thread.currentThread().getId() + " createSupport start...");
        try {
            return new DbAccessTestSupport(testInstance.getClass());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            System.out.println(now() + " !!!!!!!!!!!!!!!!!!!!!! " + Thread.currentThread().getId() + " createSupport end!!");
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        System.out.println(now() + " !!!!!!!!!!!!!!!!!!!!!! " + Thread.currentThread().getId() + " beforeEach start...");
        try {
            super.beforeEach(context);
            ((DbAccessTestSupport) support).beginTransactions();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            System.out.println(now() + " !!!!!!!!!!!!!!!!!!!!!! " + Thread.currentThread().getId() + " beforeEach end!!");
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        System.out.println(now() + " !!!!!!!!!!!!!!!!!!!!!! " + Thread.currentThread().getId() + " afterEach start...");
        try {
            super.afterEach(context);
            ((DbAccessTestSupport) support).endTransactions();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            System.out.println(now() + " !!!!!!!!!!!!!!!!!!!!!! " + Thread.currentThread().getId() + " afterEach end!!");
        }
    }

    private String now() {
        return LocalDate.now().toString();
    }
}
