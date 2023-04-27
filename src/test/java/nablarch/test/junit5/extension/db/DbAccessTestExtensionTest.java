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
    void beforeEachを実行すると_TestRuleが再現され_DbAccessTestSupportとTestEventDispatcherで定義されたテスト開始前の処理が実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        final DbAccessTestSupport originalSupport = ReflectionUtil.getFieldValue(sut, "support");
        final DbAccessTestSupport spiedSupport = spy(originalSupport);
        ReflectionUtil.setFieldValue(sut, "support", spiedSupport);

        ExtensionContext context = new MockExtensionContext(DbAccessTestExtensionTest.class,
                DbAccessTestExtensionTest.class.getDeclaredMethod("testForMock"));

        sut.beforeEach(context);

        verify(spiedSupport).beginTransactions();
        verify(spiedSupport).dispatchEventOfBeforeTestMethod();
        
        assertThat(spiedSupport.testName.getMethodName(), is("testForMock"));
    }

    @Test
    void afterEachを実行すると_DbAccessTestSupportとTestEventDispatcherで定義されたテスト終了後の処理が実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        final DbAccessTestSupport originalSupport = ReflectionUtil.getFieldValue(sut, "support");
        final DbAccessTestSupport spiedSupport = spy(originalSupport);
        ReflectionUtil.setFieldValue(sut, "support", spiedSupport);

        sut.afterEach(null);
        
        verify(spiedSupport).endTransactions();
        verify(spiedSupport).dispatchEventOfAfterTestMethod();
    }

    /**
     * Method オブジェクトを取得するために定義した空メソッド。
     * 実行して使用することはない。
     */
    private void testForMock() {
        // noop
    }
}