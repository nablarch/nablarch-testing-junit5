package nablarch.test.junit5.extension.integration;

import nablarch.test.core.integration.IntegrationTestSupport;
import nablarch.test.junit5.extension.MockExtensionContext;
import nablarch.test.support.reflection.ReflectionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link IntegrationTestExtension} の単体テスト。
 * @author Tanaka Tomoyuki
 */
public class IntegrationTestExtensionTest {

    @Test
    void beforeEachを実行すると_TestRuleが再現され_IntegrationTestSupportとTestEventDispatcherのテスト前処理が実行されることをテスト() throws Exception {
        IntegrationTestExtension sut = new IntegrationTestExtension();

        sut.postProcessTestInstance(this, null);

        final IntegrationTestSupport originalSupport = ReflectionUtil.getFieldValue(sut, "support");
        final IntegrationTestSupport spiedSupport = spy(originalSupport);
        ReflectionUtil.setFieldValue(sut, "support", spiedSupport);
        
        doNothing().when(spiedSupport).setUpDbBeforeTestMethod();

        ExtensionContext context = new MockExtensionContext(IntegrationTestExtensionTest.class,
                IntegrationTestExtensionTest.class.getDeclaredMethod("testForMock"));

        sut.beforeEach(context);

        verify(spiedSupport).setUpDbBeforeTestMethod();
        verify(spiedSupport).dispatchEventOfBeforeTestMethod();

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
