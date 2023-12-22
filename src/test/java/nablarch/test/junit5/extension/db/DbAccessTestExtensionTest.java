package nablarch.test.junit5.extension.db;


import nablarch.test.core.db.DbAccessTestSupport;
import nablarch.test.junit5.extension.MockExtensionContext;
import nablarch.test.support.reflection.ReflectionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link DbAccessTestExtension} の単体テスト。
 * @author Tanaka Tomoyuki
 */
class DbAccessTestExtensionTest {
    final DbAccessTestExtension sut = new DbAccessTestExtension();

    @Test
    void DbAccessTestSupportと同等の処理が実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        final DbAccessTestSupport originalSupport = ReflectionUtil.getFieldValue(sut, "support");
        final DbAccessTestSupport spiedSupport = spy(originalSupport);
        ReflectionUtil.setFieldValue(sut, "support", spiedSupport);

        ExtensionContext context = new MockExtensionContext(DbAccessTestExtensionTest.class,
                DbAccessTestExtensionTest.class.getDeclaredMethod("testForMock"));

        sut.beforeEach(context);
        sut.afterEach(context);

        verify(spiedSupport).beginTransactions();
        verify(spiedSupport).dispatchEventOfBeforeTestMethod();
        verify(spiedSupport).endTransactions();
        verify(spiedSupport).dispatchEventOfAfterTestMethod();

        assertThat(spiedSupport.testName.getMethodName(), is("testForMock"));
    }

    /**
     * Method オブジェクトを取得するために定義した空メソッド。
     * 実行して使用することはない。
     */
    private void testForMock() {
        // noop
    }
}
