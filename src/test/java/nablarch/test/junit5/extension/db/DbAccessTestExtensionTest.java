package nablarch.test.junit5.extension.db;


import mockit.Expectations;
import nablarch.test.core.db.DbAccessTestSupport;
import nablarch.test.junit5.extension.MockExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link DbAccessTestExtension} の単体テスト。
 * @author Tanaka Tomoyuki
 */
class DbAccessTestExtensionTest {
    DbAccessTestSupport support;

    final DbAccessTestExtension sut = new DbAccessTestExtension();

    @Test
    void DbAccessTestSupportと同等の処理が実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        new Expectations(support) {{
            support.beginTransactions(); times = 1;
            support.dispatchEventOfBeforeTestMethod(); times = 1;
            support.endTransactions(); times = 1;
            support.dispatchEventOfAfterTestMethod(); times = 1;
        }};

        ExtensionContext context = new MockExtensionContext(DbAccessTestExtensionTest.class,
                DbAccessTestExtensionTest.class.getDeclaredMethod("testForMock"));

        sut.beforeEach(context);
        sut.afterEach(context);

        assertThat(support.testName.getMethodName(), is("testForMock"));
    }

    /**
     * Method オブジェクトを取得するために定義した空メソッド。
     * 実行して使用することはない。
     */
    private void testForMock() {
        // noop
    }
}
