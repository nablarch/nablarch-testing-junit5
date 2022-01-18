package nablarch.test.junit5.extension.batch;

import nablarch.test.core.batch.BatchRequestTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link BatchRequestTestExtension} の単体テスト。
 *
 * @author Tanaka Tomoyuki
 */
@BatchRequestTest
public class BatchRequestTestExtensionTest {

    BatchRequestTestSupport support;

    @Test
    public void BatchRequestTestExtensionが動作していることをテスト() {
        assertThat(support, is(instanceOf(BatchRequestTestSupport.class)));
    }
}