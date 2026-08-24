# 解説書の修正差分案 — 「JUnit 4のTestRuleを再現する」節

タスク #6 の成果物。design.md §6 が挙げた 4 か所（既存 3 か所の修正 + 新規 1 か所の追加）を、
そのまま別リポジトリへ適用できる粒度の差分案に起こしたものである。

## 1. 対象と前提

### 1.1 反映先

**本リポジトリ（`nablarch/nablarch-testing-junit5`）では反映できない。** 解説書は別リポジトリにある。

| 項目 | 値 |
|---|---|
| リポジトリ | `nablarch/nablarch-document` |
| 基準コミット | `5391d5cf721aab89ddd1e570bddec50ec9eefeaa`（`origin/main`、2026-08-05） |
| 対象ファイル（ja） | `ja/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst` |
| 対象ファイル（en） | `en/development_tools/testing_framework/guide/development_guide/06_TestFWGuide/JUnit5_Extension.rst` |
| 対象節 | 「JUnit 4のTestRuleを再現する」 / 「Reproducing the TestRule of JUnit 4」（ja / en とも rst:370-421） |
| 想定する PR の単位 | **ja / en の 2 ファイルを 1 本の PR にまとめる。** 4 か所の修正はすべて同一の実装変更（本リポジトリの `resolveTestRules()` の適用位置の変更）に由来し、片方だけ入れると解説書が実装と食い違う状態が残るため、分割しない |

**本差分案が前提とする実装は、本リポジトリの `1f9b46e`（タスク #5 完了時点）である。** 解説書の PR は、
この実装を含むリリースに合わせて出すこと。実装より先に解説書だけを反映すると、記述が実装と食い違う。

### 1.2 行番号について

**本文書に書いた行番号は、すべて `5391d5c` 時点のものである。** ja / en の双方について
`git show 5391d5c:<path>` で実物を開き、design.md §6 が挙げた行番号を突き合わせて検証した。

- **ja は design.md §6 の行番号と一致していた**（rst:377-391 / rst:395-414 / rst:416-418 / rst:420-421）。ずれはない
- **en の行番号は design.md では未確認だったが、突き合わせた結果 ja と完全に一致していた**（節の開始 rst:370、
  コード例 rst:377-391 と rst:395-414、説明 rst:416-418、注記 rst:420-421、次節の見出し rst:424-426）。
  ja と en は段落単位で 1 対 1 に対応しており、節全体の行数も同じである

### 1.3 rst の書式（実物を開いて確認した事項）

**この 2 ファイルは CRLF 改行である。** `cat -A` で全行が `^M$` で終わることを確認した。**差分を当てる際は
CRLF を保つこと。** また、**コード例の中の「空行」には半角スペース 2 個が入っている**（`  ^M$`）。
本差分案が触るのは既存のコード例の中身（`  ` 空行を含まない範囲）だけだが、当てる際に既存行の
トレーリングスペースを落とさないよう注意すること。

書式の慣行は同じファイルの他の箇所に合わせた。

- `.. code-block:: java` の本文は**半角スペース 2 個**のインデント（rst:377-391、rst:395-414）
- `.. warning::` は**ディレクティブ行の次に空行を 1 行置き**、本文を半角スペース 2 個でインデントする（rst:94-96）
- 箇条書きは `*` を使う（rst:348-351）
- 強調は `**...**` とし、日本語に隣接する場合は前後に半角スペースを入れる（rst:58）
- クラスへの参照は `:java:extdoc:` ロールを使う（rst:117-119 に `DbAccessTestExtension` / `DbAccessTest` の実例がある）

### 1.4 適用順

**修正4 → 修正3 → 修正2 → 修正1 の順（ファイルの後方から前方へ）に適用すること。** そうすれば、
先の修正で行数が変わっても、後の修正の行番号がずれない。

### 1.5 適用の検証（本差分案に対して実施したこと）

**本文書の「変更前」「変更後」のコードブロックを機械的に読み取り、`5391d5c` の実物へ当てて検証した。**
手順は次のとおり。本リポジトリには何も残していない（作業は一時ディレクトリで行った）。

1. `git show 5391d5c:<path>` で ja / en の原文を取り出す（どちらも 455 行、CRLF）
2. 本文書の各「変更前」ブロックが、原文の該当行と**バイト単位で一致する**ことを表明する
   （ja / en とも rst:404-413 と rst:420-421 で一致。rst:415 / rst:419 が空行であることも表明）
