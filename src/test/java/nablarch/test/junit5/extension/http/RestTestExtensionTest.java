package nablarch.test.junit5.extension.http;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import nablarch.test.core.http.RestTestSupport;
import nablarch.test.junit5.extension.MockExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link RestTestExtension}の単体テスト。
 * @author Tanaka Tomoyuki
 */
public class RestTestExtensionTest {
    RestTestSupport support;

    final RestTestExtension sut = new RestTestExtension();

    @Test
    void beforeEachを実行すると_TestRuleが再現され_RestTestSupport_SimpleRestTestSupport_TestEventDispatcherのテスト前処理が実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(this, null);

        new Expectations(support) {{
            support.setUpDb(); times = 1;
            support.setUp(); times = 1;
            support.dispatchEventOfBeforeTestMethod(); times = 1;
        }};

        ExtensionContext context = new MockExtensionContext(RestTestExtensionTest.class,
                RestTestExtensionTest.class.getDeclaredMethod("testForMock"));

        sut.beforeEach(context);

        assertThat(support.testName.getMethodName(), is("testForMock"));
        assertThat(support.testDescription.getTestClass(), is(equalTo(RestTestExtensionTest.class)));
    }

    @Test
    void RestTestSupportのインスタンス生成時にテストクラスが渡されていることをテスト(@Mocked RestTestSupport mockSupport) throws Exception {
        RestTestSupport created = sut.createSupport(this, null);

        new Verifications() {{
            List<RestTestSupport> captured = withCapture(new RestTestSupport(RestTestExtensionTest.class)); times = 1;
            assertThat(captured.get(0), is(sameInstance(created)));
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
