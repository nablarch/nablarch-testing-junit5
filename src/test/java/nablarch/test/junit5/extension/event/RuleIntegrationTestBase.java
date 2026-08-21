package nablarch.test.junit5.extension.event;

import nablarch.test.junit5.extension.JupiterEngineRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link ConfigurableTestRuleExtension} に {@link TestRule} を設定し、
 * 実行対象のテストクラスを {@link JupiterEngineRunner} で実行して振る舞いを確かめるテストの共通部分。
 * <p>
 * 実行ログと、テストごとの初期化・後始末をここに集約する。
 * 実行対象のテストクラスは、 surefire に拾われないよう入れ子クラスとして宣言すること。
 * </p>
 * <p>
 * 実行ログも {@link ConfigurableTestRuleExtension} が持つルールの設定もクラス変数であり、
 * このクラスを継承するテストクラス間で共有される。
 * したがって {@code junit.jupiter.execution.parallel.enabled} を有効にすると、
 * テストが互いの実行ログとルールを壊し合う。
 * 現在は surefire にも {@code junit-platform.properties} にも並列実行の設定が無いため直列に実行される。
 * </p>
 * @author Ito Kiyohito
 */
abstract class RuleIntegrationTestBase {

    /**
     * 実行対象のテストクラスとルールが書き込む実行ログ。
     */
    static final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUpExecutionLog() {
        executionLog.clear();
    }

    @AfterEach
    void clearTestRules() {
        ConfigurableTestRuleExtension.clearTestRules();
    }

    /**
     * {@link #executionLog} に記録する {@link RecordingRule} を生成する。
     * @param label 記録するラベル
     * @return 生成した {@link RecordingRule}
     */
    static RecordingRule recordingRule(String label) {
        return new RecordingRule(executionLog::add, label);
    }

    /**
     * テスト本体が実行されたことだけを記録する、実行対象のテストクラス。
     */
    @ExtendWith(ConfigurableTestRuleExtension.class)
    static class RecordingTestFixture {
        @Test
        void test() {
            executionLog.add("test");
        }
    }
}
