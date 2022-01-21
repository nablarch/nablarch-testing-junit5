package nablarch.test.junit5.extension.batch;

import nablarch.test.core.batch.BatchRequestTestSupport;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link BatchRequestTestExtension} を実際に JUnit 5 で動かすテスト。
 *
 * @author Tanaka Tomoyuki
 */
@BatchRequestTest
public class BatchRequestTestExtensionIntegrationTest {

    BatchRequestTestSupport support;

    @Test
    public void BatchRequestTestExtensionが動作していることをテスト() {
        assertThat(support, is(instanceOf(BatchRequestTestSupport.class)));
    }
}