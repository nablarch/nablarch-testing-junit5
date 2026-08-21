package nablarch.test.junit5.extension.db;

import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.transaction.TransactionContext;
import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * {@link DbAccessTestExtension} と JUnit 4 の {@link Timeout} が併用できないことをテストする。
 * <p>
 * {@link DbAccessTestExtension} は {@code beforeEach} でトランザクションを開始し、
 * DB コネクションとトランザクションを素の {@link ThreadLocal} へ束縛する。
 * 一方 {@link Timeout} はテスト本体を {@code "Time-limited test"} という別スレッドで実行するため、
 * テスト本体からはどちらも取得できなくなる。
 * </p>
 * <p>
 * 対照として、 {@link Timeout} を併用しない場合は同じスレッドで実行され、どちらも取得できることも確かめる。
 * DB は {@code unit-test.xml} が登録する {@link MockConnectionFactory} /
 * {@link MockTransactionFactory} を使用するため、実際の DB は必要ない。
 * </p>
 * @author Ito Kiyohito
 */
public class TimeoutDbAccessIntegrationTest {

    /**
     * テスト本体を実行したスレッド。
     */
    static Thread threadOfTestMethod;

    /**
     * テスト本体から取得できた DB コネクション。取得できなかった場合は {@code null}。
     */
    static Object connection;

    /**
     * テスト本体から取得できたトランザクション。取得できなかった場合は {@code null}。
     */
    static Object transaction;

    /**
     * DB コネクションの取得で発生した例外。発生しなかった場合は {@code null}。
     */
    static Throwable connectionFailure;

    /**
     * トランザクションの取得で発生した例外。発生しなかった場合は {@code null}。
     */
    static Throwable transactionFailure;

    @BeforeEach
    void clearRecords() {
        threadOfTestMethod = null;
        connection = null;
        transaction = null;
        connectionFailure = null;
        transactionFailure = null;
    }

    @Test
    void DbAccessTestExtensionだけならテスト本体からDBコネクションとトランザクションを取得できることをテスト() {
        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(DbAccessTestFixture.class);

        assertThat(summary.getSuccessfulTestCount(), is(1));
        assertThat("テスト本体は beforeEach と同じスレッドで実行される",
                threadOfTestMethod, is(sameInstance(Thread.currentThread())));
        assertThat(connectionFailure, is(nullValue()));
        assertThat(connection, is(notNullValue()));
        assertThat(transactionFailure, is(nullValue()));
        assertThat(transaction, is(notNullValue()));
    }

    @Test
    void Timeoutを併用するとテスト本体からDBコネクションもトランザクションも取得できなくなることをテスト() {
        JupiterEngineRunner.ExecutionSummary summary = JupiterEngineRunner.run(TimeoutDbAccessTestFixture.class);

        assertThat("取得に失敗してもテスト自体は成功する", summary.getSuccessfulTestCount(), is(1));
        assertThat("テスト本体を実行するスレッドは Timeout が起動したスレッドである",
                threadOfTestMethod.getName(), startsWith("Time-limited test"));

        assertThat(connectionFailure, is(instanceOf(IllegalArgumentException.class)));
        assertThat(connectionFailure.getMessage(),
                is("specified database connection name is not register in thread local."
                        + " connection name = [transaction]"));

        assertThat(transactionFailure, is(instanceOf(IllegalArgumentException.class)));
        assertThat(transactionFailure.getMessage(),
                is("specified transaction name is not register in thread local."
                        + " transaction name = [transaction]"));
    }

    /**
     * テスト本体を実行したスレッドと、そこから見える DB コネクション・トランザクションを記録する。
     */
    private static void recordDbAccessFromTestMethod() {
        threadOfTestMethod = Thread.currentThread();

        try {
            connection = DbConnectionContext.getConnection();
        } catch (RuntimeException e) {
            connectionFailure = e;
        }

        try {
            transaction = TransactionContext.getTransaction();
        } catch (RuntimeException e) {
            transactionFailure = e;
        }
    }

    /**
     * {@link DbAccessTestExtension} に {@link Timeout} を追加した Extension。
     */
    static class TimeoutDbAccessTestExtension extends DbAccessTestExtension {
        @Override
        protected List<TestRule> resolveTestRules() {
            List<TestRule> testRules = new ArrayList<>(super.resolveTestRules());
            testRules.add(Timeout.seconds(30));
            return testRules;
        }
    }

    /**
     * {@link DbAccessTestExtension} だけを適用した、実行対象のテストクラス。
     */
    @ExtendWith(DbAccessTestExtension.class)
    static class DbAccessTestFixture {
        @Test
        void test() {
            recordDbAccessFromTestMethod();
        }
    }

    /**
     * {@link DbAccessTestExtension} と {@link Timeout} を併用した、実行対象のテストクラス。
     */
    @ExtendWith(TimeoutDbAccessTestExtension.class)
    static class TimeoutDbAccessTestFixture {
        @Test
        void test() {
            recordDbAccessFromTestMethod();
        }
    }
}