3. 「変更後」ブロックと挿入内容を、§1.4 の位置に当てる
4. 結果を `docutils` で構文解析する

**結果。** ja / en とも **455 行 → 480 行**（修正1 で −2、修正2 で +7、修正3 で +18、修正4 で +3、
差引 +26 だが修正4 が 2 行を置き換えるため実質 +25）。
`docutils` の system message は **原文・適用後ともに 0 件**（`:java:extdoc:` / `:ref:` / `contents` は
ダミーのロール・ディレクティブとして登録したうえで解析した）。改行コードも CRLF のままである。

**「変更前」が実物と一致することを機械的に表明しているので、行番号がずれていれば適用時に検出できる。**

## 2. ja 側の差分案

### 修正1 — rst:404-413（コード例から `super.resolveTestRules()` を外す）

**理由。** 基底実装が空のリストを返すようになったため（`TestEventDispatcherExtension.java:531-533` の
`return Collections.emptyList();`）、`super.resolveTestRules()` をベースにする意味がなくなった。
コメント 2 を削り、以降の番号を繰り上げる。

**変更前（rst:404-413）**

```
      // 1. resolveTestRules メソッドをオーバーライドする
      @Override
      protected List<TestRule> resolveTestRules() {
          // 2. 親クラスの resolveTestRules() の結果をベースにしてリストを生成する
          List<TestRule> rules = new ArrayList<>(super.resolveTestRules());
          // 3. 独自拡張クラスで定義しているTestRuleをリストに追加する
          rules.add(((CustomTestSupport) support).timeout);
          // 4. 生成したリストを返却する
          return rules;
      }
```

**変更後（8 行。2 行減る）**

```
      // 1. resolveTestRules メソッドをオーバーライドする
      @Override
      protected List<TestRule> resolveTestRules() {
          List<TestRule> rules = new ArrayList<>();
          // 2. 独自拡張クラスで定義しているTestRuleをリストに追加する
          rules.add(((CustomTestSupport) support).timeout);
          // 3. 生成したリストを返却する
          return rules;
      }
```

### 修正2 — rst:415 の直後に警告を新規追加（`Timeout` と `DbAccessTestExtension` の併用）

**理由。** design.md §6 のとおり、**この節が挙げている唯一の例が `Timeout` である**以上、必須である。
挿入位置は rst:414 のコード例の終わり（`  }`）の後、rst:415 の空行の後で、rst:416 の本文の前。
**挿入後も rst:416 の本文との間に空行が 1 行入るようにすること。**

**挿入する内容（7 行。末尾の空行を含む）**

```
.. warning::

  ``Timeout`` はテスト本体を別スレッドで実行するため、 :java:extdoc:`DbAccessTestExtension <nablarch.test.junit5.extension.db.DbAccessTestExtension>` （ :java:extdoc:`DbAccessTest <nablarch.test.junit5.extension.db.DbAccessTest>` ）と併用できない。
  自動テストフレームワークが確立したデータベース接続とトランザクションは、事前処理を実行したスレッドに紐付けて保持されるため、別スレッドで実行されるテスト本体からは取得できなくなる。
  取得に失敗したときの例外を捕捉していると、データベースにアクセスできていないままテストが成功してしまうので注意すること。
  また、タイムアウトが発生した場合、テスト本体の実行は打ち切られないまま事後処理が実行される。

```

### 修正3 — rst:418 の直後に制約を追記

**理由。** 現行の rst:416-418 は「これにより、JUnit 5のテスト上でもJUnit 4の `TestRule` を再現できるようになる」で
終わっており、再現に付く制約が一切書かれていない。design.md §4.4 の (1)(2)(3)(6) を追記する。

**rst:416-418 の 3 行は変更しない。** その直後（rst:419 の空行の後、修正4 で書き換える rst:420 の前）に、
以下を挿入する。**挿入後も、続く本文との間に空行が 1 行入るようにすること。**

**挿入する内容（18 行。末尾の空行を含む）**

