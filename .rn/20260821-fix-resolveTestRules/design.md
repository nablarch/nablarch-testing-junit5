# TestRule 再現機構 — design notes

Not read at runtime — for whoever maintains the design and needs to judge whether a decision is still
right when requirements change.

調査日 2026-08-21 / JUnit 5.11.0・junit:junit 4.13.1・Java 17

## 0. この文書の読み方

**これは決定済みの記録である。** 判断1（`resolveTestRules()` を存続させるか撤退するか）は 2026-08-21 に
**1-A（存続させて直す）** で決定した（§5.1）。判断2（直し方）は選択肢が存在しないため、決定事項として §4 に記録する。
以降のタスク #4〜#6 はすべて 1-A を前提とする。この文書は、その決定の根拠と、決定に伴って受け入れた制約を残すためのもの。

**用語**

| 語 | 意味 |
|---|---|
| **本モジュール** | Maven モジュール `nablarch-testing-junit5`（`pom.xml:8`）。本リポジトリ `nablarch/nablarch-testing-junit5` が生成する唯一のモジュール。リポジトリ運用の話をするときだけ「本リポジトリ」と書く |
| **NTF** | Nablarch Testing Framework。本文では Maven アーティファクト `nablarch-testing` を指す。本モジュールが compile スコープで依存している（`pom.xml:37-41`） |
| **解説書** | `nablarch/nablarch-document` の `ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst`。別リポジトリ |
| **`RestTestSupport` / `SimpleRestTestSupport`** | NTF の REST テスト用サポートクラス。Maven アーティファクトは `nablarch-testing-rest`（本モジュールの optional 依存。`pom.xml:43-49`）。`RestTestSupport` は `SimpleRestTestSupport` を継承し、DB セットアップ機能を足したもの |
| **`@Published`** | Nablarch が後方互換性を維持する公開 API であることを表すアノテーション。クラス宣言に付いている場合は「クラスの全てのAPIを公開APIとする」意味で、「利用者がオーバーライド可能なメソッドも公開API」と定義されている（`nablarch-core` sources `nablarch/core/util/annotation/Published.java:14,16`） |
| **タスク #4 / #5 / #6** | `steering.md` の Tasks に定義された作業単位。#4 = TestRule の適用先を分離する（実装）、#5 = Javadoc を実装と一致させる、#6 = 解説書の修正差分案を作成する |

**見出しの言語** — §1〜§4 の節見出しは rn の design テンプレート由来の英語をそのまま使う。テンプレートにない §0・§5・§6 と、
§4 以降の小見出しは日本語で書く。混在しているのはこの規則による。

**行番号の基準** — `src/main` / `pom.xml` は本リポジトリのコミット `b2ecc31` 時点。`src/test` は本セッションのタスク #1 で
追加・改訂中のため、参照するときに毎回コミットを明示する。外部の jar は sources jar を `~/.m2/repository` から展開して確認した。

## 1. Background & Goals

### 1.1 What is the goal?

解説書「JUnit 5用拡張機能 § JUnit 4のTestRuleを再現する」に書かれているとおりに実装すれば
JUnit 4 の `TestRule` が再現される、という状態にする。

現状はそうなっていない。`TestRule` は `apply(Statement base, Description description)` の `base` を呼ぶ位置で
前処理と後処理を書き分けるものだが、本モジュールは `base` に「何もしない `Statement`」を渡したうえで、
その結果を `beforeEach` の中で `evaluate()` している（`TestEventDispatcherExtension.java:44-49` と `:122-136`）。
そのため**ルールの前処理も後処理も、テスト本体が始まる前にまとめて終わってしまう。**
解説書が唯一の例に挙げている `Timeout` は、まったく機能しない。

出典: 解説書は `nablarch/nablarch-document` のコミット `5391d5c`（`origin/main`、2026-08-05）の
`ja/.../JUnit5_Extension.rst:370-421`。

**実測1 — ルールがテスト本体を包んでいない**（本セッションのタスク #1 で取得）

```
$ mvn -o clean test
[ERROR] TestRuleEmulationIntegrationTest.テストメソッドの実行がTestRuleに包まれていることをテスト:80
Expected: is <[rule-before, test]>
     but: was <[rule-before, rule-after, test]>
[ERROR] Tests run: 32, Failures: 1, Errors: 0, Skipped: 0
```

出典: `git show 7342b5f:src/test/java/nablarch/test/junit5/extension/event/TestRuleEmulationIntegrationTest.java`
の 77-88 行。上の出力はこのリビジョンに対するもので、行番号 `:80` と件数 32 は `7342b5f` 時点の値。
同テストはタスク #1 のレビュー対応で改訂中のため、現在のワーキングツリーとは一致しない。
**本文書の改訂時には `mvn` を実行していないため、この出力自体は再取得していない。**

**実測2 — 解説書の `Timeout` の例が機能しない**（**再現物なし**）

解説書 rst:377-391 / rst:395-414 のコード（`Timeout(1000, MILLISECONDS)` を `resolveTestRules()` で追加）を
そのまま写した `DocTimeoutExampleSpikeTest` を作り、2 秒スリープするテストメソッドが 1 秒のタイムアウトに対して
2.143 秒かかって BUILD SUCCESS になった、と過去のセッションで記録されている。

**ただしこのクラスはワーキングツリーにも全ブランチの git 履歴にも存在しない**
（`git log --all --diff-filter=A --name-only` と `find` のいずれでもヒットしない）。再実行で確かめられる出典はない。
そこでタスク #4 では、解説書の `Timeout` の例が実際にタイムアウトすることを**恒久的なテストとして追加する**（§4.6）。
それまでは「実測したという記録」であって、読み手が確かめられる事実ではない。

### 1.2 What goes wrong without this?

**壊れているのは「テスト本体の実行を必要とするルール」すべて。**

| ルールの種類 | 現状 |
|---|---|
| `TestName` のように前処理だけのもの | 動作する |
| `Timeout` のようにテスト本体の実行を監視するもの | 何も検知しない（実測2） |
| `ExternalResource` / `TemporaryFolder` のように後処理を持つもの | 後処理がテスト本体より前に実行される（実測1） |
| `ErrorCollector` / `ExpectedException` のようにテストの結果を見るもの | テスト本体を見ていないため機能しない |

**NTF が内部で使う 2 つのルールは壊れない。** `TestEventDispatcher#testName`（`org.junit.rules.TestName`。
`nablarch-testing` 6-NEXT-SNAPSHOT sources `nablarch/test/event/TestEventDispatcher.java:92-94`）と
`SimpleRestTestSupport#testDescription`（`nablarch.test.core.rule.TestDescription`）は、どちらも
`Description` からテストメソッド名を控えるだけでテスト本体を必要としない。適用先が移っても壊れなかった（§1.5）。

**しかし「壊れるのは利用者の自作ルールだけ」ではない。** NTF 自身が `TestRule` を公開している。
`nablarch-testing` sources の `nablarch/test/SystemPropertyResource.java:23-24` は
`@Published(tag = "architect") public class SystemPropertyResource extends ExternalResource` であり、
`after()`（`:36-39`）でシステムプロパティをテスト実行前の状態に戻す。JUnit 5 の利用者がこれを
`resolveTestRules()` に渡すと、この復元処理がテスト本体より前に走る。しかも例外にはならず、テストは通る。
**NTF が提供している公開 API のルールが、NTF の JUnit 5 拡張の上で黙って壊れる。**

