package nablarch.test.junit5.extension.http;

import mockit.Expectations;
import nablarch.test.core.http.SimpleRestTestSupport;
import nablarch.test.junit5.extension.MockExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
        new Expectations(support) {{
            support.setUp(); times = 1;
            support.dispatchEventOfBeforeTestMethod(); times = 1;
        }};

        ExtensionContext mockContext = new MockExtensionContext(SimpleRestTestExtensionTest.class,
                SimpleRestTestExtensionTest.class.getDeclaredMethod("testForMock"));

        sut.beforeEach(mockContext);

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