```
ただし、JUnit 5はJUnit 4の ``TestRule`` を正式にはサポートしていないため、この再現には以下の制約がある。

* ルールが包むのはテストメソッドの実行だけであり、 ``@BeforeEach`` / ``@AfterEach`` や自動テストフレームワークの事前処理・事後処理は含まれない。
  そのため、JUnit 4ではルールの事前処理が ``@Before`` より前・事後処理が ``@After`` より後に実行されていたのに対し、本拡張機能ではルールの事前処理が ``@BeforeEach`` の後、事後処理が ``@AfterEach`` の前に実行される。
  例えば、 ``ExternalResource`` や ``TemporaryFolder`` が作成したリソースを ``@AfterEach`` から参照するコードや、 ``@AfterEach`` の失敗を ``TestWatcher`` で観測するコードは、 **例外にならないまま期待どおりに動かなくなる** 。
  なお、 ``@BeforeEach`` から ``TemporaryFolder`` の ``getRoot()`` を呼び出した場合は、一時フォルダがまだ作成されていないため ``IllegalStateException`` が発生する。
* ``@BeforeEach`` が失敗した場合、ルールは事前処理も事後処理も一切実行されない。
  ルールを組み立てられるのはテストメソッドを実行する時点であり、JUnit 5は事前処理が失敗するとテストメソッドの実行に到達しないためである。
  JUnit 4では ``@Before`` が失敗してもルールの事後処理は実行されていた。
  リソースの解放をルールに任せている場合、 ``@BeforeEach`` が失敗したときにだけ解放漏れが起きることになるので注意すること。
* ``base`` を呼び出さないルール（テストをスキップするもの）と、 ``base`` を2回以上呼び出すルール（テストをリトライ・繰り返すもの）は使用できない。
  どちらもテストが例外で失敗する（後者は、テスト本体が1回実行されたうえで失敗する）。
  ここでいう ``base`` とは、 ``TestRule`` の ``apply`` メソッドの第1引数、すなわちルールが包む対象の ``Statement`` である。
* ``@TestFactory`` が生成した ``DynamicTest`` には、ルールが適用されない。
  この場合、 **例外にもならないままテストが実行される** ので注意すること。

```

### 修正4 — rst:420-421 を移行手順に書き換える

**理由。** 基底実装が空のリストを返すようになったため、「必ず親クラスの `resolveTestRules()` が返すリストを
ベースにすること」という指示と、その理由づけ（「そうしない場合、親クラスで登録している `TestRule` が
再現されなくなる」）がどちらも事実でなくなる。あわせて、ルールの適用位置が移ることに対する移行手順を書く。

**`resolveInternalTestRules()` は移行先として案内しない**（design.md §4.5 (2)。NTF 内部専用であり、
そこに置いたルールは「テスト本体を包まない」＋「例外が `RuntimeException` に包まれる」という別の意味論を持つ）。
代わりに「別の経路で適用される」とだけ書き、メソッド名は出さない。

**変更前（rst:420-421）**

```
なお、 ``resolveTestRules()`` をオーバーライドするときは、必ず親クラスの ``resolveTestRules()`` が返すリストをベースにすること。
そうしない場合、親クラスで登録している ``TestRule`` が再現されなくなる。
```

**変更後（5 行。3 行増える）**

```
なお、 ``resolveTestRules()`` の基底実装は空のリストを返すため、親クラスの ``resolveTestRules()`` が返すリストをベースにする必要はない。
自動テストフレームワークが内部で使用する ``TestRule`` は、このメソッドとは別の経路で適用される。

また、 ``resolveTestRules()`` で返却したルールは、 ``@BeforeEach`` の後、テストメソッドの実行の直前から適用される。
テストの実行前に情報を控えるだけのルール（ ``TestName`` など）を返却していて、ルールが設定した値を ``@BeforeEach`` の中で参照している場合は、参照する箇所をテストメソッドの中に移すこと。
```

## 3. en 側の差分案

**ja の修正1〜修正4 と 1 対 1 に対応する。** 行番号・適用順・書式上の注意（CRLF、コード例のインデント、
`.. warning::` の書き方）は ja と同じである。文体は同じファイルの周辺の英文に合わせた
（`the automated testing framework` / `pre-processing` / `post-processing` / `your own extension class` /
`Note that ...` / `In this case ...`）。

### 修正1en — rst:404-413（ja 修正1 に対応）

**変更前（rst:404-413）**

