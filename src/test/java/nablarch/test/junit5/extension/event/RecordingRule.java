package nablarch.test.junit5.extension.event;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.function.Consumer;

/**
 * 前処理と後処理の実行を記録する {@link TestRule}。
 * <p>
 * {@code base.evaluate()} の前に {@code <ラベル>-before} を、後に {@code <ラベル>-after} を記録する。
 * 後処理は {@code finally} で記録するため、テスト本体が例外を投げた場合でも記録される。
 * </p>
 * <p>
 * 記録先を {@link Consumer} で受け取るのは、クラス変数の実行ログに記録するテストと、
 * サポートクラスのインスタンスに記録するテストの両方から使うためである。
 * </p>
 * @author Ito Kiyohito
 */
class RecordingRule implements TestRule {

    /**
     * 記録先。
     */
    private final Consumer<String> recorder;

    /**
     * 記録するラベル。
     */
    private final String label;

    /**
     * 記録先とラベルを指定してインスタンスを生成する。
     * @param recorder 記録先
     * @param label 記録するラベル
     */
    RecordingRule(Consumer<String> recorder, String label) {
        this.recorder = recorder;
        this.label = label;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                recorder.accept(label + "-before");
                try {
                    base.evaluate();
                } finally {
                    recorder.accept(label + "-after");
                }
            }
        };
    }
}
