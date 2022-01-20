package nablarch.test.junit5.extension.http;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import nablarch.test.core.http.SimpleRestTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link SimpleRestTestExtension}の単体テスト。
 * @author Tanaka Tomoyuki
 */
public class SimpleRestTestExtensionTest {
    SimpleRestTestSupport support;

    @Mocked
    Invocation<Void> mockInvocation;
    @Mocked
    ExtensionContext mockExtensionContext;

    @Test
    void beforeEachを実行すると_SimpleRestTestSupportとTestEventDispatcherのテスト前処理が実行されることをテスト(
            @Mocked SimpleRestTestSupport mockSupport) throws Exception {

        SimpleRestTestExtension sut = new SimpleRestTestExtension();

        sut.postProcessTestInstance(this, null);

        sut.beforeEach(null);

        new Verifications() {{
            mockSupport.setUp(); times = 1;
            mockSupport.dispatchEventOfBeforeTestMethod(); times = 1;
        }};
    }

    @Test
    void interceptTestMethodを実行すると_testDescriptionとtestNameのRuleがエミュレートされ_次のテストが実行されることをテスト() throws Throwable {
        new Expectations() {{
            mockExtensionContext.getRequiredTestClass();
            result = SimpleRestTestExtensionTest.class;

            mockExtensionContext.getRequiredTestMethod();
            result = SimpleRestTestExtensionTest.class.getDeclaredMethod("testForMock");
        }};

        SimpleRestTestExtension sut = new SimpleRestTestExtension();

        sut.postProcessTestInstance(this, null);

        sut.interceptTestMethod(mockInvocation, null, mockExtensionContext);

        // TestName
        assertThat(support.testName.getMethodName(), is("testForMock"));
        // TestDescription
        assertThat(support.testDescription.getTestClass(), is(equalTo(SimpleRestTestExtensionTest.class)));
        assertThat(support.testDescription.getMethodName(), is("testForMock"));

        new Verifications() {{
            mockInvocation.proceed(); times = 1;
        }};
    }

    /**
     * Method オブジェクトを取得するために定義した空メソッド。
     * 実行して使用することはない。
     */
    private void testForMock() {
        // noop
    }
}