```
      // 1. Override the resolveTestRules method
      @Override
      protected List<TestRule> resolveTestRules() {
          // 2. Generate a list based on the result of resolveTestRules() of the parent class
          List<TestRule> rules = new ArrayList<>(super.resolveTestRules());
          // 3. Add the TestRule defined in your own extension class to the list
          rules.add(((CustomTestSupport) support).timeout);
          // 4. Return the generated list
          return rules;
      }
```

**変更後（8 行。2 行減る）**

```
      // 1. Override the resolveTestRules method
      @Override
      protected List<TestRule> resolveTestRules() {
          List<TestRule> rules = new ArrayList<>();
          // 2. Add the TestRule defined in your own extension class to the list
          rules.add(((CustomTestSupport) support).timeout);
          // 3. Return the generated list
          return rules;
      }
```

### 修正2en — rst:415 の直後に警告を新規追加（ja 修正2 に対応）

**挿入する内容（7 行。末尾の空行を含む）**

```
.. warning::

  ``Timeout`` cannot be used together with :java:extdoc:`DbAccessTestExtension <nablarch.test.junit5.extension.db.DbAccessTestExtension>` (:java:extdoc:`DbAccessTest <nablarch.test.junit5.extension.db.DbAccessTest>`), because it executes the body of the test method in a separate thread.
  The database connection and the transaction established by the automated testing framework are held bound to the thread that executed the pre-processing, so they cannot be obtained from the body of the test method, which runs in another thread.
  Note that if the exception raised when obtaining them is caught, the test succeeds even though the database has not been accessed.
  In addition, when a timeout occurs, the post-processing is executed while the body of the test method is still running.

```

### 修正3en — rst:418 の直後に制約を追記（ja 修正3 に対応）

**rst:416-418 の 3 行は変更しない。** その直後に以下を挿入する（18 行。末尾の空行を含む）。

```
However, JUnit 5 does not officially support the ``TestRule`` of JUnit 4, so the following restrictions apply to this reproduction.

* The rule wraps only the execution of the test method; ``@BeforeEach`` / ``@AfterEach`` and the pre-processing and post-processing of the automated testing framework are not included.
  Therefore, whereas in JUnit 4 the pre-processing of the rule was executed before ``@Before`` and its post-processing after ``@After``, in this extension the pre-processing of the rule is executed after ``@BeforeEach`` and its post-processing before ``@AfterEach``.
  For example, code that refers from ``@AfterEach`` to a resource created by ``ExternalResource`` or ``TemporaryFolder``, and code that observes a failure of ``@AfterEach`` with ``TestWatcher``, **stop working as expected without raising any exception**.
  Note that calling ``getRoot()`` of ``TemporaryFolder`` from ``@BeforeEach`` raises ``IllegalStateException``, because the temporary folder has not been created yet.
* If ``@BeforeEach`` fails, neither the pre-processing nor the post-processing of the rule is executed at all.
  This is because the rule can be assembled only when the test method is executed, and JUnit 5 does not reach the execution of the test method if the pre-processing fails.
  In JUnit 4, the post-processing of the rule was executed even if ``@Before`` failed.
  Note that if you leave the release of resources to the rule, the resources are leaked only when ``@BeforeEach`` fails.
* A rule that does not call ``base`` (one that skips the test) and a rule that calls ``base`` twice or more (one that retries or repeats the test) cannot be used.
  In both cases the test fails with an exception (in the latter case, after the body of the test method has been executed once).
  Here, ``base`` means the first argument of the ``apply`` method of ``TestRule``, that is, the ``Statement`` that the rule wraps.
* The rule is not applied to the ``DynamicTest`` generated by ``@TestFactory``.
  Note that in this case **the test is executed without raising any exception**.

```

### 修正4en — rst:420-421 を書き換える（ja 修正4 に対応）

**変更前（rst:420-421）**

```
Note that overriding ``resolveTestRules()`` should always be based on the list returned by the parent class ``resolveTestRules()``.
If not, the ``TestRule`` registered in the parent class will not be reproduced.
```

**変更後（5 行。3 行増える）**

```
Note that the base implementation of ``resolveTestRules()`` returns an empty list, so it does not need to be based on the list returned by the parent class ``resolveTestRules()``.
The ``TestRule`` used internally by the automated testing framework is applied through a route other than this method.

Also note that the rules returned by ``resolveTestRules()`` are applied after ``@BeforeEach``, immediately before the test method is executed.
If you return a rule that only records information before the test is executed (such as ``TestName``), and you refer to the value set by the rule in ``@BeforeEach``, move that reference into the test method.
```

