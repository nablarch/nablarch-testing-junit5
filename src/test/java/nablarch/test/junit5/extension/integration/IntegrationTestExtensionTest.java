package nablarch.test.junit5.extension.integration;

import mockit.Expectations;
import nablarch.test.core.integration.IntegrationTestSupport;
import nablarch.test.junit5.extension.MockExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link IntegrationTestExtension} の単体テスト。
 * @author Tanaka Tomoyuki
 */
public class IntegrationTestExtensionTest {
    IntegrationTestSupport mockSupport;

    @Test
    void beforeEachを実行すると_TestRuleが再現され_IntegrationTestSupportとTestEventDispatcherのテスト前処理が実行されることをテスト() throws Exception {
        IntegrationTestExtension sut = new IntegrationTestExtension();

        sut.postProcessTestInstance(this, null);

        new Expectations(mockSupport) {{
            mockSupport.setUpDbBeforeTestMethod(); times = 1;
            mockSupport.dispatchEventOfBeforeTestMethod(); times = 1;
        }};

        ExtensionContext context = new MockExtensionContext(IntegrationTestExtensionTest.class,
                IntegrationTestExtensionTest.class.getDeclaredMethod("testForMock"));

        sut.beforeEach(context);

        assertThat(mockSupport.testName.getMethodName(), is("testForMock"));
    }

    /**
     * Method オブジェクトを取得するために定義した空メソッド。
     * 実行して使用することはない。
     */
    private void testForMock() {
        // noop
    }
}
