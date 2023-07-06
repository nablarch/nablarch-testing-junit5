package nablarch.test.junit5.extension.db;

import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.transaction.SimpleDbTransactionManager;
import nablarch.test.core.db.DbAccessTestSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link DbAccessTestExtension} を実際に JUnit5 で動かすテスト。
 * @author Tanaka Tomoyuki
 */
@DbAccessTest
public class DbAccessTestExtensionIntegrationTest {

    DbAccessTestSupport support;

    @Test
    void DbAccessTestExtensionが適用できていることをテスト() throws Exception {
        Field f1 = DbAccessTestSupport.class.getDeclaredField("transactionManagers");
        f1.setAccessible(true);
        List<SimpleDbTransactionManager> trans = (List<SimpleDbTransactionManager>) f1.get(support);
        SimpleDbTransactionManager defaultTransactionManager = trans.get(0);
        Field f2 = SimpleDbTransactionManager.class.getDeclaredField("connectionFactory");
        f2.setAccessible(true);
        ConnectionFactory connectionFactory = (ConnectionFactory) f2.get(defaultTransactionManager);

        System.out.println("####################### " + connectionFactory.getClass().getSimpleName() + " ######################");

        assertThat(support, is(instanceOf(DbAccessTestSupport.class)));
    }
}