## 4. 各記述を裏づける恒久テスト

**「修正後の記述どおりに実装すれば動作する」ことの裏づけは、すべて本リポジトリ（`1f9b46e`）の
`src/test` にある恒久テストである。** design.md は根拠にしていない。

| 差分案の記述 | 裏づける恒久テスト |
|---|---|
| **修正1** — `super.resolveTestRules()` をベースにしなくてもルールが適用される | `src/test/java/nablarch/test/junit5/extension/event/ConfigurableTestRuleExtension.java:55-58` が `super` を呼ばずに設定されたリストをそのまま返す実装であり、`StandardTestRuleIntegrationTest`（9 件）・`TestRuleLifecycleIntegrationTest`（2 件）・`TestRuleInvocationContractIntegrationTest`（4 件）・`TestFactoryRuleIntegrationTest`（1 件）がすべてこの Extension 経由でルールの適用を表明している |
| **修正1** — 解説書と同じ形（サポートクラスの `@Rule Timeout` を `resolveTestRules()` で返す）でテストがタイムアウトする | `TimeoutRuleIntegrationTest.java:79-86`（`解説書の例と同じ実装でTimeoutを追加するとテストがタイムアウトすることをテスト`）。フィクスチャは `:103-137` にあり、解説書 rst:377-391 / rst:395-414 と同じ形である |
| **修正2** — `Timeout` を併用するとテスト本体から DB コネクションもトランザクションも取得できない／それでもテストは成功する | `src/test/java/nablarch/test/junit5/extension/db/TimeoutDbAccessIntegrationTest.java:92-109`（`Timeoutを併用すると…`）。`:79-90` に `DbAccessTestExtension` 単体との対照がある。失敗時の例外は `IllegalArgumentException: specified database connection name is not register in thread local. connection name = [transaction]` |
| **修正2** — テスト本体が `beforeEach` とは別スレッド（`Time-limited test`）で実行される | `TimeoutRuleIntegrationTest.java:88-101`、`TimeoutDbAccessIntegrationTest.java:97-98` |
| **修正2** — 「タイムアウト発生時、テスト本体の実行は打ち切られないまま事後処理が実行される」 | **裏づけとなる恒久テストがない（未実測）。** §5 を参照 |
| **修正3 第1項** — ルールの事前処理が `@BeforeEach` の後、事後処理が `@AfterEach` の前 | `TestRuleLifecycleIntegrationTest.java:28-37`。実行ログ `["@BeforeEach", "resource-before", "test", "resource-after", "@AfterEach"]` を表明している |
| **修正3 第1項** — `@AfterEach` の失敗を `TestWatcher` / `Stopwatch` で観測できない | `StandardTestRuleIntegrationTest.java:173-183`。実行ログ `["test", "succeeded", "finished", "@AfterEach"]` |
| **修正3 第1項** — `@BeforeEach` から `TemporaryFolder#getRoot()` を呼ぶと `IllegalStateException` | `StandardTestRuleIntegrationTest.java:93-111`。例外型と `the temporary folder has not yet been created` を表明し、実行ログ `["@BeforeEach:root-not-created", "rule-before", "test:root-exists", "rule-after"]` で「適用位置がずれている」と「適用されていない」を区別している |
| **修正3 第2項** — `@BeforeEach` が失敗するとルールの事前処理も事後処理も走らない | `TestRuleLifecycleIntegrationTest.java:39-47`。実行ログ `["@BeforeEach", "@AfterEach"]`（`resource-before` も `resource-after` も現れない） |
| **修正3 第3項** — `base` を呼ばないルールは失敗し、テスト本体も実行されない | `TestRuleInvocationContractIntegrationTest.java:46-56`。`JUnitException`（`never called invocation`）と実行ログが空であることを表明 |
| **修正3 第3項** — `base` を 2 回呼ぶルールは、テスト本体が 1 回実行されたうえで失敗する | `TestRuleInvocationContractIntegrationTest.java:58-68`。`JUnitException`（`multiple times`）と実行ログ `["test"]` |
| **修正3 第4項** — `@TestFactory` の動的テストにルールが適用されず、例外にもならない | `TestFactoryRuleIntegrationTest.java:36-45`。テストは成功し、実行ログは `["factory-body", "dynamic-1"]` でルールの前後処理が現れない |
| **修正4** — 基底実装が空のリストを返す | `src/main/java/nablarch/test/junit5/extension/event/TestEventDispatcherExtension.java:531-533`（`return Collections.emptyList();`）。「NTF の内部ルールは別経路」は `:479-481` の `resolveInternalTestRules()` と `:322-330` の `applyInternalTestRules` |
| **修正4** — ルールの適用位置が `@BeforeEach` の後、テストメソッドの直前 | `TestRuleLifecycleIntegrationTest.java:28-37`（上と同じ実行ログ）。`@Test` 以外の経路（`@ParameterizedTest` / `@RepeatedTest`）でも同じであることは `TestRuleEmulationIntegrationTest.java:117-131`（3 件） |

