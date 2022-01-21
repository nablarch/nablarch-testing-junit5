package nablarch.test.junit5.extension.http;

import mockit.Mocked;
import mockit.Verifications;
import nablarch.test.core.http.RestTestSupport;
import org.junit.jupiter.api.Test;

/**
 * {@link RestTestExtension}の単体テスト。
 * @author Tanaka Tomoyuki
 */
public class RestTestExtensionTest {
    @Mocked
    RestTestSupport mockSupport;

    final RestTestExtension sut = new RestTestExtension();

    @Test
    void beforeEachを実行すると_RestTestSupport_SimpleRestTestSupport_TestEventDispatcherのテスト前処理が実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(new RestTestExtensionTest(), null);

        sut.beforeEach(null);

        new Verifications() {{
            mockSupport.setUpDb(); times = 1;
            mockSupport.setUp(); times = 1;
            mockSupport.dispatchEventOfBeforeTestMethod(); times = 1;
        }};
    }
}
