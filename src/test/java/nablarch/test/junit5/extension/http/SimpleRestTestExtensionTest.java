package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.SimpleRestTestSupport;
import nablarch.test.junit5.extension.MockExtensionContext;
import nablarch.test.support.reflection.ReflectionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link SimpleRestTestExtension}の単体テスト。
 * @author Tanaka Tomoyuki
 */
public class SimpleRestTestExtensionTest {
    SimpleRestTestSupport support;

    @Test
    void beforeEachを実行すると_TestRuleが再現され_SimpleRestTestSupportとTestEventDispatcherのテスト前処理が実行されることをテスト() throws Exception {
        SimpleRestTestExtension sut = new SimpleRestTestExtension();

        sut.postProcessTestInstance(this, null);

        final SimpleRestTestSupport originalSupport = ReflectionUtil.getFieldValue(sut, "support");
        final SimpleRestTestSupport spiedSupport = spy(originalSupport);
        ReflectionUtil.setFieldValue(sut, "support", spiedSupport);

        ExtensionContext mockContext = new MockExtensionContext(SimpleRestTestExtensionTest.class,
                SimpleRestTestExtensionTest.class.getDeclaredMethod("testForMock"));

        sut.beforeEach(mockContext);

        verify(spiedSupport).setUp();
        verify(spiedSupport).dispatchEventOfBeforeTestMethod();

        // TestName
        assertThat(support.testName.getMethodName(), is("testForMock"));
        // TestDescription
        assertThat(support.testDescription.getTestClass(), is(equalTo(SimpleRestTestExtensionTest.class)));
        assertThat(support.testDescription.getMethodName(), is("testForMock"));
    }

    /**
     * Method オブジェクトを取得するために定義した空メソッド。
     * 実行して使用することはない。
     */
    private void testForMock() {
        // noop
    }
}
