package nablarch.test.junit5.extension.http;

import nablarch.test.core.http.RestTestSupport;
import nablarch.test.junit5.extension.MockExtensionContext;
import nablarch.test.support.reflection.ReflectionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedConstruction;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * {@link RestTestExtension}の単体テスト。
 * @author Tanaka Tomoyuki
 */
public class RestTestExtensionTest {
    final RestTestExtension sut = new RestTestExtension();

    @Test
    void beforeEachを実行すると_TestRuleが再現され_RestTestSupport_SimpleRestTestSupport_TestEventDispatcherのテスト前処理が実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        final RestTestSupport originalSupport = ReflectionUtil.getFieldValue(sut, "support");
        final RestTestSupport spiedSupport = spy(originalSupport);
        ReflectionUtil.setFieldValue(sut, "support", spiedSupport);

        ExtensionContext context = new MockExtensionContext(RestTestExtensionTest.class,
                RestTestExtensionTest.class.getDeclaredMethod("testForMock"));

        sut.beforeEach(context);

        verify(spiedSupport).setUpDb();
        verify(spiedSupport).setUp();
        verify(spiedSupport).dispatchEventOfBeforeTestMethod();

        assertThat(spiedSupport.testName.getMethodName(), is("testForMock"));
        assertThat(spiedSupport.testDescription.getTestClass(), is(equalTo(RestTestExtensionTest.class)));
    }

    @Test
    void RestTestSupportのインスタンス生成時にテストクラスが渡されていることをテスト() throws Exception {
        final RestTestSupportMockInitializer initializer = new RestTestSupportMockInitializer();

        try (final MockedConstruction<RestTestSupport> mocked = mockConstruction(RestTestSupport.class, initializer)) {
            RestTestSupport created = sut.createSupport(this, null);
            
            assertThat(initializer.testType, is(sameInstance(RestTestExtensionTest.class)));
            assertThat(initializer.mock, is(sameInstance(created)));
        }
    }

    /**
     * Method オブジェクトを取得するために定義した空メソッド。
     * 実行して使用することはない。
     */
    private void testForMock() {
        // noop
    }
    
    private static class RestTestSupportMockInitializer implements MockedConstruction.MockInitializer<RestTestSupport> {
        private RestTestSupport mock;
        private Class<?> testType;

        @Override
        public void prepare(RestTestSupport mock, MockedConstruction.Context context) {
            this.mock = mock;
            testType = (Class<?>) context.arguments().get(0);
        }
    }
}
