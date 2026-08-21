package nablarch.test.junit5.extension;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * 指定したテストクラスを JUnit 5 のエンジンで実行し、その実行結果を収集するテスト用のランナー。
 * <p>
 * 「テストが失敗すること」自体を検証したい場合、検証対象のテストを surefire の実行対象に含めることはできない。
 * このランナーは、検証対象のテストクラスを {@link Launcher} で直接実行することで、
 * 失敗も含めた実行結果を、それを検証する側のテストメソッドから観測できるようにする。
 * </p>
 * <p>
 * 検証対象のテストクラスは、 surefire に拾われないよう、
 * 検証する側のテストクラスの入れ子クラスとして宣言すること。
 * surefire は入れ子クラス({@code *$*})を既定で実行対象から除外する。
 * </p>
 * @author Ito Kiyohito
 */
public final class JupiterEngineRunner {

    /**
     * インスタンス化を禁止する。
     */
    private JupiterEngineRunner() {
    }

    /**
     * 指定されたテストクラスを JUnit 5 のエンジンで実行する。
     * @param testClass 実行するテストクラス
     * @return 実行結果
     */
    public static ExecutionSummary run(Class<?> testClass) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(testClass))
                .build();

        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);

        return new ExecutionSummary(listener.getSummary());
    }

    /**
     * テストクラス 1 つ分の実行結果。
     * <p>
     * {@link TestExecutionSummary} の薄いアダプタである。
     * 中断({@link org.junit.jupiter.api.Assumptions})とスキップ({@code @Disabled})は失敗にも成功にも数えないため、
     * 「すべて成功した」ことを表明する場合は {@link #getFailures()} が空であることではなく、
     * {@link #getSuccessfulTestCount()} が期待する件数であることを表明すること。
     * </p>
     */
    public static final class ExecutionSummary {
        private final TestExecutionSummary summary;
        private final List<Throwable> failures;

        /**
         * {@link TestExecutionSummary} をラップする実行結果を生成する。
         * @param summary ラップする {@link TestExecutionSummary}
         */
        private ExecutionSummary(TestExecutionSummary summary) {
            this.summary = summary;
            this.failures = Collections.unmodifiableList(summary.getFailures().stream()
                    .map(TestExecutionSummary.Failure::getException)
                    .collect(Collectors.toList()));
        }

        /**
         * 実行が開始されたテストの件数を返す。
         * @return 実行が開始されたテストの件数
         */
        public int getTestCount() {
            return (int) summary.getTestsStartedCount();
        }

        /**
         * 成功したテストの件数を返す。
         * @return 成功したテストの件数
         */
        public int getSuccessfulTestCount() {
            return (int) summary.getTestsSucceededCount();
        }

        /**
         * 中断されたテストの件数を返す。
         * <p>
         * {@link org.junit.jupiter.api.Assumptions} の前提条件を満たさなかった場合がこれにあたる。
         * </p>
         * @return 中断されたテストの件数
         */
        public int getAbortedTestCount() {
            return (int) summary.getTestsAbortedCount();
        }

        /**
         * スキップされたテストの件数を返す。
         * <p>
         * {@code @Disabled} などで実行されなかった場合がこれにあたる。
         * </p>
         * @return スキップされたテストの件数
         */
        public int getSkippedTestCount() {
            return (int) summary.getTestsSkippedCount();
        }

        /**
         * 失敗の原因となった例外のリストを返す。
         * <p>
         * テストメソッドで発生した例外だけでなく、テストクラス単位で発生した例外も含む。
         * 中断とスキップは失敗ではないため含まれない。
         * </p>
         * @return 失敗の原因となった例外のリスト。失敗が無い場合は空のリスト
         */
        public List<Throwable> getFailures() {
            return failures;
        }

        /**
         * 失敗の原因となった例外がただ 1 つであることを確かめたうえで、その例外を返す。
         * @return 失敗の原因となった例外
         * @throws AssertionError 失敗の原因となった例外が 1 つでない場合
         */
        public Throwable getOnlyFailure() {
            if (failures.size() != 1) {
                throw new AssertionError("発生した例外がちょうど 1 つであることを期待したが、"
                        + failures.size() + " 件だった : " + failures);
            }
            return failures.get(0);
        }
    }
}