失敗の仕方が「静かに成功する」であることが最も問題で、利用者は誤った安心を得る。

### 1.3 What does reaching it require?

次の 2 つを同時に満たす必要がある。片方だけなら過去に達成されているが、両立していない。

1. 利用者が `resolveTestRules()` で追加した `TestRule` が、テストメソッドの実行を包むこと
2. NTF の前処理（`dispatchEventOfBeforeTestMethod`、`testName` / `testDescription` の設定）が、
   利用者の `@BeforeEach` より先に実行されること

2 が必要な理由は、JUnit 4 では `TestEventDispatcher#dispatchEventOfBeforeTestMethod` が
親クラスの `@Before` であり、テストクラス側の `@Before` より必ず先に実行されていたため
（`TestEventDispatcher.java:134-140`）。また `RestTestExtension#beforeEach` が呼ぶ `setUpDb()` は
`testDescription.getMethodName()` を参照する（`nablarch-testing-rest` 6-NEXT-SNAPSHOT sources
`nablarch/test/core/http/RestTestSupport.java:79-82`。参照は `:81`）。

出典: `src/main/java/nablarch/test/junit5/extension/http/RestTestExtension.java:19-23`、
`git show 7342b5f:src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionLifecycleMethodTest.java`
の 29-34 行（`@BeforeEach` の中で `support.testName.getMethodName()` が `"test"` になっていることを表明している）

### 1.4 What is out of scope?

- **解説書本体（nablarch/nablarch-document）の修正。** 別リポジトリのため、本セッションでは差分案の作成までとする（タスク #6）
- **NTF 本体（nablarch-testing）の変更。** §5.3 の別課題にあたる。本リポジトリだけでは完結しない
- **JUnit 6 への対応。** 本モジュールが JUnit 6 上で動作するかは未検証。§2.1 に未確認事項として記録する
- **`@Rule` の自動収集。** 利用者が明示的に `resolveTestRules()` へ渡す方式は変えない

### 1.5 How did it get this way?

**解説書が書かれた時点では、記述は事実だった。5 日後に実装だけが変わった。**

