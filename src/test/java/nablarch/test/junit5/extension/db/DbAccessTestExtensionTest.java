package nablarch.test.junit5.extension.db;


import mockit.Mocked;
import mockit.Verifications;
import nablarch.test.core.db.DbAccessTestSupport;
import org.junit.jupiter.api.Test;

/**
 * {@link DbAccessTestExtension} の単体テスト。
 * @author Tanaka Tomoyuki
 */
class DbAccessTestExtensionTest {
    @Mocked
    DbAccessTestSupport mockSupport;

    final DbAccessTestExtension sut = new DbAccessTestExtension();

    @Test
    void beforeEachを実行すると_DbAccessTestSupportとTestEventDispatcherで定義されたテスト開始前の処理が実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(new DbAccessTestExtensionTest(), null);

        sut.beforeEach(null);

        new Verifications() {{
            mockSupport.beginTransactions(); times = 1;
            mockSupport.dispatchEventOfBeforeTestMethod(); times = 1;
        }};
    }

    @Test
    void afterEachを実行すると_DbAccessTestSupportとTestEventDispatcherで定義されたテスト終了後の処理が実行されることをテスト() throws Exception {
        sut.postProcessTestInstance(new DbAccessTestExtensionTest(), null);

        sut.afterEach(null);

        new Verifications() {{
            mockSupport.endTransactions(); times = 1;
            mockSupport.dispatchEventOfAfterTestMethod(); times = 1;
        }};
    }
}