## 5. レビュー依頼時に伝えること（差分案の本文には含めない）

**修正2 / 修正2en の最後の 1 文だけは未実測である。**

- ja: 「また、タイムアウトが発生した場合、テスト本体の実行は打ち切られないまま事後処理が実行される。」
- en: 「In addition, when a timeout occurs, the post-processing is executed while the body of the test method is still running.」

**この記述に対応する恒久テストは存在しない。** design.md §4.4 (5) が根拠にしているのは
`junit-4.13.1-sources` の `org/junit/internal/runners/statements/FailOnTimeout.java:133-138` の
`finally` で `thread.join(1)`（1 ミリ秒）しか待たずに抜ける実装であり、**そこから導かれる帰結**である。
`afterEach` がテスト本体の終了を待たないこと自体は design.md §4.2 後半で実測しているが、
その再現物は残っておらず、`endTransactions()` との競合そのものは観測していない（design.md §2.3）。

**この 1 点をどう扱うかは、レビュー時に判断してほしい。** 選択肢は次の 3 つである。

1. このまま入れる（実装を読めば導ける帰結であり、利用者にとっては注意喚起として有用）
2. 本リポジトリ側で先に恒久テストを追加し、実測してから入れる
3. この 1 文だけ落とし、DB コネクションが取れないこと（実測済み・恒久テストあり）だけを書く

**なお「未実測である」という検証状況そのものは、解説書の文面には含めていない。** 利用者向け文書に
検証状況を書くのは適切でないためである。上の 3 つのうちどれを採っても、解説書の文面に
「未確認」といった記述は入れないこと。

## 6. 本差分案が意図的に含めなかったもの

design.md §6 の末尾にあるとおり、以下は解説書には入れていない。**判断したことを残すために記録する。**

| 入れなかったもの | 理由 |
|---|---|
| 例外の扱いの変更（`resolveTestRules()` で返したルールが投げた例外が `RuntimeException` に包まれなくなる。design.md §4.5 (4)） | 当該節（rst:370-421）にもともと例外の扱いの記述がなく、書かれていない前提が変わっただけであるため。Javadoc（タスク #5）には明記済み |
| `resolveInternalTestRules()` の存在 | NTF 内部専用であり（design.md §4.5 (2)）、利用者に開くと非対称な例外ポリシーを公開 API として抱えることになるため。修正4 では「別の経路で適用される」とだけ書き、メソッド名は出していない |
| `interceptTestMethod` / `interceptTestTemplateMethod` を `final` にしたこと（design.md §4.5 (5)） | 解説書の手順どおりに `resolveTestRules()` をオーバーライドするだけの利用者には影響がなく、影響を受ける利用者はコンパイルエラーで気づくため |
| `@Nested` を持つテストクラスで正しく動かないこと（design.md §4.4 (7)） | `TestRule` 再現機構に固有の問題ではなく、1-A 以前からある別課題であるため。Javadoc には明記済み |
| `@ParameterizedTest` で全 invocation の `Description` が同一内容になること（design.md §4.4 (4) の残り） | 1-A で直ったことでも 1-A で受け入れた制約でもなく、JUnit 5 側の性質であるため。Javadoc には明記済み |
| 「JUnit 5 に同等の機能がある場合はそちらを使うこと」という案内 | design.md §6 が挙げた 4 か所の外側であるため、本差分案には含めていない。**入れるべきかはレビュー時に判断してほしい**（Javadoc には記載済み） |