| 日付 | できごと |
|---|---|
| 2022-01-21 | 本モジュール初版 `ad2410b`（PR #2「JUnit5対応」）。`TestRule` を `InvocationInterceptor#interceptTestMethod` に適用しており、ルールはテスト本体を包んでいた |
| 2022-01-26 | 解説書に当該節が追加される（`nablarch/nablarch-document` のコミット `c35a1b1`「JUnit5拡張機能の説明を追加 (#409)」。`JUnit5_Extension.rst` を 456 行追加している） |
| 2022-01-31 | 本モジュール `148db9a`（PR #3「fix: TestRuleを再現するタイミングをExtensionのbeforeEachの前に変更」）で適用先が `beforeEach` へ移り、ルールがテスト本体を包まなくなる。解説書は追随していない |

出典: `git show -s --format='%H %ad' ad2410b 148db9a`（それぞれ 2022-01-21 13:29 / 2022-01-31 16:57）、
`nablarch-document` 側は `git log -1 c35a1b1` と `git show --stat c35a1b1`。

**4 年 6 か月検知されなかった理由は 2 つ。**（2022-01-31 → 2026-08-21 は 4 年 6 か月 21 日）

1. NTF 自身が使うルールが 2 つとも前処理だけのもので、適用先が移っても壊れなかった（§1.2）
2. PR #3 が追加した検証が `testName.getMethodName()` の設定だけを見ており、ルールがテスト本体を包むかは
   対象外だった。出典: `git show 7342b5f:src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionTest.java`
   の 110-120 行（`beforeEachを実行すると_TestRuleのエミュレートが行われることをテスト`）

## 2. Assumptions & Constraints

### 2.1 What do we take as true?

**JUnit 5 のライフサイクル順序**

`junit-jupiter-engine` 5.11.0 の `TestMethodTestDescriptor#execute` が、1 件のテストメソッドについて次の順で呼ぶ。

```
1. invokeBeforeEachCallbacks       BeforeEachCallback              (:132)
2. invokeBeforeEachMethods         @BeforeEach                     (:134)
3. invokeBeforeTestExecutionCallbacks  BeforeTestExecutionCallback (:136)
4. invokeTestMethod                テストメソッド                    (:138)
     └ InterceptingExecutableInvoker 経由で InvocationInterceptor#interceptTestMethod が呼ばれる (:217)
5. invokeAfterTestExecutionCallbacks   AfterTestExecutionCallback  (:140)
6. invokeAfterEachMethods          @AfterEach                      (:142)
7. invokeAfterEachCallbacks        AfterEachCallback               (:144)
```

出典: `junit-jupiter-engine-5.11.0.jar` の
`org/junit/jupiter/engine/descriptor/TestMethodTestDescriptor.class` を
`javap -p -c -l` で逆アセンブルし、`execute` の LineNumberTable（`TestMethodTestDescriptor.java:129-147`）と
呼び出し順を突き合わせた。`:217` は `lambda$invokeTestMethod$8` が
`InterceptingExecutableInvoker.invoke(...)` を呼んでいる行。

**`TestRule` の性質**

`TestRule#apply(Statement base, Description description)` の `base` は「テストメソッドを呼び出す処理」であり、
ルールは `base.evaluate()` を自分で呼ぶことで、自分の処理をテストの前に置くか後に置くかを決める。
JUnit 4 のランナーはこの `Statement` を入れ子に積み上げてテスト 1 件の実行を構成する。
`BlockJUnit4ClassRunner#methodBlock` は、`withRules` を `withBefores` / `withAfters` の**外側**に積む。
つまり JUnit 4 ではルールが `@Before` / `@After` ごとテストを包んでいた。

出典: `junit-4.13.1-sources.jar` → `org/junit/runners/model/Statement.java`、
`org/junit/rules/ExternalResource.java:42-67`、
`org/junit/runners/BlockJUnit4ClassRunner.java:303-322`（`:316` methodInvoker → `:319` withBefores →
`:320` withAfters → `:321` withRules の順に包む）

**`InvocationInterceptor` の契約**

> Each method in this class must call {@link Invocation#proceed()} or {@link
> Invocation#skip()} exactly once on the supplied invocation. Otherwise, the
> enclosing test or container will be reported as failed.

出典: [InvocationInterceptor.java:35-37](https://github.com/junit-team/junit5/blob/r5.11.0/junit-jupiter-api/src/main/java/org/junit/jupiter/api/extension/InvocationInterceptor.java)（タグ r5.11.0）。
`Invocation#skip()` は `:236-245` に「This allows to bypass the check that `proceed()` must be called at least once」と
書かれた `default` メソッドとして定義されている。

実装側でもこの契約が確認できる。`junit-jupiter-engine-5.11.0.jar` の
`InvocationInterceptorChain$ValidatingInvocation` は `AtomicBoolean invokedOrSkipped` を持ち、
`proceed()`（`InvocationInterceptorChain.java:130-131`）と `skip()`（`:136-139`）の**どちらも**
`markInvokedOrSkipped()`（`:142-145`）を通す。どちらも呼ばれないまま終わると
`verifyInvokedAtLeastOnce()`（`:148-151`）が
`"Chain of InvocationInterceptors never called invocation"` で失敗させる。
（`javap -p -c -l 'org/junit/jupiter/engine/execution/InvocationInterceptorChain$ValidatingInvocation.class'` で確認）

**`InvocationInterceptor` が持つ intercept メソッド**

`javap -p org/junit/jupiter/api/extension/InvocationInterceptor.class`（`junit-jupiter-api-5.11.0.jar`）の出力より。

| メソッド | 戻り値 | 対象 |
|---|---|---|
| `interceptTestMethod` | `void` | `@Test` |
| `interceptTestTemplateMethod` | `void` | `@TestTemplate`（`@ParameterizedTest` / `@RepeatedTest` を含む） |
| `interceptTestFactoryMethod` | **`T`** | `@TestFactory` |
| `interceptDynamicTest`（2 オーバーロード） | `void` | `DynamicTest` |
| `interceptTestClassConstructor` / `interceptBeforeAllMethod` / `interceptBeforeEachMethod` / `interceptAfterEachMethod` / `interceptAfterAllMethod` | — | 本設計の対象外 |

**JUnit の方針**

> As stated above, JUnit Jupiter does not and will not support JUnit 4 rules natively.

> This support is based on adapters and is limited to those rules that are semantically compatible to
> the JUnit Jupiter extension model, i.e. those that do not completely change the overall execution
> flow of the test.

移行用の `junit-jupiter-migrationsupport` が対応するのは `org.junit.rules.ExternalResource`（`TemporaryFolder` を含む）/
`org.junit.rules.Verifier`（`ErrorCollector` を含む）/ `org.junit.rules.ExpectedException` の 3 種類のみで、
`Timeout` は含まれない。

出典: [migrating-from-junit4.adoc](https://github.com/junit-team/junit-framework/blob/r6.1.3/documentation/modules/ROOT/pages/migrating-from-junit4.adoc)（タグ r6.1.3）
の `:270`、`:274-277`、`:279-284`。同 `:267-268` には
「_JUnit 4 rule support_ is deprecated for removal since version 6.0.0.」ともある。

**`junit-jupiter-migrationsupport` は次のメジャーバージョンで削除される。**

> The `junit-jupiter-migrationsupport` module and its contained classes are now
> deprecated and will be removed in the next major version.

出典: [release-notes-6.0.0.adoc:195-196](https://github.com/junit-team/junit-framework/blob/r6.0.0/documentation/src/docs/asciidoc/release-notes/release-notes-6.0.0.adoc)（タグ r6.0.0）。
JUnit 6.0.0 のリリース日は同ファイル `:4` に「September 30, 2025」とある。

**JUnit のバージョンと保守状況**

| 対象 | 最新 | 状況 |
|---|---|---|
| JUnit 4 | 4.13.2 (2021-02-13) | 5 年半リリースなし。方針は下の引用のとおり |
| JUnit 5 | 5.14.4 (2026-04-26) | 6.x と並行してリリース継続中。セキュリティポリシー上のサポート対象 |
| JUnit 6 | 6.1.3 (2026-08-07) | 現行メジャー。Java 17 ベースライン。セキュリティポリシー上のサポート対象 |

JUnit 4 の方針について、JUnit チームの marcphilipp は 2021-01-19 に次のように書いている（原文ママ）。

> Even though new features are only added to JUnit Platform and Jupiter (currently known as JUnit 5),
> given the widespread use of JUnit 4, we will continue to fix critical bugs, in particular
> security-related ones, for the time being.

出典: [junit4#1695 のコメント](https://github.com/junit-team/junit4/issues/1695#issuecomment-762838552)。

リリース日は次のコマンドで取得した。

```
$ curl -s "https://api.github.com/repos/junit-team/junit-framework/releases?per_page=100" \
    | grep -E '"tag_name"|"published_at"'
$ curl -s "https://api.github.com/repos/junit-team/junit4/releases?per_page=5" \
    | grep -E '"tag_name"|"published_at"'
```

公表された EOL 日程は見つからなかった。セキュリティポリシー上のサポート対象は `6.1.x` と `5.14.x` に限られている
（[SECURITY.md](https://github.com/junit-team/junit-framework/blob/a15778ab46efeef03e2689e2fea0306b17a720cf/SECURITY.md)。
このファイルを最後に更新したコミット `a15778a`、2026-05-19 で固定したリンク）。
JUnit 6 の Java 17 ベースラインは `release-notes-6.0.0.adoc:8`（タグ r6.0.0）の "Java 17 and Kotlin 2.2 baseline" による。

**本モジュールの JUnit 依存**

- `junit:junit:4.13.1` を **compile スコープ**で依存（利用者へ推移的に伝播する）。出典: `pom.xml:57-62`
- 参照している JUnit 4 の型は `org.junit.rules.TestRule` / `org.junit.runners.model.Statement` /
  `org.junit.runner.Description` の 3 つ。出典: `grep -rn "org.junit" src/main --include=*.java`
- **`junit-jupiter-migrationsupport` は使っていない。** JUnit 6 で削除予定なのは同モジュールであり、本モジュールではない
- `org.junit.platform.commons.util.ReflectionUtils` を使用している。これは
  `@API(status = INTERNAL, since = "1.0")` が付いた内部 API で、JUnit 側の都合で変更・削除されうる。
  出典: `src/main/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtension.java:11,65-67` と
  [ReflectionUtils.java:79](https://github.com/junit-team/junit5/blob/r5.11.0/junit-platform-commons/src/main/java/org/junit/platform/commons/util/ReflectionUtils.java)（タグ r5.11.0）

**NTF 本体の JUnit 4 結合度**

`nablarch-testing` 6-NEXT-SNAPSHOT sources jar の 185 java ファイル中、`org.junit` を import しているのは **9 ファイル**。
`unzip` して `grep -rl "^import.*org\.junit" --include=*.java .` と `find . -name '*.java' | wc -l` で数えた。

| ファイル | 参照している JUnit 4 API | 性質 |
|---|---|---|
| `nablarch/test/event/TestEventDispatcher` | `@Rule TestName`、`@BeforeClass` / `@Before` / `@After` / `@AfterClass` | ライフサイクル注釈・ルール |
| `nablarch/test/core/db/DbAccessTestSupport` | `@Before` / `@After` | ライフサイクル注釈 |
| `nablarch/test/core/integration/IntegrationTestSupport` | `@Before` | ライフサイクル注釈 |
| `nablarch/test/Assertion` | `org.junit.Assert` / `org.junit.ComparisonFailure` | 表明 |
| `nablarch/test/SystemPropertyResource` | `extends org.junit.rules.ExternalResource` | ルール |
| `nablarch/test/core/db/EntityTestSupport` | `import static org.junit.Assert.assertArrayEquals` / `assertEquals` | 表明の静的 import のみ |
| `nablarch/test/core/entity/SingleValidationTester` | `import static org.junit.Assert.assertEquals` / `assertFalse` / `assertTrue` | 表明の静的 import のみ |
| `nablarch/test/core/http/ServletForwardVerifier` | `import static org.junit.Assert.assertEquals` | 表明の静的 import のみ |
| `nablarch/test/core/messaging/MessagingRequestTestSupport` | `import static org.junit.Assert.assertEquals` | 表明の静的 import のみ |

**未確認事項**

- **`@RepeatedTest` / `@TestTemplate` / `@Nested` でルールが正しく適用されるか。** §4.2 のプロトタイプで実測したのは
  `@ParameterizedTest` だけで、他は確かめていない。`@RepeatedTest` は `@TestTemplate` の一種なので
  `interceptTestTemplateMethod` を通るはずだが、実行して確かめてはいない。タスク #4 で検証する
- 本モジュールが JUnit 6 上で動作するか（内部 API 使用のため無検証では判断できない）
- JUnit 6 における `junit-vintage-engine` の削除方針の有無。非推奨であることは確認済み
  （migrating-from-junit4.adoc の `:21-23` "The JUnit Vintage engine is deprecated and should only be used
  temporarily while migrating tests to JUnit Jupiter or another testing framework with native JUnit Platform
  support."）だが、削除時期の記載は見つからなかった
- `resolveTestRules()` を実際に利用しているプロジェクトの有無と規模
- 「`getRequiredTestMethod()` が返す `Method` を拡張が自分で `invoke` するとテストが 2 回走る」ことの実測。
  JUnit 5 側の呼び出しが止まらないことは §2.2 のとおり出典があるが、2 回走る様子そのものは観測していない

### 2.2 What binds the solution?

**JUnit 5 には、§1.3 の 2 条件を同時に満たす拡張ポイントが存在しない。**

| | テストメソッドを呼び出す処理を引数で受け取れるか | 呼ばれる順番 |
|---|---|---|
| `BeforeEachCallback#beforeEach(ExtensionContext)` | 受け取れない | `@BeforeEach` より前 |
| `InvocationInterceptor#interceptTestMethod(Invocation, …)` | 受け取れる（`invocation`） | `@BeforeEach` より後 |

`ExtensionContext` には、これから行われるテストメソッドの呼び出しを表すものがない。
`javap -p org/junit/jupiter/api/extension/ExtensionContext.class`（`junit-jupiter-api-5.11.0.jar`）が返す
メソッドは 24 個で、テストメソッドに関するものは `getTestMethod()` / `getRequiredTestMethod()` の 2 つだけ、
いずれも `java.lang.reflect.Method` を返す。そして JUnit 5 側の呼び出しは
`TestMethodTestDescriptor#execute` が `invokeTestMethod` を通じて行う（§2.1 の `:138`）ので、
拡張が自分で `invoke` してもそれを**置き換えることはできない**。

これが §1.3 の 2 条件が両立しなかった構造的な理由であり、判断2 に選択肢がない理由でもある（§5.2）。

**その他の制約**

- `TestEventDispatcher#testName` は NTF 側の `public final` フィールド（`TestEventDispatcher.java:92-94`）であり、
  本モジュールから値を直接設定できない。`TestName` の内部フィールドは
  `private volatile String name`（`junit-4.13.1-sources` `org/junit/rules/TestName.java:28`）で、
  `starting(Description)`（`:31-33`）経由でしか設定されない。つまり `apply()` を通すしかない
- `TestEventDispatcherExtension` はクラス宣言に `@Published(tag = "architect")` が付いている
  （`TestEventDispatcherExtension.java:33`）。`Published` の定義上、
  **このクラスの `protected` メソッドはすべて後方互換を保証する公開 API**（§0 の用語表）。
  `resolveTestRules()` のシグネチャ変更は避ける
- 既存テスト、特に `TestEventDispatcherExtensionLifecycleMethodTest` を壊さない

## 3. Design overview

### 3.1 What is the core idea, and why does it solve the problem?

**「TestRule をどこに適用するか」を 1 か所に決めるのをやめ、ルールの性質で適用先を分ける。**

過去の 2 つの実装（§1.5）は、いずれも全部を 1 か所に置いたために §2.2 のトレードオフをそのまま被った。

| | TestRule がテスト本体を包むか | NTF 前処理が `@BeforeEach` より先か |
|---|---|---|
| 初版 `ad2410b`（全部を `interceptTestMethod` へ） | ○ | × |
| PR #3 `148db9a` 以降の現行（全部を `beforeEach` へ） | × | ○ |
| 本設計（性質で分ける） | ○ | ○ |

分ける基準は「そのルールがテスト本体の実行を必要とするか」。

- **必要としないもの** — `TestName` / `TestDescription`。`Description` からテストメソッド名を控えるだけ。
  現行どおり `beforeEach` で空の `Statement` に対して適用すれば足りる
- **必要とするもの** — 利用者が `resolveTestRules()` で追加するルール。`interceptTestMethod` へ移す

NTF の前処理より後に実行されて困るのは NTF 自身のルールだけなので、この分割で両方が成立する。

### 3.2 What are the pieces, and what is each responsible for?

| 要素 | 責務 |
|---|---|
| `beforeEach`（`BeforeEachCallback`、既存） | 内部ルールの適用と NTF 前処理の実行。実行位置は現行から変えない |
| `resolveInternalTestRules()`（新設、**`protected`**） | `TestName` / `TestDescription` を返す。`SimpleRestTestExtension`（別パッケージ）が override して `testDescription` を追加する。`TestEventDispatcherExtension` がクラス単位で `@Published(tag = "architect")` なので、**これは後方互換を保証する公開 API が 1 本増えることを意味する**（§2.2、§4.5、§5.1） |
| `interceptTestMethod` / `interceptTestTemplateMethod`（`InvocationInterceptor`、新設） | `resolveTestRules()` が返すルールで `invocation.proceed()` を包む |
| `resolveTestRules()`（既存、`@Published`） | 利用者がテスト本体を包みたいルールを返す。基底実装は空リストを返すよう変更する（§4.5） |

### 3.3 How does work move?

```
1. BeforeEachCallback#beforeEach
     resolveInternalTestRules() を NOOP に対して適用 → テストメソッド名が確定
     support.dispatchEventOfBeforeTestMethod()
     （RestTestExtension はここで setUpDb()、DbAccessTestExtension は beginTransactions() を呼ぶ）
2. @BeforeEach              ← testName / testDescription を参照できる
3. interceptTestMethod
     resolveTestRules() のルールで invocation.proceed() を包み、evaluate()
4.   └ テストメソッド        ← 利用者のルールが包む範囲
5. proceed から復帰
6. @AfterEach
7. AfterEachCallback#afterEach
     support.dispatchEventOfAfterTestMethod()
```

## 4. Detailed design

**この章は、過去のセッションで実装したプロトタイプの検証結果に基づく。**
プロトタイプはワーキングツリーから取り消してあり（変更を破棄して元の実装に戻してある）、
実装はタスク #4 で改めて行う。§4.2 の実測値は再実行では確かめられない（§4.2 の注記）。

### 4.1 変更差分（`src/main` のみ。+68 / -15 行）

**これは差分の全文ではない。** 下に載せるのは変更点を読むための**要約 diff** で、`@@` にハンク範囲が入っていないため
`git apply` はできない。またこの差分には、`TestEventDispatcherExtension.java:149-168` の既存 Javadoc
（「親クラスが返したリストに追加する形でルールを追加すること」というコード例を含む）が入っていない。
そのまま当てると実装と食い違う Javadoc が `src/main` に残るため、**タスク #5 で Javadoc を直すことを前提とする。**

`src/main/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtension.java`

```diff
 import org.junit.jupiter.api.extension.ExtensionContext;
+import org.junit.jupiter.api.extension.InvocationInterceptor;
+import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
 import org.junit.jupiter.api.extension.TestInstancePostProcessor;
@@
 import java.lang.reflect.Field;
+import java.lang.reflect.Method;
@@
         BeforeEachCallback,
-        AfterEachCallback {
+        AfterEachCallback,
+        InvocationInterceptor {
@@
     public void beforeEach(ExtensionContext context) throws Exception {
-        emulateTestRules(context);
+        applyInternalTestRules(context);
         support.dispatchEventOfBeforeTestMethod();
     }
 
     /**
-     * JUnit4の{@link TestRule}を再現する。
+     * NTF が内部で使用する{@link TestRule}を適用する。
      * @param context コンテキスト
      */
-    private void emulateTestRules(ExtensionContext context) {
-        Description description = convert(context);
-
-        List<TestRule> testRules = resolveTestRules();
-        Statement statement = NOOP_STATEMENT;
-        for (TestRule testRule : testRules) {
-            statement = testRule.apply(statement, description);
-        }
-
+    private void applyInternalTestRules(ExtensionContext context) {
+        Statement statement = applyTestRules(resolveInternalTestRules(), NOOP_STATEMENT, context);
         try {
             statement.evaluate();
         } catch (Throwable e) {
             throw new RuntimeException(e);
         }
     }
 
+    @Override
+    public void interceptTestMethod(Invocation<Void> invocation,
+                                    ReflectiveInvocationContext<Method> invocationContext,
+                                    ExtensionContext extensionContext) throws Throwable {
+        applyTestRules(resolveTestRules(), toStatement(invocation), extensionContext).evaluate();
+    }
+
+    @Override
+    public void interceptTestTemplateMethod(Invocation<Void> invocation,
+                                            ReflectiveInvocationContext<Method> invocationContext,
+                                            ExtensionContext extensionContext) throws Throwable {
+        applyTestRules(resolveTestRules(), toStatement(invocation), extensionContext).evaluate();
+    }
+
+    /**
+     * テストメソッドの呼び出しを表す{@link Statement}を生成する。
+     * @param invocation テストメソッドの呼び出し
+     * @return {@code invocation} を実行する{@link Statement}
+     */
+    private Statement toStatement(Invocation<Void> invocation) {
+        return new Statement() {
+            @Override
+            public void evaluate() throws Throwable {
+                invocation.proceed();
+            }
+        };
+    }
+
+    /**
+     * {@code base} を{@code testRules}で包んだ{@link Statement}を構築する。
+     * @param testRules 適用する{@link TestRule}のリスト
+     * @param base ベースとなる{@link Statement}
+     * @param context コンテキスト
+     * @return 構築された{@link Statement}
+     */
+    private Statement applyTestRules(List<TestRule> testRules, Statement base, ExtensionContext context) {
+        Description description = convert(context);
+        Statement statement = base;
+        for (TestRule testRule : testRules) {
+            statement = testRule.apply(statement, description);
+        }
+        return statement;
+    }
+
+    /**
+     * NTF が内部で使用する{@link TestRule}のリストを取得する。
+     * <p>
+     * ここで返した{@link TestRule}は、テストメソッドの実行を包まない。
+     * {@link Description}から情報を取得するだけのルールに限って使用すること。
+     * </p>
+     * @return NTF が内部で使用する{@link TestRule}のリスト
+     */
+    protected List<TestRule> resolveInternalTestRules() {
+        return Collections.singletonList(support.testName);
+    }
+
@@
     protected List<TestRule> resolveTestRules() {
-        return Collections.singletonList(support.testName);
+        return Collections.emptyList();
     }
```

`src/main/java/nablarch/test/junit5/extension/http/SimpleRestTestExtension.java`

```diff
     @Override
-    protected List<TestRule> resolveTestRules() {
-        List<TestRule> testRules = new ArrayList<>(super.resolveTestRules());
+    protected List<TestRule> resolveInternalTestRules() {
+        List<TestRule> testRules = new ArrayList<>(super.resolveInternalTestRules());
         testRules.add(((SimpleRestTestSupport) support).testDescription);
         return testRules;
     }
```

### 4.2 プロトタイプの実測結果（**再現物なし**）

すべて JUnit 5.11.0・Java 17 上で実行。全体行は `mvn -o clean test`、個別行は
`mvn -o clean test -Dtest=<クラス名>` で確認した、と記録されている。

**この表は再実行では確かめられない。** プロトタイプの差分はワーキングツリーから取り消してあり、
git 履歴にも残っていない。`DocTimeoutExampleSpikeTest` も存在しない（§1.1 実測2）。
**タスク #4 の実装時に、この表の各行を取り直す。**

| 確認項目 | 修正前 | 修正後 |
|---|---|---|
| `TestRuleEmulationIntegrationTest`（実行順が `[rule-before, test, rule-after]`） | FAIL | **PASS** |
| 解説書 rst:377-391 / rst:395-414 の `Timeout` の例（1 秒に対し 2 秒スリープ） | 2.143 s で BUILD SUCCESS | **`TestTimedOutException: test timed out after 1000 milliseconds`（0.976 s）** |
| `@ParameterizedTest` でルールが適用されるか | 未確認 | **PASS**（`interceptTestTemplateMethod` 経由） |
| `TestEventDispatcherExtensionLifecycleMethodTest`（NTF 前処理が `@BeforeEach` より先） | PASS | **PASS** |
| `RestTestExtensionTest` / `SimpleRestTestExtensionTest`（`testDescription` の設定） | PASS | **PASS** |
| `mvn -o clean test` 全体 | 32 件中 1 件失敗 | **32 件中 1 件失敗（§4.3 のとおり別の 1 件。仕様変更そのもの）** |

`@RepeatedTest` / `@TestTemplate` / `@Nested` は実測していない（§2.1 の未確認事項）。

### 4.3 既存テストで 1 件だけ落ちる。それは仕様変更そのもの

```
[ERROR] TestEventDispatcherExtensionTest.TestRuleエミュレート時に例外が発生した場合は
        _発生した例外を原因として持つ実行時例外がスローされること:135
        expected java.lang.RuntimeException to be thrown, but nothing was thrown
```

（この出力もプロトタイプ実行時のもので、本文書の改訂時には再取得していない。）

このテストは「`resolveTestRules()` が返したルールが `beforeEach` の中で評価され、
そこで起きた例外が `RuntimeException` に包まれる」ことを検証している
（出典: `git show 7342b5f:src/test/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtensionTest.java`
の 122-139 行）。本設計はこの前提そのものを変えるので、テストは `interceptTestMethod` を対象に書き換える。

あわせて **例外の扱いが変わる。** 現行はルールが投げた例外を `RuntimeException` で包むが、
`interceptTestMethod` 側は包まずそのまま伝播させる。JUnit 4 では `ExpectedException` や
`ErrorCollector` がテストの成否を例外で表現するため、包むと機能しなくなる。
包まないのは意図した変更であり、タスク #4 の完了条件に含める。この結果として生じる非対称は §4.5 に記録する。

### 4.4 再現できない範囲

タスク #5（Javadoc）とタスク #6（解説書）で、次の 4 点をすべて明記する。

**(1) テストを飛ばすルールは使えない。**

`InvocationInterceptor` の契約は「`Invocation#proceed()` **または** `Invocation#skip()` をちょうど 1 回呼ぶ」
（§2.1）。§4.1 の実装は `proceed()` しか呼ばないので、`base.evaluate()` を呼ばないルールを渡すと
どちらも呼ばれないまま終わり、次の例外になる。

```
org.junit.platform.commons.JUnitException: Chain of InvocationInterceptors never called invocation:
  org.junit.jupiter.engine.extension.TimeoutExtension, ...
```

このメッセージ文字列は `junit-jupiter-engine-5.11.0.jar` の
`InvocationInterceptorChain$ValidatingInvocation#verifyInvokedAtLeastOnce`
（`InvocationInterceptorChain.java:148-151`）に実在する（§2.1 で逆アセンブルして確認）。
上の出力自体はプロトタイプ実行時のもので、本文書の改訂時には再取得していない。
`skip()` を使えば飛ばすルールも表現しうるが、本設計では採らない。理由は §4.5。

**(2) ルールが包むのはテストメソッドの呼び出しのみ。**

`@BeforeEach` / `@AfterEach` および NTF の前後処理は含まれない。JUnit 5 に、自身の
`BeforeEachCallback` を `Statement` で包む手段がないため（§2.2）。JUnit 4 のランナーはルールを
`@Before` / `@After` の外側に積んでいた（§2.1 の `BlockJUnit4ClassRunner.java:319-321`）ので、
**これは JUnit 4 との意味の違いである。**

**(3) `Timeout` と `DbAccessTestExtension` は併用できない。**

これは §4.4 のなかで最も影響が大きい。**解説書が挙げている唯一の例が `Timeout` である**ため、
タスク #6 の解説書修正案にも必ず入れる（§6）。

JUnit 4 の `Timeout` は `apply()` で `FailOnTimeout` を返し（`junit-4.13.1-sources`
`org/junit/rules/Timeout.java:153-155` → `:145-151`）、`FailOnTimeout#evaluate()` は
`"Time-limited test"` という名前の**新しいスレッド**を起こしてそこで `base.evaluate()` を走らせる
（`org/junit/internal/runners/statements/FailOnTimeout.java:120-132`。スレッド生成は `:123-124`、
`start()` は `:127`）。

一方 NTF の DB コネクションは `ThreadLocal` で保持される
（`nablarch-core-jdbc` sources `nablarch/core/db/connection/DbConnectionContext.java:26-32`）。
`DbAccessTestExtension.java:19-23` は `beforeEach` で `beginTransactions()` を呼び、それが
`nablarch/test/core/db/DbAccessTestSupport.java:95-117`（`:115` が `manager.beginTransaction()`）→
`nablarch/core/db/transaction/SimpleDbTransactionManager.java:35-52`（`:48` が
`DbConnectionContext.setConnection(dbTransactionName, dbConnection)`）を通って
**呼び出し元スレッドの `ThreadLocal`** にコネクションを置く。

`nablarch-core-jdbc` は本モジュールが実際に解決する 6-NEXT-SNAPSHOT 版の sources jar が
ローカルリポジトリに存在しないため、上の 2 ファイルの行番号は **2.2.0 の sources jar** で確認した。
6-NEXT-SNAPSHOT の class ファイルを `javap -p -l` で逆アセンブルして LineNumberTable を照合したところ、
`DbConnectionContext#setConnection` も `SimpleDbTransactionManager#beginTransaction` も
2.2.0 sources と同じ行に対応していた（`setConnection(String, AppDbConnection)` は 59-67、
`beginTransaction` の `setConnection` 呼び出しは 48）。

JUnit 4 のランナーではルールが `@Before` / `@After` ごと包む（§2.1）ため、トランザクション開始も
テスト本体も同じ `"Time-limited test"` スレッドで起きて整合していた。本設計では `beforeEach` はメインスレッド、
テスト本体だけが別スレッドになるため、**`DbAccessTestExtension` と `Timeout` を併用すると
テスト本体から DB コネクションが取れない。**

さらにタイムアウト成立時、`FailOnTimeout` はタイムアウト例外を作ったあと `finally` で
`thread.join(1)`（1 ミリ秒）しか待たずに抜ける（`FailOnTimeout.java:133-138`、待ち時間の判定は
`getResult` の `:153-168`）。つまり**テスト本体が走ったまま `@AfterEach` → `afterEach` →
`endTransactions()`（`DbAccessTestExtension.java:25-29`）が並行実行される競合が残る。**

なお `RestTestExtension` / `SimpleRestTestExtension` はこの問題を持たない。
`RestTestExtension#beforeEach` が呼ぶのは `setUpDb()` だけで（`RestTestExtension.java:19-23`）、
その先の `DbAccessTestSupport#setUpDb` も `assertTableEquals` 系も
`TransactionTemplate#execute`（`nablarch-testing` sources `nablarch/test/core/db/TransactionTemplate.java:69-90`）
の中で 1 回の呼び出しごとにトランザクションを開始・終了する。`beforeEach` をまたいで
`ThreadLocal` にコネクションを残さないため、スレッドが変わっても壊れない。
`beginTransactions()` / `endTransactions()` を呼ぶ Extension は `DbAccessTestExtension` だけである
（`grep -rn "beginTransactions\|endTransactions" src/main --include=*.java`）。

**(4) `@TestFactory` / `DynamicTest` には適用されない。**

§4.1 の差分が override するのは `interceptTestMethod` と `interceptTestTemplateMethod` の 2 つだけ。
`InvocationInterceptor` にはこのほかに `interceptTestFactoryMethod` と `interceptDynamicTest` がある（§2.1 の表）。
override していないと既定実装が単に `invocation.proceed()` を呼ぶので、
**`@TestFactory` のテストでは利用者のルールが一切適用されず、しかも例外にならず黙って通る。**
§1.2 が「最も問題」とした「静かに成功する」が、この経路にはそのまま残る。

これは「今後追加すればよい」で済む話ではない。`interceptTestFactoryMethod` の戻り値は `T`
（`javap` の出力。§2.1 の表）で、`Statement#evaluate()` は戻り値 `void` なので、
§4.1 の `toStatement(invocation)` と同じ形では包めない。`DynamicTest` 側も、
1 個の `@TestFactory` メソッドから生まれる複数の動的テストそれぞれに対して
`interceptDynamicTest` が呼ばれるため、「テスト 1 件を包む」という `TestRule` の単位と対応しない。
**構造的な制約として扱う。**

### 4.5 記録しておく実装上の選択

**(1) `resolveTestRules()` の基底実装を `singletonList(support.testName)` から `emptyList()` に変える。**

代案は「基底実装はそのまま `testName` を返し、`interceptTestMethod` でも `beforeEach` でも
同じリストを適用する」だが、`TestName` が 2 回適用される。値は同じなので実害はないものの、
`resolveTestRules()` の意味が「利用者がテスト本体を包みたいルール」に定まらない。空リストを採る。

副作用として、解説書 rst:420-421 の「必ず親クラスの `resolveTestRules()` が返すリストをベースにすること。
そうしない場合、親クラスで登録している `TestRule` が再現されなくなる」が事実でなくなる（§6 参照）。

**(2) `protected resolveInternalTestRules()` を新設する。公開 API が 1 本増えることを受け入れる。**

`TestEventDispatcherExtension` はクラス単位で `@Published(tag = "architect")` なので、
`protected` メソッドを 1 つ足すことは後方互換を保証する公開 API を 1 つ足すことに等しい（§2.2）。

**新しい公開 API を増やさない代替がある。** 内部ルールの適用先を基底の `beforeEach` に固定してしまい、
`SimpleRestTestExtension` は自分の `beforeEach`（`SimpleRestTestExtension.java:24-28`。既に override 済み）の中で
`testDescription` を適用する。こうすれば `resolveInternalTestRules()` は不要になり、結果は同じになる。

**それでも新設を採る。** 理由は 2 つ。

- 適用の順序が壊れやすくなる。`SimpleRestTestExtension#beforeEach` は
  `super.beforeEach(context)` を先に呼ぶので（`:26`）、そこで `dispatchEventOfBeforeTestMethod()` が
  終わってしまう。`testDescription` を使う NTF のリスナがもし現れれば、順序が合わなくなる
- 「内部で使うルール」という概念が名前で表れないと、次に触る人が
  `resolveTestRules()` に内部ルールを戻してしまう。§1.5 と同じ事故の再発を招く

**逆に言えば、公開 API を 1 本も増やしたくないという方針が優先されるなら、代替に切り替えてよい。**
その場合 §4.1 の `SimpleRestTestExtension` 側の差分だけが変わる。

**(3) `skip()` は使わない。**

`InvocationInterceptor` の契約は `proceed()` **または** `skip()` を 1 回（§2.1）なので、
`skip()` を呼べば「テストを飛ばすルール」も表現しうる。しかし `TestRule` の側には
「`base` を呼ばなかった」ことを外から知る手段がない。`apply()` が返した `Statement` を `evaluate()` するだけでは、
その中で `base.evaluate()` が呼ばれたかどうかは分からない。呼ばれなかった場合にだけ `skip()` を呼ぶ、という
実装ができないため、本設計では採らない。結果として §4.4 (1) の制約が残る。

**(4) 例外の扱いが、同じ `TestRule` 型でもリストによって変わる。**

`applyInternalTestRules`（`beforeEach` 側）は例外を `RuntimeException` で包む。
`interceptTestMethod` 側は包まない（§4.3）。**この非対称は意図的だが、対称ではない。**

- 包まない理由 — `ExpectedException` / `ErrorCollector` が例外でテストの成否を表現するため（§4.3）
- 包む側を残す理由 — `beforeEach` は `throws Exception` しか宣言できず、`Statement#evaluate()` は
  `Throwable` を投げるため、そのままでは伝播させられない

利用者から見ると「同じ `TestRule` を `resolveTestRules()` に渡すか `resolveInternalTestRules()` に渡すかで
例外の扱いが変わる」ことになる。タスク #5 の Javadoc で両方に明記する。

### 4.6 タスク #4 で恒久的なテストとして残すもの

§1.1 実測2 と §4.2 が「再現物なし」になった原因は、確認をスパイク（使い捨てのテストクラス）で行い、
その場で捨てたこと。同じ主張が二度と出典なしにならないよう、次をタスク #4 で**リポジトリに残るテストとして**追加する。

- 解説書 rst:377-391 / rst:395-414 と同じ形で `Timeout` を `resolveTestRules()` に渡し、
  タイムアウトが実際に発生することを検証するテスト
- `base` を呼ばないルールを渡すと `JUnitException` になることを検証するテスト（§4.4 (1)）
- `@ParameterizedTest` でルールが適用されることを検証するテスト（`TestRuleEmulationIntegrationTest` に既にある）
- `@RepeatedTest` でルールが適用されることを検証するテスト（§2.1 の未確認事項の解消）

## 5. 判断ポイント

### 5.1 判断1 — `resolveTestRules()` を存続させるか（**決定済み**）

> **決定: 1-A（存続させて直す）。** 2026-08-21、`/rn:ty 1-a` によりユーザーが決定した。
> 以降のタスク #4〜#6 は 1-A を前提とする。以下は、その決定の根拠と、決定に伴って受け入れた制約の記録である。

**背景。** JUnit は JUnit 4 の Rule をネイティブサポートしないと明言し、移行用モジュール
`junit-jupiter-migrationsupport` は JUnit 6.0.0 で非推奨になり、**次のメジャーバージョンで削除される**と
公表されている（§2.1）。本モジュールの `resolveTestRules()` はその流れの外にある。だから
「直す」の前に「続けるか」を決める必要があった。

**選択肢**

| | 内容 |
|---|---|
| **1-A 存続して直す** | §4 の差分を入れる。解説書のコード例がそのまま動くようになる |
| **1-B 非推奨化して撤退** | `resolveTestRules()` に `@Deprecated` を付け、解説書から「TestRule を再現できる」という記述を撤回し、JUnit 5 の同等機能（`Timeout` → `@Timeout`、`ExternalResource` / `TemporaryFolder` → `BeforeEachCallback` / `@TempDir`、`ExpectedException` → `assertThrows`）への置き換えを案内する。実装は現状のままとし、`TestName` / `TestDescription` を設定する内部経路だけを残す |
| **1-C doc-only** | 実装も非推奨マークも変えず、「ルールはテスト本体を包まない」という制約を Javadoc と解説書に明記するだけ |
| **1-D 1-A + `@Deprecated` 併走** | §4 の差分を入れて直したうえで、同時に `resolveTestRules()` に `@Deprecated` を付け、JUnit 5 の機能への移行を促す |

**比較**

| 判断軸 | 1-A 存続 | 1-B 撤退 | 1-C doc-only | 1-D 併走 |
|---|---|---|---|---|
| 解説書のコード例が動くか | 動く（§4.2、ただし再現物なし） | 動かない。記述を撤回する | 動かない。「動かない」と書く | 動く |
| `ExternalResource` 系の後処理の順序 | 直る | 誤ったまま残る | 誤ったまま残る | 直る |
| `SystemPropertyResource`（NTF の公開 API、§1.2）| 使えるようになる | 使えないままだが明示される | 使えないままだが明示される | 使えるようになる |
| 変更範囲 | `src/main` +68/-15 行（§4.1）+ 既存テスト 1 件の書き換え + 新規テスト 4 件（§4.6）+ Javadoc（#5）+ 解説書 2 か所（#6） | `@Deprecated` 1 行 + Javadoc（#5）+ 解説書の当該節の書き直し（#6）+ `TestRuleEmulationIntegrationTest` の処遇（下記） | Javadoc（#5）+ 解説書 2 か所（#6）+ `TestRuleEmulationIntegrationTest` の処遇（下記） | 1-A と同じ + `@Deprecated` 1 行 |
| 公開 API への影響 | ルールの実行位置が変わる（下記の非互換）。`protected resolveInternalTestRules()` が 1 つ増える（§4.5 (2)） | 非推奨マークが付く | なし | 1-A と同じ + 非推奨マーク |
| 既存利用者の移行コスト | **ゼロではない。** 実行位置が `beforeEach` からテストメソッド直前へ移る／例外が `RuntimeException` に包まれなくなる／`Timeout` と `DbAccessTestExtension` の併用が壊れる（§4.4 (3)）／`@TestFactory` では適用されない（§4.4 (4)） | ルールごとに JUnit 5 の機能へ書き換え | なし（動きは変わらない） | 1-A と同じ。加えて非推奨警告への対応 |
| JUnit 4 依存の解消 | **しない** | **しない** | **しない** | **しない** |

**1-B / 1-C を採った場合、`TestRuleEmulationIntegrationTest` をどうするか。**
本セッションのタスク #1 で追加したこのテストは、現行実装に対して FAIL する（§1.1 実測1）。
1-B / 1-C では実装が変わらないので、このテストは削除するか、「ルールはテスト本体を包まない」ことを
固定する期待値へ反転させるかのどちらかになる。反転させる場合、**現在の壊れた挙動が仕様として固定される**
ことになるので、`@Deprecated` を付ける 1-B と組み合わせるのが自然。

**「JUnit 4 から離れる」ことにはどれもならない、という点が判断の要。**
NTF 本体の `TestEventDispatcher#testName` が JUnit 4 の `@Rule TestName` であり、
`DbAccessTestSupport` などが `@Before` / `@After` を使っている（§2.1 の表）。本モジュールの
`junit:junit:4.13.1` は compile スコープで、`resolveTestRules()` の存廃とは無関係に残る。
JUnit の流れに沿わせる本丸は §5.3 の別課題であって、判断1 ではない。

**推奨は 1-A だった。理由は 3 つ。**

1. **1-B / 1-C は不具合を残す。** `ExternalResource` の後処理がテスト本体より前に走る状態は、
   非推奨マークを付けても文書に書いても消えない。移行が済むまで誤った実行順で動き続ける。
   §1.2 のとおり、NTF 自身が公開している `SystemPropertyResource` も対象に含まれる
2. **コード変更は本モジュールに閉じる。** `src/main` は +68/-15 行で、実測で既存テストは 1 件しか落ちず、
   それは仕様変更そのもの（§4.3）。ただし**ドキュメント変更は閉じない。** タスク #6 は別リポジトリ
   `nablarch-document` の 2 か所の修正を必要とする（§6）。これはどの選択肢でも同じ
3. **1-A のあとで 1-B へ進みやすい。** `resolveTestRules()` の意味が
   「利用者がテスト本体を包みたいルール」に定まるので、後から非推奨にする判断がしやすくなる。
   （**逆向きが不可能なわけではない。** 1-B を選んでも `@Deprecated` を外して §4 の差分を入れる道は塞がらない。
   「1-B → 1-A は不可能」とは言えない）

**1-D を採らなかった理由。** 直したうえで同時に非推奨にすると、利用者に対して
「動くようにしたので使ってよい／使うのをやめてほしい」という 2 つのメッセージを同時に出すことになる。
JUnit 4 依存の解消は §5.3 の別課題であり、その方針が決まる前に非推奨マークだけ先に付けると、
移行先を示せないまま警告だけが出る。**非推奨化は §5.3 の結論とセットで判断する**のが筋。

**1-A を選んだことで受け入れた非互換。** §4.4 の (1)〜(4) がそのまま非互換の一覧になる。加えて、
`resolveTestRules()` に前処理だけのルールを渡していた場合、その実行位置が
`beforeEach`（NTF 前処理と同時）からテストメソッド直前（`@BeforeEach` の後）へ移る。
また、ルールが投げた例外が `RuntimeException` に包まれなくなる（§4.3）。
`resolveTestRules()` を実際に使っているプロジェクトの有無は未確認（§2.1）。

### 5.2 判断2 — 直し方（**判断不要。決定事項として §4 に記録**）

「テスト本体を包む」ためには `invocation` を引数で受け取る必要があり、それができる拡張ポイントは
`InvocationInterceptor` しかない（§2.2）。そして `InvocationInterceptor` は `@BeforeEach` より後に
呼ばれる（§2.1）ため、NTF の内部ルールをそちらへ移すと `TestEventDispatcherExtensionLifecycleMethodTest` が壊れる。
したがって「利用者のルールを `InvocationInterceptor` へ、内部ルールを `beforeEach` に残す」以外に形がない。

型ごとのアダプタを書く道（JUnit 公式の `junit-jupiter-migrationsupport` 方式）もあるが、
対応できるのが 3 種類だけで `Timeout` を含まず（§2.1）、そのうえ同モジュールは次のメジャーで削除される。
実装量も §4 の差分より大きい。選ぶ理由がない。

実装上の細部は §4.5 に記録した。そのうち §4.5 (2) は「公開 API を 1 本増やすか」という
判断を含むので、方針が変われば切り替えられるように代替も併記してある。

### 5.3 判断1 の外にある別課題 — NTF 本体から JUnit 依存を分離する

**結論から言うと、これは本件とは別に立てるべき課題である。** NTF 本体の変更を伴うため本リポジトリだけでは完結せず、
既存の JUnit 4 利用者への後方互換の検討も必要になる。本件の不具合修正と同じ土俵で決めるべきではない（§1.4）。

**何をする課題か。** NTF のロジックを JUnit のライフサイクル注釈から切り離し、JUnit 4 用・JUnit 5 用の
薄いアダプタを両側に置く。`TestEventDispatcher#testName` を素のフィールドにできれば、
TestRule 再現機構は NTF 内部からは不要になり、利用者向けに残すかどうかを純粋に方針として決められる。
JUnit の流れに沿うのはこれ。§5.1 で「非推奨化は §5.3 の結論とセット」と書いたのはこの意味。

**規模感。** §2.1 のとおり、`org.junit` を import しているのは 185 ファイル中 9 ファイル。
うち 5 ファイルがライフサイクル注釈・ルールを使っており、残る 4 ファイルは
`org.junit.Assert` の静的 import だけ（表明を別の手段に置き換えれば済む）。技術的な障壁は低い。

## 6. 解説書の通りになるか（タスク #6 の下ごしらえ）

**1-A（存続させて直す）を選んだ前提での話。コード例はそのまま動くようになるが、文章は 3 か所直す必要がある。**

出典はいずれも `nablarch/nablarch-document` のコミット `5391d5c`（`origin/main`、2026-08-05）の
`ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst`。

| 解説書の箇所 | 修正後の状態 |
|---|---|
| rst:377-391 `CustomTestSupport`（`@Rule Timeout` を持つ独自サポートクラス）の例 | そのまま。変更不要 |
| rst:395-414 `CustomTestSupportExtension`（`resolveTestRules()` のオーバーライド）の例 | そのまま。変更不要 |
| rst:416-418「これにより、JUnit 5のテスト上でもJUnit 4の `TestRule` を再現できるようになる」 | **要修正。** §4.4 の (1)(2)(4) を追記する。すなわち、包む範囲がテストメソッドのみで `@BeforeEach` / `@AfterEach` を含まないこと、`base` を呼ばないルールは使えないこと、`@TestFactory` / `DynamicTest` には適用されないこと |
| rst:420-421「必ず親クラスの `resolveTestRules()` が返すリストをベースにすること。そうしない場合、親クラスで登録している `TestRule` が再現されなくなる」 | **要修正。** 基底実装が空リストを返すようになるため、この理由づけは成り立たなくなる（§4.5 (1)） |
| （新規）rst:395-414 の直後 | **要追加。** §4.4 (3) の警告。**この節の唯一の例が `Timeout` である以上、これは必須。** `Timeout` はテスト本体を別スレッドで実行するため、`DbAccessTestExtension`（`@DbAccessTest`）と併用するとテスト本体から DB コネクションが取れない。またタイムアウト成立時にテスト本体と後処理が並行実行される |

**利用者が書くコードは変わらない。** 解説書の手順どおりに `resolveTestRules()` をオーバーライドすれば、
`Timeout` は実際にタイムアウトし、`ExternalResource` の後処理はテスト本体の後に実行される。
ただし §4.4 の制約の範囲内で、である。

これはタスク #6 で ja / en 両方の差分案として起こす。en 側の対応箇所の行番号は未確認。
