package nablarch.test.junit5.extension.integration;

import nablarch.test.core.db.DbAccessTestSupport;
import nablarch.test.core.integration.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

/**
 * {@link IntegrationTestExtension} を実際に JUnit 5 で動かすテスト。
 *
 * @author Tanaka Tomoyuki
 */
@IntegrationTest
class IntegrationTestExtensionIntegrationTest {
    final MockedConstruction<DbAccessTestSupport> mocked = mockConstruction(DbAccessTestSupport.class);

    IntegrationTestSupport support;

    @AfterEach
    void tearDown() {
        mocked.close();
    }

    @Test
    void IntegrationTestExtensionが動作することをテスト() {
        assertThat(support, is(instanceOf(IntegrationTestSupport.class)));

        final DbAccessTestSupport mockDbAccessTestSupport = mocked.constructed().get(0);
        verify(mockDbAccessTestSupport).setUpDb("setUpDb");
    }
}