package nablarch.test.junit5.extension.integration;

import mockit.Mocked;
import mockit.Verifications;
import nablarch.test.core.integration.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

/**
 * {@link IntegrationTestExtension} の単体テスト。
 * @author Tanaka Tomoyuki
 */
public class IntegrationTestExtensionTest {
    @Mocked
    IntegrationTestSupport mockSupport;

    final IntegrationTestExtension sut = new IntegrationTestExtension();

    @Test
    void beforeEachを実行すると_IntegrationTestSupportとTestEventDispatcherのテスト前処理が実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(new IntegrationTestExtensionTest(), null);

        sut.beforeEach(null);

        new Verifications() {{
            mockSupport.setUpDbBeforeTestMethod(); times = 1;
            mockSupport.dispatchEventOfBeforeTestMethod(); times = 1;
        }};
    }
}
